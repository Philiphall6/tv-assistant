package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import org.json.JSONObject;

/** Local display names only; package IDs, deep links and viewing history never change. */
final class AppNames {
    static final String KEY = "app_label_overrides";
    static final int MAX = 64;
    static JSONObject read(SharedPreferences prefs) {
        try { return new JSONObject(prefs.getString(KEY, "{}")); }
        catch (Exception malformed) { return new JSONObject(); }
    }
    static String label(SharedPreferences prefs, String pkg, String original) {
        String value=read(prefs).optString(pkg, "");
        return valid(value) ? value : original;
    }
    static boolean valid(String value) {
        return value!=null && !value.trim().isEmpty() && value.codePointCount(0,value.length())<=MAX
            && value.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT);
    }
    static void save(SharedPreferences prefs, String pkg, String value) throws Exception {
        if(pkg==null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new IllegalArgumentException();
        JSONObject names=read(prefs);
        if(value==null)names.remove(pkg);
        else {value=value.trim();if(!valid(value))throw new IllegalArgumentException();names.put(pkg,value);}
        if(names.length()==0)prefs.edit().remove(KEY).apply();
        else prefs.edit().putString(KEY,names.toString()).apply();
    }
}
