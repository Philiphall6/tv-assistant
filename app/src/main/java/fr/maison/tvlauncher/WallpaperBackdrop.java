package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.graphics.*;

/** Solid backgrounds are genuinely uniform; photo-only gradients never tint them. */
final class WallpaperBackdrop {
    static final int SOLID = 5;
    static final int BRAND = 6;
    static final String DEFAULT_COLOR = "#202124";
    static void initialize(SharedPreferences prefs) {
        if (prefs.getBoolean("home_redesign_v2", false)) return;
        SharedPreferences.Editor edit = prefs.edit().putBoolean("home_redesign_v2", true);
        if (!prefs.contains("wallpaper_mode")) edit.putInt("wallpaper_mode", BRAND);
        if (!prefs.contains("background_dim")) edit.putInt("background_dim", 8);
        edit.apply();
    }
    /** Built-in vector branding. No downloaded image or private wallpaper is needed. */
    static void drawBrand(Canvas canvas, float w, float h, android.graphics.drawable.Drawable icon) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, w, h,
            new int[]{0xff0b1322, 0xff122838, 0xff164051}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .79f, h * .30f, w * .35f,
            new int[]{0x333bbded, 0x0018bcf2}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        float size = h * .15f, cx = w * .78f, cy = h * .29f;
        icon.setBounds(Math.round(cx-size/2), Math.round(cy-size/2), Math.round(cx+size/2), Math.round(cy+size/2));
        icon.draw(canvas);
        paint.setColor(0xffe0f4ff); paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(h * .043f);
        canvas.drawText("TV Assistant", cx, cy + size * .92f, paint);
    }
    static int color(String raw){
        return raw!=null&&raw.matches("#[0-9a-fA-F]{6}")?Color.parseColor(raw):Color.parseColor(DEFAULT_COLOR);
    }
    static boolean drawSolid(Canvas c,float w,float h,int mode,SharedPreferences prefs,Paint p){
        if(mode!=SOLID)return false;
        p.setShader(null);p.setColor(color(prefs.getString("wallpaper_color",DEFAULT_COLOR)));
        c.drawRect(0,0,w,h,p);return true;
    }
    static void overlays(Canvas c,float w,float h,int mode,SharedPreferences prefs,Paint p){
        p.setShader(null);p.setColor(Color.WHITE);
        if(mode!=SOLID && mode!=BRAND){
            int a=prefs.getInt("wallpaper_left_overlay",100)*255/100;
            float width=w*prefs.getInt("wallpaper_overlay_width",82)/100f;
            p.setShader(new LinearGradient(0,0,width,0,new int[]{Color.argb(a,8,16,22),Color.argb(a*95/100,8,16,22),Color.argb(a*60/100,8,16,22),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
            int bottom=prefs.getInt("wallpaper_bottom_overlay",100)*255/100;
            p.setShader(new LinearGradient(0,h*.54f,0,h,new int[]{Color.TRANSPARENT,Color.argb(bottom,8,16,22)},null,Shader.TileMode.CLAMP));
            c.drawRect(0,h*.46f,w,h,p);p.setShader(null);
        }
        int dim=prefs.getInt("background_dim",0);
        if(dim>0){p.setColor(Color.argb(Math.min(150,dim*255/100),0,0,0));c.drawRect(0,0,w,h,p);}
        p.setShader(null);
    }
}
