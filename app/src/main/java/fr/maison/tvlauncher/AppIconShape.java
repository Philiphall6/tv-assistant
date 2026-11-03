package fr.maison.tvlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Unit-square masks shared by application artwork and the settings previews.
 * Inspired by Material's cookie/arch silhouettes; no system theme dependency. */
final class AppIconShape {
    static Path create(int shape) {
        Path p = new Path();
        if (shape == AppIconRenderer.SQUARE) p.addRect(0, 0, 1, 1, Path.Direction.CW);
        else if (shape == AppIconRenderer.ROUNDED_SQUARE)
            p.addRoundRect(new RectF(0, 0, 1, 1), .22f, .22f, Path.Direction.CW);
        else if (shape == AppIconRenderer.COOKIE_FOUR) {
            p.moveTo(.5f, .08f);
            p.cubicTo(.66f,.08f,.7f,0,.82f,0);p.cubicTo(.96f,0,1,.09f,1,.18f);
            p.cubicTo(1,.30f,.92f,.36f,.92f,.5f);p.cubicTo(.92f,.66f,1,.7f,1,.82f);
            p.cubicTo(1,.96f,.91f,1,.82f,1);p.cubicTo(.70f,1,.64f,.92f,.5f,.92f);
            p.cubicTo(.34f,.92f,.30f,1,.18f,1);p.cubicTo(.04f,1,0,.91f,0,.82f);
            p.cubicTo(0,.70f,.08f,.64f,.08f,.5f);p.cubicTo(.08f,.34f,0,.30f,0,.18f);
            p.cubicTo(0,.04f,.09f,0,.18f,0);p.cubicTo(.30f,0,.36f,.08f,.5f,.08f);
            p.close();
        } else if (shape == AppIconRenderer.COOKIE_SEVEN) {
            // Smooth shallow scallops, not a sharp star. Cached, never rebuilt per frame.
            for (int i = 0; i < 224; i++) {
                double a = i * Math.PI * 2 / 224;
                double r = .46 + .04 * Math.cos(7 * a);
                float x = .5f + (float)(r * Math.sin(a)), y = .5f - (float)(r * Math.cos(a));
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            RectF bounds = new RectF();p.computeBounds(bounds, true);
            Matrix normal = new Matrix();normal.setRectToRect(bounds, new RectF(0,0,1,1), Matrix.ScaleToFit.FILL);
            p.transform(normal);
        } else if (shape == AppIconRenderer.ARCH) {
            p.moveTo(0,.5f);p.cubicTo(0,.223858f,.223858f,0,.5f,0);
            p.cubicTo(.776142f,0,1,.223858f,1,.5f);p.lineTo(1,.88f);
            p.quadTo(1,1,.88f,1);p.lineTo(.12f,1);p.quadTo(0,1,0,.88f);p.close();
        } else p.addCircle(.5f, .5f, .5f, Path.Direction.CW);
        return p;
    }

    static Drawable preview(int shape, int color) {
        return new Drawable() {
            final Path path = create(shape);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            { paint.setColor(color); }
            @Override public void draw(Canvas canvas) {
                int save = canvas.save();canvas.clipRect(getBounds());canvas.translate(getBounds().left, getBounds().top);
                canvas.scale(getBounds().width(), getBounds().height());canvas.drawPath(path, paint);
                canvas.restoreToCount(save);
            }
            @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
            @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
        };
    }
}
