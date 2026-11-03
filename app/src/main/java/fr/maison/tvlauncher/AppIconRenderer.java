package fr.maison.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** Full-bleed application artwork, independent of the system's adaptive icon mask. */
final class AppIconRenderer {
    static final int ROUND = 0;
    static final int ROUNDED_SQUARE = 1;
    static final int SQUARE = 2;
    // Existing IDs remain stable for saved profiles and local settings.
    static final int COOKIE_FOUR = 3;
    static final int COOKIE_SEVEN = 4;
    static final int ARCH = 5;
    private static final int TEXTURE_SIZE = 256;
    private final Drawable source;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF bounds = new RectF();
    private BitmapShader texture;
    private final Matrix maskMatrix = new Matrix();
    private final Path mask = new Path();
    private Path unitMask;
    private int maskShape = -1;
    private final RectF maskBounds = new RectF();

    AppIconRenderer(Drawable source) {
        this.source = source;
    }

    void draw(Canvas canvas, float cx, float cy, float radius, int shape) {
        if (radius <= 0) return;
        if (texture == null) prepareTexture();
        float size = radius * 2;
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        matrix.setScale(size / TEXTURE_SIZE, size / TEXTURE_SIZE);
        matrix.postTranslate(bounds.left, bounds.top);
        texture.setLocalMatrix(matrix);
        if (unitMask == null || maskShape != shape) {
            unitMask = AppIconShape.create(shape);maskShape = shape;maskBounds.setEmpty();
        }
        if (!maskBounds.equals(bounds)) {
            maskMatrix.setScale(size, size);maskMatrix.postTranslate(bounds.left, bounds.top);
            unitMask.transform(maskMatrix, mask);maskBounds.set(bounds);
        }
        // A filled, anti-aliased mask: no decorative stroke or inset around the artwork.
        int save = canvas.save();canvas.clipRect(bounds);
        canvas.drawPath(mask, paint);
        canvas.restoreToCount(save);
    }

    private void prepareTexture() {
        Bitmap bitmap = Bitmap.createBitmap(TEXTURE_SIZE, TEXTURE_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (source != null) {
            Rect previousBounds = new Rect(source.getBounds());
            try {
                if (Build.VERSION.SDK_INT >= 26 && source instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) source;
                    adaptive.setBounds(0, 0, TEXTURE_SIZE, TEXTURE_SIZE);
                    // Keep Android's layer layout, but apply our own shape, not its circular mask.
                    if (adaptive.getBackground() != null) adaptive.getBackground().draw(canvas);
                    if (adaptive.getForeground() != null) adaptive.getForeground().draw(canvas);
                } else {
                    int width = Math.max(1, source.getIntrinsicWidth());
                    int height = Math.max(1, source.getIntrinsicHeight());
                    float scale = (float) TEXTURE_SIZE / Math.min(width, height);
                    int drawWidth = Math.round(width * scale), drawHeight = Math.round(height * scale);
                    int left = (TEXTURE_SIZE - drawWidth) / 2, top = (TEXTURE_SIZE - drawHeight) / 2;
                    source.setBounds(left, top, left + drawWidth, top + drawHeight);
                    source.draw(canvas);
                }
            } finally {
                source.setBounds(previousBounds);
            }
        }
        if (!(Build.VERSION.SDK_INT >= 26 && source instanceof AdaptiveIconDrawable))
            LegacyIconNormalizer.flattenMatte(bitmap);
        // Extend the artwork's own edge color into transparent corners. A neutral
        // circle behind a dark legacy icon would otherwise look like another rim.
        canvas.drawColor(backingColor(bitmap), PorterDuff.Mode.DST_OVER);
        texture = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(texture);
    }

    private static int backingColor(Bitmap bitmap) {
        int size = bitmap.getWidth();
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        int[] counts = new int[4096], reds = new int[4096], greens = new int[4096], blues = new int[4096];
        int opaque = 0, edge = size / 6, best = 0;
        for (int y = 0; y < size; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int color = pixels[y * size + x];
                if ((color >>> 24) < 240) continue;
                opaque++;
                if (x >= edge && x < size - edge && y >= edge && y < size - edge) continue;
                int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
                int bucket = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4);
                counts[bucket]++;reds[bucket] += red;greens[bucket] += green;blues[bucket] += blue;
                if (counts[bucket] > counts[best]) best = bucket;
            }
        }
        // A sparse transparent glyph needs a contrasting tile, not its own ink color.
        if (opaque < size * size / 4 * .4f || counts[best] < 16) return 0xff202832;
        return 0xff000000 | ((reds[best] / counts[best]) << 16)
                | ((greens[best] / counts[best]) << 8) | (blues[best] / counts[best]);
    }
}
