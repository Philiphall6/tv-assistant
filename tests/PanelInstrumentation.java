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
        CardStore.prefs(getTargetContext()).edit().putBoolean("setup_seen",true).putBoolean(PlaybackPermission.ASKED,true).apply();
        try{int id=getTargetContext().getResources().getIdentifier("launcher_profile_schema","raw",getTargetContext().getPackageName());
            try(InputStream in=getTargetContext().getResources().openRawResource(id)){PanelStore.schema=new JSONObject(new String(SharedPhotoAlbum.bounded(in,1024*1024),StandardCharsets.UTF_8));}
            AmbientModeTest.run(this);
            NotificationTest.run(this);
            android.util.Log.i("TVA-Tests", "PASS: " + NotificationTest.checks + " notification, sleep/no-replay and settings checks");
            FireTvCompatibilityTest.run(this);
            android.util.Log.i("TVA-Tests", "PASS: " + FireTvCompatibilityTest.checks + " experimental Fire TV compatibility checks (not hardware validation)");
            android.util.Log.i("TVA-Tests", "PASS: " + AmbientModeTest.checks + " Ambient Mode checks");
            MaintenanceTest.run(this);
            ApkUpdateTest.run(this);
            android.util.Log.i("TVA-Tests", "PASS: " + ApkUpdateTest.checks + " APK download/installer security checks");
            android.util.Log.i("TVA-Tests", "PASS: " + MaintenanceTest.checks + " update/diagnostic/default-wallpaper checks");
            PlaybackRemoteTest.runUi(this);
            LocalizationTest.run(this);PanelRuntimeTest.run(null);checkLogo();checkInstallationBrand();AppIconRendererTest.main(new String[0]);CardConfigTest.main(new String[0]);HaWebAuthTest.run(this);HaLiveClientTest.run(this);WallpaperColorTest.run(this);AppActionsTest.run(this);HomeExperienceTest.run();PlaybackRemoteTest.run();SecurityTest.run(this);HeatPumpStateTest.main(new String[0]);EntitySearchTest.main(new String[0]);PhotoAlbumTest.run(this);result.putString("stream","PASS: "+PanelRuntimeTest.checks+" native validation/layout/logo checks, "+LocalizationTest.checks+" language checks, "+AppIconRendererTest.checks+" icon checks, "+CardConfigTest.checks+" card checks and "+HaWebAuthTest.checks+" WebView auth checks and "+HaLiveClientTest.checks+" live-protocol checks and "+WallpaperColorTest.checks+" wallpaper checks and "+AppActionsTest.checks+" app-menu checks and "+HomeExperienceTest.checks+" home/voice checks and "+PlaybackRemoteTest.checks+" playback/remote checks and "+SecurityTest.checks+" credential/HTTPS security checks and "+PhotoAlbumTest.checks+" album checks\n");finish(-1,result);
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
    private void checkInstallationBrand() {
        android.content.Context context = getTargetContext();
        android.content.pm.ApplicationInfo info = context.getApplicationInfo();
        int launcherIcon = context.getResources().getIdentifier("app_icon", "drawable", context.getPackageName());
        int installationIcon = context.getResources().getIdentifier("installation_icon", "drawable", context.getPackageName());
        check(info.icon == installationIcon && info.icon != launcherIcon, "Installation and launcher marks are separate");
        Bitmap icon = render(info.loadIcon(context.getPackageManager()), 256, 256);
        Bitmap banner = render(info.loadBanner(context.getPackageManager()), 1200, 675);
        try {
            check(icon.getPixel(0, 0) == 0, "Icon rounded corner is transparent");
            check(icon.getPixel(128, 20) == 0xff18bcf2, "Icon cyan background");
            check(icon.getPixel(60, 90) == 0xffffffff, "Icon white TV screen");
            for (int[] point : new int[][]{{128,83},{99,124},{157,107},{128,160}})
                check(icon.getPixel(point[0], point[1]) == 0xff18bcf2, "Icon connected branch inside TV");
            check(icon.getPixel(72, 191) == 0xffffffff, "Icon TV left foot");
            check(icon.getPixel(184, 191) == 0xffffffff, "Icon TV right foot");
            check(banner.getPixel(100, 100) == 0xff18bcf2, "Banner cyan background");
            check(banner.getPixel(260, 300) == 0xffffffff, "Banner white TV screen");
            for (int[] point : new int[][]{{320,296},{294,333},{346,318}})
                check(banner.getPixel(point[0], point[1]) == 0xff18bcf2, "Banner connected branch");
            int whitePixels = 0;
            for (int y = 285; y < 365; y++) for (int x = 438; x < 1030; x++)
                if (banner.getPixel(x, y) == 0xffffffff) whitePixels++;
            check(whitePixels > 5000, "Outlined TV Assistant wordmark renders without external font");
        } finally { icon.recycle(); banner.recycle(); }
    }
    private Bitmap render(android.graphics.drawable.Drawable drawable, int width, int height) {
        check(drawable != null, "Packaged brand drawable exists");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
    private void check(boolean valid, String message) {
        PanelRuntimeTest.checks++;
        if (!valid) throw new AssertionError(message);
    }
}
