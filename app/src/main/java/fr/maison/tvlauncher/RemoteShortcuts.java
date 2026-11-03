package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.widget.Toast;

/** Foreground-only shortcuts. Never intercepts Home, power, volume or other apps. */
final class RemoteShortcuts {
    static final String PREFIX = "remote_key_";
    static boolean assignable(int key) {
        String name=KeyEvent.keyCodeToString(key);
        if(name.equals("KEYCODE_TCL_TV_ON")||name.equals("KEYCODE_TCL_TV_OFF")||name.equals("KEYCODE_TCL_RC_SLEEP"))return false;
        switch (key) {
            case KeyEvent.KEYCODE_UNKNOWN: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return false;
            default: return key > 0 && key <= KeyEvent.getMaxKeyCode();
        }
    }
    // Resolve names at runtime: TCL's vendor numbers collide with newer Android key codes.
    static String vendorAction(String name) {
        switch (name) {
            case "KEYCODE_TCL_NETFLIX": case "KEYCODE_TCL_FORCE_NETFLIX": return "netflix";
            case "KEYCODE_TCL_PRIMEVIDEO": case "KEYCODE_TCL_FORCE_AMAZON": return "prime";
            case "KEYCODE_TCL_YOUTUBE": case "KEYCODE_TCL_FORCE_YOUTUBE": return "youtube";
            case "KEYCODE_TCL_CHANNELPLUS": case "KEYCODE_TCL_TVPLUS": return "tcl";
            case "KEYCODE_TCL_QUICK_MENU": case "KEYCODE_TCL_MORE": return "menu";
            case "KEYCODE_TCL_SET": case "KEYCODE_TCL_QUICK_SETTINGS": case "KEYCODE_TCL_DASHBOARD": return "settings";
            case "KEYCODE_TCL_LIST": case "KEYCODE_TCL_EPG": case "KEYCODE_TCL_LINE_TV": return "tv";
            case "KEYCODE_TCL_ALL_APP": return "apps";
            default: return "";
        }
    }
    static String action(SharedPreferences prefs, int key) {
        if (assignable(key)) {
            String custom = prefs.getString(PREFIX + key, "");
            if (custom.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) return "app:" + custom;
        }
        return vendorAction(KeyEvent.keyCodeToString(key));
    }
    static String[] packages(String action) {
        switch (action) {
            case "netflix": return new String[]{"com.netflix.ninja", "com.netflix.mediaclient"};
            case "prime": return new String[]{"com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"};
            case "youtube": return new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"};
            case "tcl": return new String[]{"com.tcl.channelplus", "com.tcl.waterfall.overseas"};
            case "tv": return new String[]{"com.tcl.tv", "com.google.android.tv", "com.android.tv"};
            default: return action.startsWith("app:") ? new String[]{action.substring(4)} : new String[0];
        }
    }
    static Intent appIntent(PackageManager pm, String[] packages, String self) {
        for (String pkg : packages) {
            if (pkg.equals(self)) continue;
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return null;
    }
    static void launch(Activity activity, String action) {
        Intent intent = appIntent(activity.getPackageManager(), packages(action), activity.getPackageName());
        try {
            if (intent == null) throw new android.content.ActivityNotFoundException();
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(activity, I18n.t("Application indisponible"), Toast.LENGTH_SHORT).show();
        }
    }
    /** Only app buttons here; contextual Menu/CH behavior belongs to the current screen. */
    static boolean dispatchApp(Activity activity, KeyEvent event) {
        String action = action(CardStore.prefs(activity), event.getKeyCode());
        if (packages(action).length == 0) return false;
        if (shouldLaunch(event)) launch(activity, action);
        return true;
    }
    static boolean shouldLaunch(KeyEvent event) { return event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&!event.isCanceled(); }
}
