package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class HaClient {
    static final String PREFS = "maison_tv";

    interface Callback { void done(boolean ok, String message); }
    interface StatesCallback { void done(boolean ok, JSONArray states, String message); }

    static boolean isConfigured(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !p.getString("ha_url", "").isEmpty() && !TokenStore.get(context).isEmpty();
    }

    static void test(Context context, Callback cb) { request(context, "GET", "/api/", null, cb); }

    static void callService(Context context, String domain, String service, JSONObject body, Callback cb) {
        if(!domain.matches("[a-z0-9_]+")||!service.matches("[a-z0-9_]+")){cb.done(false,"Action invalide");return;}
        request(context,"POST","/api/services/"+domain+"/"+service,body.toString(),cb);
    }

    static void callService(Context context, String domain, String service, String entity, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("entity_id", entity);
            request(context, "POST", "/api/services/" + domain + "/" + service, body.toString(), cb);
        } catch (Exception e) { cb.done(false, e.getMessage()); }
    }

    static void getStates(Context context, StatesCallback cb) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String base = p.getString("ha_url", "").trim().replaceAll("/+$", "");
        String token = TokenStore.get(context).trim();
        if (base.isEmpty() || token.isEmpty()) { cb.done(false, null, "Home Assistant n’est pas configuré"); return; }
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(ServerAddress.normalize(base) + "/api/states").openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod("GET"); c.setConnectTimeout(6000); c.setReadTimeout(10000);
                c.setRequestProperty("Authorization", "Bearer " + token);
                int code = c.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String response="";
                if (stream != null) try (InputStream in=stream) { response=new String(SharedPhotoAlbum.bounded(in,16*1024*1024),StandardCharsets.UTF_8); }
                if (code >= 200 && code < 300) cb.done(true, new JSONArray(response), "Connecté"); else cb.done(false, null, "Erreur HTTP " + code);
            } catch (Exception e) { cb.done(false, null, e.getClass().getSimpleName() + " : " + e.getMessage()); }
            finally { if (c != null) c.disconnect(); }
        }).start();
    }

    private static void request(Context context, String method, String path, String body, Callback cb) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String base = p.getString("ha_url", "").trim().replaceAll("/+$", "");
        String token = TokenStore.get(context).trim();
        if (base.isEmpty() || token.isEmpty()) { cb.done(false, "Home Assistant n’est pas configuré"); return; }
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(ServerAddress.normalize(base) + path).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod(method); c.setConnectTimeout(5000); c.setReadTimeout(7000);
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Content-Type", "application/json");
                if (body != null) {
                    c.setDoOutput(true);
                    try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
                }
                int code = c.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                if (stream != null) try (InputStream in=stream) { SharedPhotoAlbum.bounded(in,1024*1024); }
                cb.done(code >= 200 && code < 300, code >= 200 && code < 300 ? "Connecté" : "Erreur HTTP " + code);
            } catch (Exception e) { cb.done(false, e.getClass().getSimpleName() + " : " + e.getMessage()); }
            finally { if (c != null) c.disconnect(); }
        }).start();
    }
}
