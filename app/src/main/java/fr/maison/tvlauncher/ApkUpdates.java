package fr.maison.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** User-initiated GitHub downloads. No HA client, token, device ID or shared storage. */
final class ApkUpdates {
    static final long MAX_APK = 200 * 1024 * 1024L;
    static final String MIME = "application/vnd.android.package-archive";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    enum Error { NONE, CANCELLED, NETWORK, INVALID, SIGNATURE, SPACE, BUSY, CHANGED }
    static final class Failure extends IOException {
        final Error error;
        Failure(Error error) { super(error.name()); this.error = error; }
    }
    interface Listener { void progress(int percent); void done(Error error); }
    static final class Task {
        volatile boolean cancelled;
        volatile HttpsURLConnection connection;
        final long deadline = android.os.SystemClock.elapsedRealtime() + 180000;
        void cancel() { cancelled = true; HttpsURLConnection c = connection; if (c != null) c.disconnect(); }
        void check() throws Failure {
            if (cancelled) throw new Failure(Error.CANCELLED);
            if (android.os.SystemClock.elapsedRealtime() > deadline) throw new Failure(Error.NETWORK);
        }
    }
    static File file(Context c) { return new File(c.getCacheDir(), "tv-assistant-update.apk"); }
    static File part(Context c) { return new File(c.getCacheDir(), "tv-assistant-update.part"); }
    static boolean digest(String text) { return text != null && text.matches("[a-fA-F0-9]{64}"); }
    static AppUpdates.Release ready(Context c) {
        android.content.SharedPreferences p = AppUpdates.prefs(c);
        String v = p.getString("ready_version", ""), tag = p.getString("ready_tag", "");
        long size = p.getLong("ready_size", 0);
        if (!AppUpdates.validVersion(v) || !AppUpdates.validTag(tag) || !digest(p.getString("ready_sha", ""))
                || size <= 0 || size > MAX_APK || !file(c).isFile() || file(c).length() != size
                || !AppUpdates.validVersion(AppUpdates.installed(c)) || AppUpdates.compare(v, AppUpdates.installed(c)) <= 0) return null;
        return new AppUpdates.Release(v, tag, size, 0);
    }
    static void clear(Context c) {
        c.revokeUriPermission(UpdateApkProvider.uri(c), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        AppUpdates.prefs(c).edit().remove("ready_version").remove("ready_tag").remove("ready_size").remove("ready_sha").apply();
        // Only these two dedicated cache files; never preferences, credentials or user media.
        file(c).delete(); part(c).delete();
    }
    static boolean allowedRedirect(String location, String original) {
        try {
            URI u = new URI(location);
            if (!"https".equals(u.getScheme()) || u.getRawUserInfo() != null || u.getRawFragment() != null
                    || (u.getPort() != -1 && u.getPort() != 443)) return false;
            if (location.equals(original)) return true;
            return Arrays.asList("release-assets.githubusercontent.com", "objects.githubusercontent.com").contains(u.getHost())
                && u.getRawPath().startsWith("/github-production-release-asset/1444452521/")
                && !u.getRawPath().contains("%") && !u.getRawPath().contains("..") && !u.getRawPath().contains("\\");
        } catch (Exception ignored) { return false; }
    }
    static HttpsURLConnection open(String original, long limit, Task task, boolean assets) throws Exception {
        String url = original;
        for (int redirects = 0; redirects <= 3; redirects++) {
            task.check();
            HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
            task.connection = c;
            c.setInstanceFollowRedirects(false); c.setUseCaches(false);
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "TV-Assistant-Update");
            c.setRequestProperty("Accept", assets ? "application/octet-stream" : "application/vnd.github+json");
            c.setRequestProperty("Accept-Encoding", "identity");
            int status = c.getResponseCode();
            if (status == 200) {
                if (c.getContentLengthLong() > limit) throw new Failure(Error.INVALID);
                task.check(); return c;
            }
            String next = c.getHeaderField("Location"); c.disconnect();
            if (!assets || !Arrays.asList(301,302,303,307,308).contains(status) || !allowedRedirect(next, original))
                throw new Failure(status >= 500 || status == 429 || status == 403 ? Error.NETWORK : Error.INVALID);
            url = next;
        }
        throw new Failure(Error.INVALID);
    }
    static byte[] read(String url, int limit, Task task, boolean assets) throws Exception {
        HttpsURLConnection c = open(url, limit, task, assets);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) {
                task.check(); if (out.size() + n > limit) throw new Failure(Error.INVALID); out.write(buffer, 0, n);
            }
            task.check(); return out.toByteArray();
        } finally { c.disconnect(); }
    }
    static String checksum(String text, String name) throws Failure {
        if (text == null || text.length() > 65536) throw new Failure(Error.INVALID);
        String found = null;
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-fA-F0-9]{64}) [ *]([^\\r\\n]+)").matcher(line);
            if (!m.matches()) throw new Failure(Error.INVALID);
            if (!m.group(2).equals(name)) continue;
            if (found != null) throw new Failure(Error.INVALID);
            found = m.group(1).toLowerCase(Locale.ROOT);
        }
        if (found == null) throw new Failure(Error.INVALID);
        return found;
    }
    static String hash(File file, long expected) throws Exception {
        if (expected <= 0 || expected > MAX_APK || !file.isFile() || file.length() != expected) throw new Failure(Error.INVALID);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; long total = 0; int n;
            while ((n = in.read(buffer)) != -1) {
                total += n; if (total > expected) throw new Failure(Error.INVALID);
                hash.update(buffer, 0, n);
            }
            if (total != expected) throw new Failure(Error.INVALID);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : hash.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    static boolean sameSigners(Signature[] a, Signature[] b) {
        return a != null && b != null && a.length > 0 && a.length == b.length
            && new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));
    }
    static void packagePolicy(PackageInfo current, PackageInfo candidate, String packageName, String version, int api) throws Failure {
        if (current == null || candidate == null || !packageName.equals(candidate.packageName)
                || !version.equals(candidate.versionName) || !AppUpdates.validVersion(current.versionName)
                || AppUpdates.compare(version, current.versionName) <= 0 || candidate.getLongVersionCode() <= current.getLongVersionCode()
                || candidate.applicationInfo == null || candidate.applicationInfo.minSdkVersion > api
                || candidate.applicationInfo.targetSdkVersion < current.applicationInfo.targetSdkVersion
                || (candidate.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            throw new Failure(Error.INVALID);
    }
    static void verify(Context c, File apk, AppUpdates.Release release, String sha) throws Exception {
        if (!digest(sha) || !sha.equalsIgnoreCase(hash(apk, release.size))) throw new Failure(Error.INVALID);
        PackageManager pm = c.getPackageManager();
        PackageInfo current = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        packagePolicy(current, candidate, c.getPackageName(), release.version, Build.VERSION.SDK_INT);
        if (current.signingInfo == null || candidate.signingInfo == null
                || !sameSigners(current.signingInfo.getApkContentsSigners(), candidate.signingInfo.getApkContentsSigners()))
            throw new Failure(Error.SIGNATURE);
        // Android's installer performs final cryptographic APK verification. Never bypass it.
    }
    static Task download(Context context, AppUpdates.Release selected, Listener listener) {
        Context c = context.getApplicationContext(); Handler ui = new Handler(Looper.getMainLooper()); Task task = new Task();
        if (!ACTIVE.compareAndSet(false, true)) { ui.post(() -> listener.done(Error.BUSY)); return task; }
        new Thread(() -> {
            Error result = Error.NONE;
            try {
                clear(c);
                AppUpdates.Release release;
                try {
                    release = AppUpdates.parse(new JSONObject(new String(read(AppUpdates.API, AppUpdates.MAX_RESPONSE, task, false), StandardCharsets.UTF_8)));
                } catch (Failure e) { throw e; }
                  catch (org.json.JSONException | IllegalArgumentException e) { throw new Failure(Error.INVALID); }
                if (release == null || selected == null || !release.version.equals(selected.version) || !release.tag.equals(selected.tag)
                        || !AppUpdates.validVersion(AppUpdates.installed(c)) || AppUpdates.compare(release.version, AppUpdates.installed(c)) <= 0)
                    throw new Failure(Error.CHANGED);
                if (release.checksumsSize <= 0) throw new Failure(Error.INVALID);
                if (c.getCacheDir().getUsableSpace() < release.size * 2 + 8 * 1024 * 1024L) throw new Failure(Error.SPACE);
                byte[] checksums = read(release.checksums(), 65536, task, true);
                if (checksums.length != release.checksumsSize) throw new Failure(Error.INVALID);
                String sha = checksum(new String(checksums, StandardCharsets.UTF_8), release.name());
                HttpsURLConnection connection = open(release.download(), release.size, task, true);
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part(c))) {
                    byte[] buffer = new byte[32768]; long count = 0; int n, last = -1;
                    while ((n = in.read(buffer)) != -1) {
                        task.check(); count += n;
                        if (count > release.size) throw new Failure(Error.INVALID);
                        out.write(buffer, 0, n);
                        int progress = (int) (count * 100 / release.size);
                        if (progress != last) { last = progress; ui.post(() -> listener.progress(progress)); }
                    }
                    if (count != release.size) throw new Failure(Error.INVALID);
                    out.getFD().sync();
                } finally { connection.disconnect(); }
                task.check(); verify(c, part(c), release, sha); task.check();
                if (!part(c).renameTo(file(c))) throw new Failure(Error.SPACE);
                if (!AppUpdates.prefs(c).edit().putString("ready_version", release.version).putString("ready_tag", release.tag)
                    .putLong("ready_size", release.size).putString("ready_sha", sha).commit()) throw new Failure(Error.SPACE);
                task.check();
            } catch (Failure e) { result = e.error; }
              catch (Exception e) { result = task.cancelled ? Error.CANCELLED : Error.NETWORK; }
            finally {
                if (task.connection != null) task.connection.disconnect();
                if (task.cancelled) result = Error.CANCELLED;
                if (result != Error.NONE) clear(c);
                ACTIVE.set(false);
            }
            Error done = result; ui.post(() -> listener.done(done));
        }, "public-apk-download").start();
        return task;
    }
    static Intent installer(Context c) throws Failure {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(UpdateApkProvider.uri(c), MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true);
        intent.setClipData(ClipData.newRawUri("TV Assistant APK", UpdateApkProvider.uri(c)));
        // Only an OS installer receives the temporary read grant, never another app.
        for (ResolveInfo info : c.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY)) {
            if (info.activityInfo != null && info.activityInfo.exported) {
                intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name)); return intent;
            }
        }
        throw new Failure(Error.INVALID);
    }
    static String message(Error error) {
        switch (error) {
            case NONE: return I18n.t("APK téléchargé et contrôlé. Vous pouvez maintenant lancer l’installation Android.");
            case CANCELLED: return I18n.t("Téléchargement annulé. Aucun réglage modifié.");
            case SIGNATURE: return I18n.t("Certificat différent : mise à jour refusée. Ne désinstallez pas votre launcher.");
            case INVALID: return I18n.t("Fichier ou publication non conforme : installation refusée.");
            case SPACE: return I18n.t("Espace disponible insuffisant pour la mise à jour.");
            case BUSY: return I18n.t("Un téléchargement est déjà en cours.");
            case CHANGED: return I18n.t("La version disponible a changé. Vérifiez de nouveau les mises à jour.");
            default: return I18n.t("Téléchargement impossible. Vérifiez le réseau et réessayez.");
        }
    }
}
