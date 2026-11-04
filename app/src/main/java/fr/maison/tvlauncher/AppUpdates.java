package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Public release metadata only. No HA client, credentials, device identifier or APK execution. */
final class AppUpdates {
    static final String REPOSITORY = "https://github.com/Philiphall6/tv-assistant";
    static final String RELEASES = REPOSITORY + "/releases";
    static final String API = "https://api.github.com/repos/Philiphall6/tv-assistant/releases/latest";
    static final long DAY = 24 * 60 * 60 * 1000L;
    static final int MAX_RESPONSE = 512 * 1024;
    private static final String NUMBER = "(0|[1-9][0-9]{0,5})";
    static final Pattern VERSION = Pattern.compile(NUMBER + "\\." + NUMBER + "\\." + NUMBER);
    static final Pattern APK = Pattern.compile("TVAssistant-(" + NUMBER + "\\." + NUMBER + "\\." + NUMBER + ")\\.apk");
    private static final AtomicBoolean checking = new AtomicBoolean();
    enum Result { OK, NETWORK, RATE_LIMIT, INVALID, NO_APK, BUSY, WAIT }
    interface Callback { void done(Result result); }
    static final class Release {
        final String version, tag;
        final long size, checksumsSize;
        Release(String version, String tag) { this(version, tag, 0, 0); }
        Release(String version, String tag, long size, long checksumsSize) {
            this.version = version; this.tag = tag; this.size = size; this.checksumsSize = checksumsSize;
        }
        String page() { return RELEASES + "/tag/" + tag; }
        String name() { return "TVAssistant-" + version + ".apk"; }
        String download() { return RELEASES + "/download/" + tag + "/" + name(); }
        String checksums() { return RELEASES + "/download/" + tag + "/SHA256SUMS"; }
    }

