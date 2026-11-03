package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic public metadata and privacy canaries; never accesses GitHub or a real HA server. */
final class MaintenanceTest {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked action, String message) throws Exception {
        boolean failed = false; try { action.run(); } catch (Exception expected) { failed = true; }
        check(failed, message);
    }
    static JSONObject release(String version) throws Exception {
        String tag = "v0.7.3", name = "TVAssistant-" + version + ".apk";
        return new JSONObject().put("draft", false).put("prerelease", false).put("tag_name", tag)
            .put("html_url", AppUpdates.RELEASES + "/tag/" + tag)
            .put("assets", new JSONArray().put(new JSONObject().put("name", name).put("state", "uploaded")
                .put("size", 3000000).put("browser_download_url", AppUpdates.RELEASES + "/download/" + tag + "/" + name)));
    }
    static void run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        check(context.getPackageName().endsWith(".audit"), "isolated maintenance tests");
        for (String version : new String[]{"0.0.0", "2.13.6", "2.9.99", "10.0.0", "999999.999999.999999"})
            check(AppUpdates.validVersion(version), "valid numeric APK version");
        for (String version : new String[]{"", "2.13", "v2.13.6", "2.13.6-beta", "02.1.0", "1.0.0\n", "1000000.0.0", "https://private.invalid", "2.13.6+id"})
            check(!AppUpdates.validVersion(version), "untrusted version rejected");
        check(AppUpdates.compare("2.10.0", "2.9.99") > 0, "numeric not lexical comparison");
        check(AppUpdates.compare("3.0.0", "2.99.99") > 0, "major upgrade");
        check(AppUpdates.compare("2.13.6", "2.13.6") == 0, "same version is not an update");
        check(AppUpdates.compare("2.13.5", "2.13.6") < 0, "older APK is not an update");
        rejects(() -> AppUpdates.compare("bad", "1.0.0"), "invalid installed version rejected");
        AppUpdates.Release valid = AppUpdates.parse(release("2.13.7"));
        check(valid.version.equals("2.13.7") && valid.tag.equals("v0.7.3"), "APK and HACS versions are separate");
        check(valid.page().equals(AppUpdates.RELEASES + "/tag/v0.7.3"), "reconstructed official page only");
        for (String flag : new String[]{"draft", "prerelease"}) {
            rejects(() -> AppUpdates.parse(release("2.13.7").put(flag, true)), "non stable release rejected");
            rejects(() -> AppUpdates.parse(release("2.13.7").put(flag, "false")), "wrong boolean type rejected");
        }
        for (String tag : new String[]{"../../private", "v0.7.3?secret=x", "v0.7.3-beta", "v01.2.3", ""})
            rejects(() -> AppUpdates.parse(release("2.13.7").put("tag_name", tag)), "unsafe tag rejected");
        for (String url : new String[]{"https://private.invalid/release", AppUpdates.RELEASES + "/tag/v0.7.3?secret=x", "http://github.com/Philiphall6/tv-assistant/releases/tag/v0.7.3"})
            rejects(() -> AppUpdates.parse(release("2.13.7").put("html_url", url)), "unexpected release URL rejected");
        for (String url : new String[]{"https://evil.invalid/app.apk", "file:///private/app.apk", "javascript:alert(1)", AppUpdates.RELEASES + "/download/v0.7.3/TVAssistant-2.13.7.apk?token=private"}) {
            JSONObject json = release("2.13.7"); json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url);
            rejects(() -> AppUpdates.parse(json), "untrusted asset URL rejected");
        }
        for (long size : new long[]{0, -1, 210_000_000, Long.MAX_VALUE}) {
            JSONObject json = release("2.13.7"); json.getJSONArray("assets").getJSONObject(0).put("size", size);
            rejects(() -> AppUpdates.parse(json), "asset size bounded");
        }
        JSONObject absent = release("2.13.7"); absent.put("assets", new JSONArray());
        check(AppUpdates.parse(absent) == null, "integration-only release is not an APK update");
        for (String name : new String[]{"TVAssistant-2.13.7-audit.apk", "TVAssistant-2.13.7-release-unsigned.apk", "Other-2.13.7.apk", "TVAssistant-2.13.7.apk.exe"}) {
            JSONObject json = release("2.13.7"); json.getJSONArray("assets").getJSONObject(0).put("name", name);
            check(AppUpdates.parse(json) == null, "test or unsigned assets excluded");
        }
        JSONObject multiple = release("2.9.0"); multiple.getJSONArray("assets").put(release("2.10.0").getJSONArray("assets").getJSONObject(0));
        check(AppUpdates.parse(multiple).version.equals("2.10.0"), "highest stable APK selected");
        long now = AppUpdates.DAY * 5;
        check(!AppUpdates.due(false, now, 0), "automatic checks opt-in only");
        check(AppUpdates.due(true, now, 0), "first enabled check");
        check(!AppUpdates.due(true, now, now-1000), "no checks on every resume");
        check(AppUpdates.due(true, now, now-AppUpdates.DAY), "daily interval");
        check(AppUpdates.due(true, now, now+1000), "clock rollback recovers");

        SharedPreferences prefs = CardStore.prefs(context), updates = AppUpdates.prefs(context);
        check(!updates.getBoolean("automatic", false), "no automatic network on first installation");
        Map<String, ?> before = prefs.getAll(), updateBefore = updates.getAll();
        MaintenanceActivity activity = null;
        String canary = "PRIVATE_CANARY_123";
        try {
            prefs.edit().putString("ha_url", "https://" + canary + ".invalid").putString(TokenStore.ENCRYPTED, canary)
                .putString("ha_authenticated_user", canary).putString("ha_panel_device_id", canary)
                .putString("ha_panel_profile", canary).putString("ha_panel_status", canary)
                .putString("photo_album_url", canary).putString("custom_cards_yaml", canary)
                .putString("favorite_apps_order", canary).putString("unexpected_secret", canary).commit();
            Map<String, ?> seeded = prefs.getAll();
            updates.edit().putString("version", "999.0.0").putString("tag", "v0.7.3").commit();
            check(AppUpdates.available(context), "new version shown");
            TechnicalDiagnostics.connection(true);
            String report = TechnicalDiagnostics.report(context);
            check(!report.contains(canary), "private canaries never exported");
            check(!report.contains("https:") && !report.contains("PRIVATE") && !report.contains("eyJ"), "no addresses or credentials");
            JSONObject json = new JSONObject(report);
            Set<String> expected = new HashSet<>(Arrays.asList("diagnostic_schema", "app_version", "android_api", "release_build", "default_home", "network", "ha_credential_present", "ha_last_observed", "ha_profile_enabled", "continue_watching_permission", "overlay_permission", "automatic_update_checks", "update_available_cached"));
            Set<String> keys = new HashSet<>();
            for (java.util.Iterator<String> it=json.keys(); it.hasNext();) keys.add(it.next());
            check(keys.equals(expected), "strict diagnostic allowlist");
            check(json.getString("ha_last_observed").equals("CONNECTED"), "last connection state only");
            check(json.getBoolean("ha_credential_present"), "reports presence without decrypting token");
            check(seeded.equals(prefs.getAll()), "diagnostic never changes or migrates credentials and settings");
            check(report.length() < 2000, "small bounded report");
            Intent share = MaintenanceActivity.shareIntent(report), save = MaintenanceActivity.saveIntent();
            check(Intent.ACTION_SEND.equals(share.getAction()) && "text/plain".equals(share.getType()), "explicit Android share");
            check(report.equals(share.getStringExtra(Intent.EXTRA_TEXT)) && share.getData() == null, "exact preview text shared, no URL");
            check(Intent.ACTION_CREATE_DOCUMENT.equals(save.getAction()) && "application/json".equals(save.getType()), "user chooses export destination");
            check(save.getStringExtra(Intent.EXTRA_TITLE).equals("tv-assistant-diagnostic.json"), "filename has no user name or identifier");
            activity = (MaintenanceActivity) test.startActivitySync(new Intent(context, MaintenanceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final MaintenanceActivity screen = activity;
            test.runOnMainSync(() -> {
                check(screen.version.getText().toString().contains("999.0.0"), "available version visible on actual TV screen");
                check(screen.automatic.getText().toString().contains(I18n.t("Désactivée")), "automatic check disabled visibly");
                check(screen.checkButton.isEnabled(), "manual check reachable");
                check(!AmbientModeTest.holdsScreen(screen.root), "maintenance does not block screensaver");
                check(find(screen.root, I18n.t("Afficher le diagnostic sans données personnelles")), "diagnostic reachable with remote");
                screen.showReport();
            });
            test.waitForIdleSync();
            check(!updates.contains("attempt"), "opening diagnostics and update settings makes no network request");
            updates.edit().putString("version", AppUpdates.installed(context)).commit();
            check(!AppUpdates.available(context), "same installed version never marked update");
            updates.edit().putString("tag", "../private?token=x").commit();
            check(AppUpdates.cached(context) == null && !AppUpdates.available(context), "corrupt cache fails closed");
        } finally {
            final MaintenanceActivity screen = activity;
            if (screen != null) test.runOnMainSync(screen::finish);
            restore(prefs, before); restore(updates, updateBefore);
        }
        // Default branding affects new installs only; all existing wallpaper modes remain intact.
        PanelRuntimeTest.Memory fresh = new PanelRuntimeTest.Memory();
        WallpaperBackdrop.initialize(fresh.prefs);
        check(fresh.prefs.getInt("wallpaper_mode", -1) == WallpaperBackdrop.BRAND, "new install uses TV Assistant wallpaper");
        for (int mode = 0; mode <= 6; mode++) {
            PanelRuntimeTest.Memory existing = new PanelRuntimeTest.Memory();
            existing.values.put("wallpaper_mode", mode); existing.values.put("background_dim", 22);
            WallpaperBackdrop.initialize(existing.prefs);
            check(existing.prefs.getInt("wallpaper_mode", -1) == mode && existing.prefs.getInt("background_dim", -1) == 22, "existing wallpaper and dimming preserved");
        }
        Bitmap image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        try {
            WallpaperBackdrop.drawBrand(new Canvas(image), 1920, 1080, context.getDrawable(R.drawable.installation_icon));
            check((image.getPixel(0, 0) >>> 24) == 255 && (image.getPixel(1919, 1079) >>> 24) == 255, "branded wallpaper fills screen");
            check(image.getPixel(0, 0) != image.getPixel(1919, 1079), "calm gradient rendered");
        } finally { image.recycle(); }
        PanelRuntimeTest.Memory grid = new PanelRuntimeTest.Memory();
        for (int columns=3; columns<=12; columns++) for (int rows=1; rows<=6; rows++)
            for (int size : new int[]{48,106,180,240}) for (int label : new int[]{10,15,32}) {
                grid.values.put("app_grid_columns", columns); grid.values.put("app_grid_rows", rows);
                grid.values.put("app_grid_icon_size", size); grid.values.put("app_grid_label_size", label);
                float width=(1920-96-12*(columns-1))/(float)columns, height=(1080-270)/(float)rows;
                float radius=ApplicationsGrid.radius(grid.prefs,width,height);
                check(ApplicationsGrid.columns(grid.prefs)==columns && ApplicationsGrid.rows(grid.prefs)==rows,"grid dimensions respected");
                check(radius>0 && radius<=size/2f && radius<=width*.4f && radius<=height*.32f,"large icons fit their cell");
                check(height*.4f+radius < height-20-label,"icon never overlaps its label");
            }
        grid.values.put("app_grid_columns",99); grid.values.put("app_grid_rows",-1);
        check(ApplicationsGrid.columns(grid.prefs)==12 && ApplicationsGrid.rows(grid.prefs)==1,"invalid local grid settings clamped");
    }
    static boolean find(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return true;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) if (find(((ViewGroup)view).getChildAt(i), text)) return true;
        return false;
    }
    static void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String)value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean)value);
            else if (value instanceof Integer) editor.putInt(key, (Integer)value);
            else if (value instanceof Long) editor.putLong(key, (Long)value);
            else if (value instanceof Float) editor.putFloat(key, (Float)value);
        }
        editor.commit();
    }
}
