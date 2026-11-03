package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Uses Android's real font initialization; all test settings remain in memory. */
public final class PanelInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){Bundle result=new Bundle();
        if(!getTargetContext().getPackageName().endsWith(".audit")){result.putString("stream","REFUSED: use the isolated audit APK, never an installed personal launcher");finish(1,result);return;}
        CardStore.prefs(getTargetContext()).edit().putBoolean("setup_seen",true).apply();
        try{int id=getTargetContext().getResources().getIdentifier("launcher_profile_schema","raw",getTargetContext().getPackageName());
            try(InputStream in=getTargetContext().getResources().openRawResource(id)){PanelStore.schema=new JSONObject(new String(SharedPhotoAlbum.bounded(in,1024*1024),StandardCharsets.UTF_8));}
            LocalizationTest.run(this);PanelRuntimeTest.run(null);checkLogo();AppIconRendererTest.main(new String[0]);CardConfigTest.main(new String[0]);HaWebAuthTest.run(this);HaLiveClientTest.run(this);WallpaperColorTest.run(this);AppActionsTest.run(this);HomeExperienceTest.run();SecurityTest.run(this);HeatPumpStateTest.main(new String[0]);EntitySearchTest.main(new String[0]);PhotoAlbumTest.run(this);result.putString("stream","PASS: "+PanelRuntimeTest.checks+" native validation/layout/logo checks, "+LocalizationTest.checks+" language checks, "+AppIconRendererTest.checks+" icon checks, "+CardConfigTest.checks+" card checks and "+HaWebAuthTest.checks+" WebView auth checks and "+HaLiveClientTest.checks+" live-protocol checks and "+WallpaperColorTest.checks+" wallpaper checks and "+AppActionsTest.checks+" app-menu checks and "+HomeExperienceTest.checks+" home/voice checks and "+SecurityTest.checks+" credential/HTTPS security checks and "+PhotoAlbumTest.checks+" album checks\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(1,result);}
    }

    private void checkLogo() {
        BrandLogo logo = new BrandLogo(getTargetContext());
        Bitmap bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        try {
            for (int color : new int[]{BrandLogo.BLUE, 0xfff4a83d, 0xff03a9f4, BrandLogo.BLUE}) {
                bitmap.eraseColor(0);
                logo.draw(canvas, 0, 0, 240, color);
                check(bitmap.getPixel(120, 40) == color, "House color");
                check(bitmap.getPixel(20, 220) == color, "Filled house");
                check(bitmap.getPixel(120, 77) == 0xfff2f4f9, "White top antenna");
                check(bitmap.getPixel(60, 174) == 0xfff2f4f9, "White left antenna");
                check(bitmap.getPixel(180, 140) == 0xfff2f4f9, "White right antenna");
                check(bitmap.getPixel(120, 220) == 0xfff2f4f9, "White antenna stem");
                check(bitmap.getPixel(0, 0) == 0, "No square container");
            }
        } finally { bitmap.recycle(); }
    }
    private void check(boolean valid, String message) {
        PanelRuntimeTest.checks++;
        if (!valid) throw new AssertionError(message);
    }
}