    // Separate local preferences: never exported to HA profiles or diagnostic reports.
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("public_app_updates", Context.MODE_PRIVATE);
    }
    static boolean validVersion(String version) { return version != null && VERSION.matcher(version).matches(); }
    static int compare(String left, String right) {
        if (!validVersion(left) || !validVersion(right)) throw new IllegalArgumentException("Invalid version");
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (result != 0) return result;
        }
        return 0;
    }
    static String installed(Context context) {
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return validVersion(version) ? version : "unknown";
        } catch (Exception ignored) { return "unknown"; }
    }
    static boolean validTag(String tag) {
        return tag != null && validVersion(tag.startsWith("v") ? tag.substring(1) : tag);
    }
    static Release parse(JSONObject json) throws Exception {
        if (!Boolean.FALSE.equals(json.get("draft")) || !Boolean.FALSE.equals(json.get("prerelease")))
            throw new IllegalArgumentException("Not a stable release");
        String tag = json.getString("tag_name");
        if (!validTag(tag) || !(RELEASES + "/tag/" + tag).equals(json.getString("html_url")))
            throw new IllegalArgumentException("Unexpected release origin");
        JSONArray assets = json.getJSONArray("assets");
        if (assets.length() > 256) throw new IllegalArgumentException("Too many assets");
        Release newest = null;
        long checksumsSize = 0;
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name");
            if (!names.add(name)) throw new IllegalArgumentException("Duplicate asset");
            if ("SHA256SUMS".equals(name)) {
                if (!"uploaded".equals(asset.optString("state")) || asset.optLong("size") <= 0
                        || asset.optLong("size") > 65536
                        || !(RELEASES + "/download/" + tag + "/SHA256SUMS").equals(asset.optString("browser_download_url")))
                    throw new IllegalArgumentException("Unexpected checksum asset");
                checksumsSize = asset.getLong("size");
            }
            Matcher match = APK.matcher(name);
            if (!match.matches()) continue;
            if (!"uploaded".equals(asset.optString("state")) || asset.optLong("size") <= 0
                    || asset.optLong("size") > 200 * 1024 * 1024L
                    || !(RELEASES + "/download/" + tag + "/" + name).equals(asset.optString("browser_download_url")))
                throw new IllegalArgumentException("Unexpected APK asset");
            String version = match.group(1);
            if (newest == null || compare(version, newest.version) > 0) newest = new Release(version, tag, asset.getLong("size"), 0);
        }
        return newest == null ? null : new Release(newest.version, newest.tag, newest.size, checksumsSize);
    }
    static Release cached(Context context) {
        SharedPreferences p = prefs(context);
        String version = p.getString("version", ""), tag = p.getString("tag", "");
        return validVersion(version) && validTag(tag) ? new Release(version, tag) : null;
    }
    static boolean available(Context context) {
        Release release = cached(context);
        String installed = installed(context);
        return release != null && validVersion(installed) && compare(release.version, installed) > 0;
    }
    static boolean due(boolean automatic, long now, long previous) {
        return automatic && (previous <= 0 || now < previous || now - previous >= DAY);
    }
    static Result lastResult(Context context) {
        try { return Result.valueOf(prefs(context).getString("result", "INVALID")); }
        catch (Exception ignored) { return Result.INVALID; }
    }
    static boolean check(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler ui = new Handler(Looper.getMainLooper());
        if (!checking.compareAndSet(false, true)) { ui.post(() -> callback.done(Result.BUSY)); return false; }
        long now = System.currentTimeMillis(), previous = prefs(app).getLong("attempt", 0);
        if (previous > 0 && now >= previous && now - previous < 60_000) {
            checking.set(false); ui.post(() -> callback.done(Result.WAIT)); return false;
        }
        prefs(app).edit().putLong("attempt", now).apply();
        new Thread(() -> {
            Result result = Result.NETWORK;
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL(API).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "TV-Assistant-Update-Check");
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                int status = connection.getResponseCode();
                if (status == 403 || status == 429) result = Result.RATE_LIMIT;
                else if (status != 200) result = status >= 500 ? Result.NETWORK : Result.INVALID;
                else if (connection.getContentLengthLong() > MAX_RESPONSE) result = Result.INVALID;
                else {
                    Release release;
                    try (InputStream in = connection.getInputStream()) {
                        release = parse(new JSONObject(new String(SharedPhotoAlbum.bounded(in, MAX_RESPONSE), StandardCharsets.UTF_8)));
                    }
                    if (release == null) result = Result.NO_APK;
                    else {
                        prefs(app).edit().putString("version", release.version).putString("tag", release.tag)
                            .putLong("success", now).apply();
                        result = Result.OK;
                    }
                }
            } catch (org.json.JSONException | IllegalArgumentException invalid) { result = Result.INVALID; }
              catch (Exception ignored) { result = Result.NETWORK; }
            finally {
                if (connection != null) connection.disconnect();
                checking.set(false);
            }
            prefs(app).edit().putString("result", result.name()).apply();
            Result finished = result;
            ui.post(() -> callback.done(finished));
        }, "public-release-check").start();
        return true;
    }
    static String message(Result result) {
        switch (result) {
            case NETWORK: return I18n.t("Vérification impossible hors ligne ou réseau indisponible. Réessayez plus tard.");
            case RATE_LIMIT: return I18n.t("GitHub limite temporairement les vérifications. Réessayez plus tard.");
            case NO_APK: return I18n.t("La dernière publication ne contient pas d’APK stable.");
            case BUSY: return I18n.t("Une vérification est déjà en cours.");
            case WAIT: return I18n.t("Veuillez patienter une minute entre deux vérifications.");
            case INVALID: return I18n.t("Réponse de mise à jour non reconnue. Aucun téléchargement effectué.");
            default: return I18n.t("Vérification terminée.");
        }
    }
    static void onHomeResumed(Activity activity) {
        if (!prefs(activity).getBoolean("automatic", false)) return;
        notifyIfAvailable(activity);
        if (!due(true, System.currentTimeMillis(), prefs(activity).getLong("attempt", 0))) return;
        WeakReference<Activity> owner = new WeakReference<>(activity);
        check(activity, result -> {
            Activity home = owner.get();
            if (home != null && !home.isFinishing() && !home.isDestroyed() && home.hasWindowFocus()
                    && prefs(home).getBoolean("automatic", false)) notifyIfAvailable(home);
        });
    }
    private static void notifyIfAvailable(Activity activity) {
        Release release = cached(activity);
        if (!available(activity) || release == null || release.version.equals(prefs(activity).getString("notified", ""))) return;
        Toast.makeText(activity, I18n.t("Mise à jour disponible : ") + release.version + " · "
            + I18n.t("Paramètres → Mises à jour et diagnostic"), Toast.LENGTH_LONG).show();
        prefs(activity).edit().putString("notified", release.version).apply();
    }
}
