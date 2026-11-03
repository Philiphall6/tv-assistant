package fr.maison.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Official 2023 HA logomark. Tint only the house, never the antenna or wordmark. */
final class BrandLogo {
    static final int BLUE = 0xff18bcf2;
    private final Drawable mark, accent;
    private int color = BLUE;

    BrandLogo(Context context) {
        mark = context.getDrawable(R.drawable.app_icon).mutate();
        accent = context.getDrawable(R.drawable.ha_logo_accent).mutate();
        mark.setBounds(0, 0, 240, 240);
        accent.setBounds(0, 0, 240, 240);
    }

    void draw(Canvas canvas, float x, float y, float size, int tint) {
        if (color != tint) { color = tint; accent.setTint(tint); }
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(size / 240f, size / 240f);
        mark.draw(canvas);
        if (tint != BLUE) accent.draw(canvas);
        canvas.restoreToCount(save);
    }
}
