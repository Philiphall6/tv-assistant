package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.Arrays;

/** Audit APK only. No real HA server, photo access or system-setting changes. */
final class AmbientModeTest {
    static int checks;

    static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }

    static String[] systemSettings(Instrumentation test) {
        android.content.ContentResolver resolver = test.getTargetContext().getContentResolver();
        return new String[]{
            Settings.System.getString(resolver, Settings.System.SCREEN_OFF_TIMEOUT),
            Settings.Secure.getString(resolver, "screensaver_enabled"),
            Settings.Secure.getString(resolver, "screensaver_components"),
            Settings.Secure.getString(resolver, "screensaver_activate_on_sleep"),
            Settings.Secure.getString(resolver, "sleep_timeout")
        };
    }

    static void idleWindow(android.app.Activity activity) {
        check((activity.getWindow().getAttributes().flags
            & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0,
            activity.getClass().getSimpleName() + " allows Ambient Mode");
        check(!holdsScreen(activity.getWindow().getDecorView()),
            "No child view keeps the idle screen awake");
    }

    static boolean holdsScreen(View view) {
        if (view.getKeepScreenOn()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (holdsScreen(group.getChildAt(i))) return true;
        }
        return false;
    }

    static void run(Instrumentation test) {
        check(test.getTargetContext().getPackageName().endsWith(".audit"), "Isolated audit target");
        String[] before = systemSettings(test);
        SharedPreferences prefs = CardStore.prefs(test.getTargetContext());
        String previousUrl = prefs.getString("ha_url", null);
        // A valid origin exercises the dashboard setup, but cannot reach any household server.
        prefs.edit().putString("ha_url", "https://127.0.0.1:1").commit();
        MainActivity home = null;
        HaDashboardActivity dashboard = null;
        try {
            home = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final MainActivity launcher = home;
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                idleWindow(launcher);
                check((launcher.getWindow().getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0, "Fullscreen layout preserved");
            });
            dashboard = (HaDashboardActivity) test.startActivitySync(new Intent(test.getTargetContext(), HaDashboardActivity.class)
                .putExtra("dashboard_path", "/lovelace/0").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final HaDashboardActivity page = dashboard;
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                check(!page.isFinishing() && page.surface != null && page.web != null, "Dashboard initialized");
                idleWindow(page);
                page.finish();
            });
            test.waitForIdleSync();
            test.runOnMainSync(() -> idleWindow(launcher));
            check(Arrays.equals(before, systemSettings(test)), "Screensaver, delay and power settings unchanged");
        } finally {
            final MainActivity launcher = home;
            final HaDashboardActivity page = dashboard;
            test.runOnMainSync(() -> {
                if (page != null && !page.isFinishing()) page.finish();
                if (launcher != null && !launcher.isFinishing()) launcher.finish();
            });
            test.waitForIdleSync();
            if (previousUrl == null) prefs.edit().remove("ha_url").commit();
            else prefs.edit().putString("ha_url", previousUrl).commit();
        }
    }
}
