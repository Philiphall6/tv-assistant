package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

final class LayoutPresets {
    static final String UNDO="layout_preset_undo";
    static boolean allowed(String key){return key.startsWith("layout_")||key.startsWith("nav_")||key.equals("favorite_icon_size")||key.equals("favorite_cell_width")||key.equals("app_icon_shape");}
    static JSONArray list(Context context) throws Exception {
        int id=context.getResources().getIdentifier("layout_presets","raw",context.getPackageName());
        try(InputStream input=context.getResources().openRawResource(id)){
            return new JSONArray(new String(SharedPhotoAlbum.bounded(input,65536),StandardCharsets.UTF_8));
        }
    }
    static void apply(Context context,SharedPreferences prefs,JSONObject values) throws Exception {
        PanelStore.validate(context,new JSONObject().put("schema",1).put("settings",values));
        JSONObject previous=new JSONObject();SharedPreferences.Editor edit=prefs.edit();
        for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();
            if(!allowed(key))throw new IllegalArgumentException();
            previous.put(key,prefs.contains(key)?prefs.getAll().get(key):JSONObject.NULL);PanelStore.put(edit,key,values.get(key));
        }
        edit.putString(UNDO,previous.toString()).apply();
    }
    static void undo(Context context,SharedPreferences prefs) throws Exception {
        JSONObject previous=new JSONObject(prefs.getString(UNDO,"{}"));SharedPreferences.Editor edit=prefs.edit();
        for(Iterator<String> keys=previous.keys();keys.hasNext();){String key=keys.next();
            if(!PanelStore.schema(context).has(key)||!allowed(key))throw new IllegalArgumentException();
            PanelStore.put(edit,key,previous.get(key));
        }
        edit.remove(UNDO).apply();
    }
}
