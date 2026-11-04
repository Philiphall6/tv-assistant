package fr.maison.tvlauncher;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicitly opted-in, authenticated HA profile overlay; never accepts credentials or local paths. */
final class PanelStore {
    static JSONObject schema;
    static synchronized JSONObject schema(Context c) {
        if(schema==null)try(InputStream in=c.getResources().openRawResource(R.raw.launcher_profile_schema)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            schema=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        }catch(Exception e){throw new IllegalStateException("Schéma de profil indisponible");}
        return schema;
    }
    static boolean enabled(Context c){SharedPreferences p=CardStore.prefs(c);return p.getBoolean("ha_panel_enabled",false)&&p.getString("ha_panel_server","").equals(p.getString("ha_url","").trim());}
    static String identity(Context c){SharedPreferences p=CardStore.prefs(c);String id=p.getString("ha_panel_device_id","");if(id.isEmpty()){id=UUID.randomUUID().toString();p.edit().putString("ha_panel_device_id",id).apply();}return id;}
    static void id(String value){if(!value.matches("[a-z0-9][a-z0-9_-]{0,39}"))throw new IllegalArgumentException("Identifiant : lettres minuscules, chiffres et tirets (40 caractères maximum)");}
    /** Protocol JSON is already parsed. Preserve its types without interpreting it as YAML. */
    static Object data(Object value,int depth)throws JSONException {
        if(depth>20)throw new IllegalArgumentException("Configuration trop imbriquée");
        if(value==JSONObject.NULL)return null;
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;Map<String,Object> out=new LinkedHashMap<>();for(Iterator<String> it=o.keys();it.hasNext();){String key=it.next();out.put(key,data(o.get(key),depth+1));}return out;}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;List<Object> out=new ArrayList<>();for(int i=0;i<a.length();i++)out.add(data(a.get(i),depth+1));return out;}
        return value;
    }
    static JSONObject validate(Context c,JSONObject config)throws Exception {
        if(config.toString().getBytes(StandardCharsets.UTF_8).length>CardConfig.MAX_LENGTH)throw new IllegalArgumentException("Profil trop volumineux (256 Ko)");
        Map<String,Object> root=CardConfig.map(data(config,0),I18n.t("Profil"));CardConfig.checkData(root,0);
        CardConfig.keys(root,I18n.t("Profil"),"schema","settings","cards","navigation");
        Object version=root.get("schema");if(!(version instanceof Integer)||((Integer)version)!=1)throw new IllegalArgumentException("Version de profil non prise en charge");
        Map<String,Object> settings=CardConfig.map(root.getOrDefault("settings",new LinkedHashMap<>()),I18n.t("Réglages"));JSONObject rules=schema(c);
        for(Map.Entry<String,Object> entry:settings.entrySet()){
            String key=entry.getKey();Object v=entry.getValue();if(!rules.has(key))throw new IllegalArgumentException("Réglage non autorisé : "+key);
            JSONObject r=rules.getJSONObject(key);String type=r.getString("type");
            if(type.equals("boolean")){if(!(v instanceof Boolean))throw new IllegalArgumentException(key+" : booléen attendu");}
            else if(type.equals("integer")){if(!(v instanceof Integer)||((Integer)v)<r.getInt("min")||((Integer)v)>r.getInt("max"))throw new IllegalArgumentException(key+" : valeur hors limites");}
            else {
                if(!(v instanceof String)||((String)v).length()>r.getInt("max"))throw new IllegalArgumentException(key+" : texte invalide");
                String text=(String)v,format=r.optString("format");
                if(format.equals("dashboard_path")&&!DashboardUrl.validPath(text))throw new IllegalArgumentException("Chemin HA local attendu, ex. /dashboard-tv/tv");
                if(format.equals("color")&&!text.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Couleur attendue : #RRGGBB");
                if(format.equals("entity")&&!text.isEmpty()&&!text.matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Entité invalide");
                if(format.equals("entities")||format.equals("packages")){Set<String> seen=new HashSet<>();if(!text.isEmpty())for(String line:text.split("\\n")){if(!line.matches(format.equals("entities")?"[a-z0-9_]+\\.[a-z0-9_]+":"[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")||!seen.add(line))throw new IllegalArgumentException(key+" : liste invalide ou doublons");}}
                if(format.equals("album")&&!text.isEmpty())SharedPhotoAlbum.albumUri(text);
                if(format.equals("voice_package")&&!text.isEmpty())CustomVoiceService.packageName(text);
                if(format.equals("voice_url")&&!text.isEmpty())CustomVoiceService.template(text);
            }
        }
        Map<String,Object> deck=new LinkedHashMap<>();deck.put("cards",root.getOrDefault("cards",new ArrayList<>()));
        JSONObject out=new JSONObject();out.put("schema",1);out.put("settings",new JSONObject(settings));out.put("cards",new JSONArray(CardConfig.parseData(deck)));if(root.containsKey("navigation"))out.put("navigation",new JSONArray(NavigationConfig.validate(root.get("navigation"))));return out;
    }
    static JSONObject export(Context c)throws Exception {
        SharedPreferences p=CardStore.prefs(c);JSONObject settings=new JSONObject(),rules=schema(c);
        for(Iterator<String> it=rules.keys();it.hasNext();){String k=it.next();Object v=p.getAll().get(k);settings.put(k,v==null?rules.getJSONObject(k).get("default"):v);}
        if(!p.contains("dashboard_entities_order")){
            Set<String> entities=p.getStringSet("dashboard_entities",null);
            settings.put("dashboard_entities_order",entities==null?String.join("\n",p.getString("light_entity","light.salon"),p.getString("scene_movie","scene.soiree_cinema"),p.getString("scene_off","scene.tout_eteindre")):String.join("\n",entities));
        }
        JSONObject config=new JSONObject().put("schema",1).put("settings",settings).put("cards",new JSONArray(CardStore.active(c)));if(p.contains("nav_items"))config.put("navigation",new JSONArray(p.getString("nav_items","[]")));return validate(c,config);
    }
    static JSONObject registration(Context c)throws Exception {
        SharedPreferences p=CardStore.prefs(c);JSONArray apps=new JSONArray();Set<String> seen=new HashSet<>();PackageManager pm=c.getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
            String pkg=r.activityInfo.packageName;if(pkg.equals(c.getPackageName())||!seen.add(pkg)||apps.length()>=500)continue;
            String label=r.loadLabel(pm).toString();apps.put(new JSONObject().put("package",pkg).put("label",label.substring(0,Math.min(160,label.length()))));
        }
        return new JSONObject().put("device_id",identity(c)).put("name",p.getString("ha_panel_name",Build.MODEL)).put("profile",p.getString("ha_panel_profile","salon")).put("config",export(c)).put("apps",apps).put("preview_supported",true);
    }
    static void enable(Context c,String name,String profile)throws Exception {
        id(profile);if(name.trim().isEmpty()||name.length()>80)throw new IllegalArgumentException("Nom de TV attendu (80 caractères maximum)");
        SharedPreferences p=CardStore.prefs(c);if(p.getString("ha_url","").isEmpty()||TokenStore.get(c).isEmpty())throw new IllegalArgumentException(I18n.t("Configurez d’abord la connexion Home Assistant"));
        if(!p.getBoolean("ha_panel_enabled",false)){
            JSONObject backup=new JSONObject();for(Iterator<String> it=schema(c).keys();it.hasNext();){String k=it.next();backup.put(k,p.contains(k)?p.getAll().get(k):JSONObject.NULL);}
            p.edit().putString("ha_panel_backup",backup.toString()).apply();
        }
        p.edit().putBoolean("ha_panel_enabled",true).putString("ha_panel_name",name.trim()).putString("ha_panel_profile",profile).putString("ha_panel_server",p.getString("ha_url","").trim()).putString("ha_panel_status","Liaison au retour à l’accueil…").apply();LinkStatus.panel(c,LinkStatus.WAITING);identity(c);
    }
    static void put(SharedPreferences.Editor e,String key,Object value){if(value==null||value==JSONObject.NULL)e.remove(key);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else if(value instanceof Number)e.putInt(key,((Number)value).intValue());else e.putString(key,(String)value);}
    static boolean apply(Context c,JSONObject snapshot)throws Exception {
        if(!enabled(c))return false;SharedPreferences p=CardStore.prefs(c);String profile=snapshot.getString("profile");id(profile);
        long revision=snapshot.getLong("revision");
        if(revision<1||snapshot.isNull("config")){LinkStatus.panel(c,snapshot.optBoolean("connection_removed")?LinkStatus.FORGOTTEN:snapshot.optBoolean("approval_required")?LinkStatus.PENDING:LinkStatus.NO_PROFILE);status(c,snapshot.optBoolean("approval_required")?I18n.t("Autorisation requise · un administrateur doit affecter un profil à cette TV dans HA"):I18n.t("TV reliée · créez ou affectez un profil dans Home Assistant"));return false;}
        JSONObject config=validate(c,snapshot.getJSONObject("config"));
        LinkStatus.panel(c,LinkStatus.SYNCED);
        if(config.toString().equals(p.getString("ha_panel_config",""))&&profile.equals(p.getString("ha_panel_applied_profile",""))&&revision==p.getLong("ha_panel_revision",0)){status(c,"Profil "+profile+" · version "+revision);return false;}
        JSONObject settings=config.getJSONObject("settings"),backup=new JSONObject(p.getString("ha_panel_backup","{}"));
        JSONObject previousConfig=new JSONObject(p.getString("ha_panel_config","{}"));JSONObject previous=previousConfig.optJSONObject("settings");SharedPreferences.Editor e=p.edit();
        if(config.has("navigation")){if(!backup.has("nav_items")){backup.put("nav_items",p.contains("nav_items")?p.getString("nav_items","[]"):JSONObject.NULL);e.putString("ha_panel_backup",backup.toString());}e.putString("nav_items",config.getJSONArray("navigation").toString());}else if(previousConfig.has("navigation"))put(e,"nav_items",backup.opt("nav_items"));
        if(previous!=null)for(Iterator<String> it=previous.keys();it.hasNext();){String k=it.next();if(!settings.has(k)&&schema(c).has(k))put(e,k,backup.opt(k));}
        for(Iterator<String> it=settings.keys();it.hasNext();){String k=it.next();put(e,k,settings.get(k));}
        e.putString("ha_panel_config",config.toString()).putString("ha_panel_profile",profile).putString("ha_panel_applied_profile",profile).putLong("ha_panel_revision",revision).putString("ha_panel_status","Profil "+profile+" · version "+revision).apply();return true;
    }
    static void disable(Context c)throws Exception {
        SharedPreferences p=CardStore.prefs(c);JSONObject backup=new JSONObject(p.getString("ha_panel_backup","{}"));SharedPreferences.Editor e=p.edit();
        for(Iterator<String> it=backup.keys();it.hasNext();){String k=it.next();if(schema(c).has(k)||k.equals("nav_items"))put(e,k,backup.get(k));}
        e.remove("ha_panel_config").remove("ha_panel_backup").remove("ha_panel_revision").remove("ha_panel_applied_profile").putBoolean("ha_panel_enabled",false).putString("ha_panel_status","Réglages locaux restaurés").apply();
    }
    static void status(Context c,String s){CardStore.prefs(c).edit().putString("ha_panel_status",s).apply();}
    static List<Map<String,Object>> cards(Context c)throws Exception {JSONObject config=new JSONObject(CardStore.prefs(c).getString("ha_panel_config","{}"));return config.has("cards")?CardConfig.parseData(data(new JSONObject().put("cards",config.getJSONArray("cards")),0)):null;}
}
