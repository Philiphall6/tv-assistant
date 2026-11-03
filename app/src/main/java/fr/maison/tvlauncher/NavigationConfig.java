package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

/** Stable destinations separate from their visual order; brand text is never configurable. */
final class NavigationConfig {
    static final int BRAND=1000,VOICE=1001;
    static final String[] TYPES={"home","applications","house","tv_settings","launcher_settings"};
    static final String[] LABELS={"Pour vous","Applications","Maison","Paramètres TV","Home Assistant"};
    static final String[] ICONS={"mdi:home","mdi:apps","mdi:home-automation","mdi:television","mdi:cog"};
    static List<Map<String,Object>> defaults(){List<Map<String,Object>> list=new ArrayList<>();Map<String,Object> voice=new LinkedHashMap<>();voice.put("id","voice");voice.put("type","voice");voice.put("label","Recherche");voice.put("icon","mdi:microphone");voice.put("display",1);list.add(voice);for(int i=0;i<5;i++){Map<String,Object> item=new LinkedHashMap<>();item.put("id",TYPES[i]);item.put("type",TYPES[i]);item.put("label",LABELS[i]);item.put("icon",ICONS[i]);list.add(item);}return list;}
    static List<Map<String,Object>> validate(Object value){
        if(!(value instanceof List)||((List<?>)value).size()>40)throw new IllegalArgumentException("navigation : liste de 0 à 40 onglets");
        List<Map<String,Object>> list=new ArrayList<>();Set<String> ids=new HashSet<>(),types=new HashSet<>();
        for(Object entry:(List<?>)value){Map<String,Object> item=CardConfig.map(entry,"Onglet");CardConfig.keys(item,"Onglet","id","type","label","icon","color","icon_color","text_size","icon_size","display","enabled","package","path");
            String id=CardConfig.str(item,"id",""),type=CardConfig.str(item,"type",""),label=CardConfig.str(item,"label","");
            if(!id.matches("[a-z0-9][a-z0-9_-]{0,39}")||!ids.add(id))throw new IllegalArgumentException("Identifiant d’onglet invalide ou en double");
            if(!Arrays.asList("home","applications","house","tv_settings","launcher_settings","voice","app","ha_dashboard").contains(type))throw new IllegalArgumentException("Destination inconnue");
            if(!type.equals("app")&&!type.equals("ha_dashboard")&&!types.add(type))throw new IllegalArgumentException("Destination native en double");
            if(label.trim().isEmpty()||label.length()>60)throw new IllegalArgumentException("Libellé : 1 à 60 caractères");
            if(item.containsKey("enabled")&&!(item.get("enabled") instanceof Boolean))throw new IllegalArgumentException("enabled : booléen attendu");
            if(item.containsKey("icon")&&!CardConfig.str(item,"icon","").matches("mdi:[a-z0-9-]{1,80}"))throw new IllegalArgumentException("Icône mdi:… attendue");
            for(String k:new String[]{"color","icon_color"})if(item.containsKey(k)&&!CardConfig.str(item,k,"").matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Couleur #RRGGBB attendue");
            if(item.containsKey("text_size"))CardConfig.integer(item,"text_size",10,40);if(item.containsKey("icon_size"))CardConfig.integer(item,"icon_size",16,56);if(item.containsKey("display"))CardConfig.integer(item,"display",0,2);
            if(type.equals("app")){if(!CardConfig.str(item,"package","").matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+"))throw new IllegalArgumentException("Package Android requis");}else if(item.containsKey("package"))throw new IllegalArgumentException("package réservé à app");
            if(type.equals("ha_dashboard")){if(!DashboardUrl.validPath(CardConfig.str(item,"path","")))throw new IllegalArgumentException("Chemin de dashboard HA local requis");}else if(item.containsKey("path"))throw new IllegalArgumentException("path réservé au dashboard HA");
            list.add(item);
        }return list;
    }
    final List<Map<String,Object>> items=new ArrayList<>();
    NavigationConfig(SharedPreferences p){List<Map<String,Object>> list;try{list=p.contains("nav_items")?validate(PanelStore.data(new JSONArray(p.getString("nav_items","[]")),0)):defaults();}catch(Exception e){list=defaults();}for(Map<String,Object> item:list)if(CardConfig.bool(item,"enabled",true))items.add(item);}
    int code(int index){Map<String,Object> item=items.get(index);if("voice".equals(item.get("type")))return VOICE;int type=Arrays.asList(TYPES).indexOf(item.get("type"));return type<0?5+index:type;}
    int index(int code){for(int i=0;i<items.size();i++)if(code(i)==code)return i;return -1;}
    int normalize(int code){return code==BRAND||index(code)>=0?code:BRAND;}
    int move(int code,int direction){int index=index(code);int target=Math.max(-1,Math.min(items.size()-1,index+direction));return target<0?BRAND:code(target);}
    Map<String,Object> selected(int code){int i=index(code);return i<0?null:items.get(i);}
}
