package fr.maison.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/** Pixel checks on the Android runtime: run with tests/run-icon-tests.sh [adb serial]. */
public final class AppIconRendererTest {
    private static final int ART = 0xffb64091;
    private static final int LOGO = 0xff31a678;
    static int checks;

    public static void main(String[] args) {
        nestedMatteChecks();
        customShapeChecks();
        AppIconRenderer icon = new AppIconRenderer(new ColorDrawable(ART));
        for (int shape = 0; shape <= 2; shape++) {
            Bitmap output = render(icon, shape);
            pixel(output, 64, 64, ART, "artwork center");
            pixel(output, 64, 17, ART, "no top inset or rim");
            pixel(output, 17, 64, ART, "no left inset or rim");
            pixel(output, 64, 110, ART, "no bottom inset or rim");
            pixel(output, 110, 64, ART, "no right inset or rim");
            pixel(output, 64, 12, 0, "no decorative outline");
            pixel(output, 18, 18, shape == 2 ? ART : 0, "shape corner");
            pixel(output, 25, 25, shape == 0 ? 0 : ART, "rounded square differs from circle");
        }
        for (int shape = 0; shape <= AppIconRenderer.ARCH; shape++) {
            Bitmap output = render(icon, shape);
            pixel(output, 64, 64, ART, "shape keeps artwork center");
            Bitmap preview = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Drawable drawable = AppIconShape.preview(shape, ART);
            drawable.setBounds(16,16,112,112);drawable.draw(new Canvas(preview));
            int filled = 0;
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int color = output.getPixel(x,y), alpha = color >>> 24;
                if(alpha == 255) {filled++;pixel(output,x,y,ART,"no inset, contour or color change");}
                require(Math.abs(alpha-(preview.getPixel(x,y)>>>24)) <= 2,"settings preview matches application mask");
                if(x<16||x>=112||y<16||y>=112) require(alpha==0,"mask stays inside bounds: shape="+shape+" x="+x+" y="+y+" alpha="+alpha);
            }
            require(filled>5000,"all shapes remain full icons");
            for(int other=0;other<shape;other++) require(!output.sameAs(render(icon,other)),"each shape is distinct");
            Bitmap small=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(small),32,38,16,shape);
            pixel(small,32,38,ART,"every mask resizes and translates");
            pixel(small,64,64,0,"resizing never keeps old mask bounds");
        }
        Bitmap cookie=render(icon,AppIconRenderer.COOKIE_FOUR);
        pixel(cookie,64,18,0,"cookie has a soft top notch");
        pixel(cookie,64,26,ART,"cookie notch is shallow");
        Bitmap arch=render(icon,AppIconRenderer.ARCH);
        pixel(arch,23,23,0,"arch rounded top");
        pixel(arch,23,104,ART,"arch filled lower corner");

        Drawable adaptive = new AdaptiveIconDrawable(new ColorDrawable(ART), new Logo(false));
        Rect previous = new Rect(9, 11, 85, 87);
        adaptive.setBounds(previous);
        Bitmap square = render(new AppIconRenderer(adaptive), AppIconRenderer.SQUARE);
        pixel(square, 18, 18, ART, "adaptive background fills square corners");
        pixel(square, 64, 64, LOGO, "adaptive foreground retained");
        require(previous.equals(adaptive.getBounds()), "drawable bounds restored");

        Bitmap transparent = render(new AppIconRenderer(new Logo(false)), AppIconRenderer.SQUARE);
        pixel(transparent, 18, 18, 0xff202832, "transparent legacy icon is filled");
        pixel(transparent, 64, 64, LOGO, "legacy logo retained");
        Bitmap crop = render(new AppIconRenderer(new Logo(true)), AppIconRenderer.SQUARE);
        pixel(crop, 18, 64, LOGO, "wide artwork center-cropped without a side margin");

        Bitmap fallback = render(new AppIconRenderer(null), AppIconRenderer.SQUARE);
        pixel(fallback, 64, 64, 0xff202832, "missing artwork fallback");
        Bitmap resized = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(resized), 40, 40, 20, AppIconRenderer.SQUARE);
        pixel(resized, 21, 21, ART, "cached artwork resizes and translates");
        pixel(resized, 64, 64, 0, "cached bounds do not leak");
        pixel(render(icon, 99), 18, 18, 0, "unknown shape defaults to round");
        Drawable legacy = new Drawable() {
            final Paint paint = new Paint();
            public void draw(Canvas canvas) {
                Rect b = getBounds();paint.setColor(0xff070a13);
                canvas.drawRect(b.left + b.width() * .08f, b.top + b.height() * .08f,
                        b.right - b.width() * .08f, b.bottom - b.height() * .08f, paint);
            }
            public void setAlpha(int alpha) { }
            public void setColorFilter(ColorFilter filter) { }
            public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            public int getIntrinsicWidth() { return 256; }
            public int getIntrinsicHeight() { return 256; }
        };
        pixel(render(new AppIconRenderer(legacy), AppIconRenderer.ROUND), 64, 17, 0xff070a13,
                "legacy transparent padding uses artwork color, not a contrasting rim");
        System.out.println("PASS: " + checks + " icon-rendering checks");
    }

    private static void nestedMatteChecks(){
        final int outer=0xff070a13,inner=0xff101827,ink=0xff38d9ff;
        Bitmap original=legacyFixture(outer,inner,ink);
        Bitmap normalized=original.copy(Bitmap.Config.ARGB_8888,true);
        LegacyIconNormalizer.flattenMatte(normalized);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++){
            int before=original.getPixel(x,y);
            if(before==ink)pixel(normalized,x,y,ink,"nested matte never recolors or moves the logo");
            if(x<25||y<25||x>230||y>230)pixel(normalized,x,y,inner,"inner tile background fills to the edges");
        }
        require(!original.sameAs(normalized),"legacy nested square really removed");
        // No loss of a high-contrast frame or an outer badge that belongs to artwork.
        Bitmap framed=legacyFixture(0xffeeeeee,inner,ink),copy=framed.copy(Bitmap.Config.ARGB_8888,true);
        LegacyIconNormalizer.flattenMatte(framed);require(copy.sameAs(framed),"contrasting frame preserved");
        Bitmap badge=legacyFixture(outer,inner,ink);new Canvas(badge).drawColor(0,android.graphics.PorterDuff.Mode.DST_OVER);
        Paint p=new Paint();p.setColor(0xffffcc00);new Canvas(badge).drawRect(9,9,27,27,p);
        copy=badge.copy(Bitmap.Config.ARGB_8888,true);LegacyIconNormalizer.flattenMatte(badge);require(copy.sameAs(badge),"outer badge preserved");
        Bitmap gradient=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++)gradient.setPixel(x,y,0xff000000|x<<16|y<<8|128);
        copy=gradient.copy(Bitmap.Config.ARGB_8888,true);LegacyIconNormalizer.flattenMatte(gradient);require(copy.sameAs(gradient),"gradient artwork untouched");
        Drawable legacy=new android.graphics.drawable.BitmapDrawable(null,original);
        for(int shape=0;shape<=5;shape++){
            Bitmap filled=render(new AppIconRenderer(legacy),shape);
            pixel(filled,64,29,inner,"all shapes filled with inner background, not old square");
            pixel(filled,64,64,ink,"all shapes retain original logo");
        }
    }
    private static void customShapeChecks(){
        AppIconRenderer icon=new AppIconRenderer(new Logo(false));
        for(int shape:new int[]{AppIconRenderer.RECTANGLE,AppIconRenderer.CUSTOM})for(int ratio:new int[]{50,100,160,250})for(int corner:new int[]{0,18,50})for(int logoSize:new int[]{50,75,100}){
            Bitmap result=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(result),64,64,48,shape,ratio,corner,logoSize);
            Drawable preview=AppIconShape.preview(shape,ART,ratio,corner);preview.setBounds(16,16,112,112);
            Bitmap mask=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);preview.draw(new Canvas(mask));
            int minX=128,minY=128,maxX=0,maxY=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){
                int pixel=result.getPixel(x,y);
                require(Math.abs((pixel>>>24)-(mask.getPixel(x,y)>>>24))<=2,"custom tile matches preview: shape="+shape+" ratio="+ratio+" corners="+corner+" logo="+logoSize+" x="+x+" y="+y);
                if(pixel==LOGO){minX=Math.min(x,minX);minY=Math.min(y,minY);maxX=Math.max(x,maxX);maxY=Math.max(y,maxY);}
            }
            require(maxX>minX&&maxY>minY,"custom shape keeps logo visible");
            require(Math.abs((maxX-minX)-(maxY-minY))<=1,"logo is never stretched by aspect ratio");
            require(result.getPixel(64,64)==LOGO,"custom logo centered");
        }
        Bitmap rectangle=render(new AppIconRenderer(new ColorDrawable(ART)),AppIconRenderer.RECTANGLE);
        pixel(rectangle,18,64,ART,"rectangle filled to left edge");pixel(rectangle,64,30,0,"rectangle has 16:9 proportions");
    }
    private static Bitmap legacyFixture(int outer,int inner,int ink){
        Bitmap b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();
        p.setColor(outer);c.drawRoundRect(14,19,242,237,28,28,p);
        p.setColor(inner);c.drawRoundRect(28,35,228,221,19,19,p);
        p.setColor(ink);c.drawRect(80,80,176,176,p);return b;
    }

    private static Bitmap render(AppIconRenderer icon, int shape) {
        Bitmap result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(result), 64, 64, 48, shape);
        return result;
    }

    private static void pixel(Bitmap bitmap, int x, int y, int expected, String message) {
        int actual = bitmap.getPixel(x, y);
        require(actual == expected, message + ": expected " + Integer.toHexString(expected)
                + ", got " + Integer.toHexString(actual));
    }

    private static void require(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }

    private static final class Logo extends Drawable {
        final boolean wide;
        final Paint paint = new Paint();
        Logo(boolean wide) { this.wide = wide; }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            paint.setColor(LOGO);
            canvas.drawRect(bounds.left + bounds.width() * .25f,
                    wide ? bounds.top : bounds.top + bounds.height() * .25f,
                    bounds.right - bounds.width() * .25f,
                    wide ? bounds.bottom : bounds.bottom - bounds.height() * .25f, paint);
        }
        @Override public int getIntrinsicWidth() { return wide ? 512 : 256; }
        @Override public int getIntrinsicHeight() { return 256; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
