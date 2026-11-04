package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;

/** Allowlisted technical facts only. Never serialize preferences, logs, exceptions or identifiers. */
final class TechnicalDiagnostics {
    enum Connection { NOT_OBSERVED, CONNECTED, DISCONNECTED }
    private static volatile Connection lastConnection = Connection.NOT_OBSERVED;
    static void connection(boolean online) { lastConnection = online ? Connection.CONNECTED : Connection.DISCONNECTED; }
    enum Network { UNKNOWN, OFFLINE, UNVALIDATED, VALIDATED }
    static Network network(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return Network.UNKNOWN;
            NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
            if (caps == null) return Network.OFFLINE;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? Network.VALIDATED : Network.UNVALIDATED;
        } catch (Exception ignored) { return Network.UNKNOWN; }
    }
    static String report(Context context) {
        try {
            SharedPreferences p = CardStore.prefs(context);
            JSONObject out = new JSONObject();
            out.put("diagnostic_schema", 1);
            out.put("app_version", AppUpdates.installed(context));
            out.put("android_api", Build.VERSION.SDK_INT);
            out.put("platform", TvPlatform.isFireTv(context) ? "FIRE_TV" : "ANDROID");
            out.put("release_build", (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0);
            out.put("default_home", context.getPackageName().equals(LauncherSetupActivity.effectiveHome(context)));
            out.put("network", network(context).name());
            out.put("ha_credential_present", p.contains(TokenStore.ENCRYPTED) || p.contains("ha_token"));
            // Last state observed on the launcher, NOT a new request or a live connection test.
            out.put("ha_last_observed", lastConnection.name());
            out.put("ha_profile_enabled", PanelStore.enabled(context));
            out.put("continue_watching_permission", PlaybackPermission.granted(context));
            out.put("continue_watching_supported", PlaybackPermission.supported(context));
            out.put("overlay_permission", Settings.canDrawOverlays(context));
            out.put("automatic_update_checks", AppUpdates.prefs(context).getBoolean("automatic", false));
            out.put("update_available_cached", AppUpdates.available(context));
            return out.toString(2) + "\n";
        } catch (Exception ignored) {
            // Even failures are fixed strings: no server name or secret from an exception message.
            return "{\"diagnostic_schema\":1,\"status\":\"unavailable\"}\n";
        }
    }
}
