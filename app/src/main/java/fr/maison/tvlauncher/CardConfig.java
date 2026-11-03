package fr.maison.tvlauncher;

import java.util.*;
import java.net.URI;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Data-only native card format. Never evaluates JavaScript, Jinja or YAML objects. */
final class CardConfig {
    static final int MAX_LENGTH = 262144;
    static final String EMPTY = "cards: []\n";
    static final String EXAMPLE = "type: button\nname: Ma carte\nicon: mdi:home\ncontent: ''\nshow_state: true\ntap_action:\n  action: more-info\n";
    static Map<String,Object> map(Object value, String at) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(at + " : objet attendu");
        Map<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : ((Map<?,?>)value).entrySet()) {
            if (!(e.getKey() instanceof String)) throw new IllegalArgumentException(at + " : clé texte attendue");
            out.put((String)e.getKey(), e.getValue());
        }
        return out;
    }
    static Object load(String raw) {
        if (raw.length() > MAX_LENGTH) throw new IllegalArgumentException("Configuration trop volumineuse (256 Ko maximum)");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false); options.setNestingDepthLimit(20); options.setCodePointLimit(MAX_LENGTH);
        try { Object v = new Yaml(new SafeConstructor(options)).load(raw); checkData(v, 0); return v; }
        catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("YAML invalide : vérifiez l’indentation, les clés en double et les types de valeurs.", e); }
    }
    static void checkData(Object v, int depth) {
        if (depth > 20) throw new IllegalArgumentException("Configuration trop imbriquée");
        if (v instanceof Map) for (Map.Entry<?,?> e : ((Map<?,?>)v).entrySet()) {
            if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("Les clés doivent être du texte");
            checkData(e.getValue(),depth+1);
        } else if (v instanceof List) for(Object e:(List<?>)v) checkData(e,depth+1);
        else if (v != null && !(v instanceof String || v instanceof Boolean || v instanceof Number)) throw new IllegalArgumentException("Type YAML non pris en charge ; mettez les dates entre guillemets");
        if (v instanceof Number && !Double.isFinite(((Number)v).doubleValue())) throw new IllegalArgumentException("Nombre invalide");
    }
    static List<Map<String,Object>> parse(String raw) {
        if (raw.trim().isEmpty()) return new ArrayList<>();
        return parseData(load(raw));
    }
    static List<Map<String,Object>> parseData(Object data) {
        checkData(data,0);
        Map<String,Object> root = map(data, "Configuration");
        List<?> items;
        if (root.containsKey("cards")) {
            keys(root,"Configuration","cards","title");
            if (!(root.get("cards") instanceof List)) throw new IllegalArgumentException("cards doit être une liste");
            items=(List<?>)root.get("cards");
        } else items=Collections.singletonList(root);
        // Practical storage guard, not a fixed number of slots in the dashboard.
        List<Map<String,Object>> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(Object item:items){Map<String,Object> card=validate(map(item,"Carte"));String id=str(card,"id","");if(!id.isEmpty()&&!ids.add(id))throw new IllegalArgumentException("Identifiant de carte en double : "+id);result.add(card);}
        return result;
    }
    static Map<String,Object> validate(Map<String,Object> c) {
        keys(c,"Carte","id","type","name","entity","icon","content","color","enabled","show_state","tap_action","hold_action","actions","frequency_entity","defrost_entity","pump_entity","heating_allowed_entity","animate","placement","interactive","package","show_icon","background","background_opacity","title_size","body_size","lovelace","source_yaml","ha_sizing");
        String type=str(c,"type","button");
        if(!Arrays.asList("button","entity","text",HeatPumpState.TYPE,"app","ha_card").contains(type))throw new IllegalArgumentException("Type non natif : "+type+". Utilisez ha_card pour les vraies cartes Home Assistant.");
        if(type.equals("ha_card")){
            if(c.containsKey("ha_sizing")&&(!(c.get("ha_sizing") instanceof String)||!Arrays.asList("yaml","zone").contains(c.get("ha_sizing"))))throw new IllegalArgumentException("Dimensionnement HA : yaml ou zone attendu");
            if(!str(c,"id","").matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Carte HA : identifiant requis");
            Map<String,Object> config=map(c.get("lovelace"),"Carte HA");
            String kind=str(config,"type","");if(kind.isEmpty()||kind.length()>200)throw new IllegalArgumentException("Carte HA : type Lovelace requis");
            if(c.containsKey("tap_action")||c.containsKey("hold_action")||c.containsKey("actions"))throw new IllegalArgumentException("Les actions HA se règlent dans lovelace");
            if(c.containsKey("source_yaml")&&!config.equals(load(str(c,"source_yaml",""))))throw new IllegalArgumentException("Le YAML source et la carte HA ne correspondent pas");
        }else if(c.containsKey("lovelace")||c.containsKey("source_yaml")||c.containsKey("ha_sizing"))throw new IllegalArgumentException("lovelace et ha_sizing sont réservés au type ha_card");
        for(String k:Arrays.asList("id","name","entity","icon","content","color","package","background")) if(c.containsKey(k)) str(c,k,"");
        for(String k:Arrays.asList("enabled","show_state","animate","interactive","show_icon")) if(c.containsKey(k)&&!(c.get(k) instanceof Boolean))throw new IllegalArgumentException(k+" doit valoir true ou false");
        if(c.containsKey("background")&&!str(c,"background","").isEmpty()&&!str(c,"background","").matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Fond attendu : #RRGGBB");
        for(String k:Arrays.asList("background_opacity","title_size","body_size"))if(c.containsKey(k))integer(c,k,k.equals("background_opacity")?0:8,100);
        if(type.equals("app")){if(!str(c,"package","").matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")||!c.containsKey("placement"))throw new IllegalArgumentException("Application : package Android et placement requis");}else if(c.containsKey("package"))throw new IllegalArgumentException("package est réservé au type app");
        if(c.containsKey("placement")){Map<String,Object> place=map(c.get("placement"),"Placement");keys(place,"Placement","x","y","width","height");int x=integer(place,"x",0,99),y=integer(place,"y",0,99),w=integer(place,"width",1,100),h=integer(place,"height",1,100);if(x+w>100||y+h>100)throw new IllegalArgumentException("Le placement doit rester dans la zone de contenu");}
        String entity=str(c,"entity","");if(!entity.isEmpty()&&!entity.matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Entité invalide : "+entity);
        if(HeatPumpState.isCard(c)){if(entity.isEmpty())throw new IllegalArgumentException("La carte PAC nécessite l’entité de priorité");for(String k:HeatPumpState.KEYS)if(!str(c,k,"").matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Entité PAC invalide : "+k);}
        else for(String k:HeatPumpState.KEYS)if(c.containsKey(k))throw new IllegalArgumentException(k+" est réservé aux cartes heat_pump");
        String color=str(c,"color","");if(!color.isEmpty()&&!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Couleur attendue : '#RRGGBB'");
        if(!type.equals("ha_card")&&str(c,"name","").isEmpty()&&entity.isEmpty()&&str(c,"content","").isEmpty())throw new IllegalArgumentException("Ajoutez un titre, une entité ou un texte");
        for(String k:Arrays.asList("tap_action","hold_action"))if(c.containsKey(k))validateAction(map(c.get(k),k),entity);
        if(c.containsKey("actions")) {if(!(c.get("actions") instanceof List))throw new IllegalArgumentException("actions doit être une liste");for(Object a:(List<?>)c.get("actions"))validateAction(map(a,"Action"),entity);}
        return c;
    }
    static void validateAction(Map<String,Object> a,String entity) {
        keys(a,"Action","action","name","perform_action","service","data","target","confirmation","navigation_path","url_path","package");
        String action=str(a,"action","none");
        if(!Arrays.asList("none","more-info","toggle","perform-action","call-service","navigate","url","launch-app").contains(action))throw new IllegalArgumentException("Action non prise en charge : "+action);
        if(a.containsKey("confirmation")&&!(a.get("confirmation") instanceof Boolean))throw new IllegalArgumentException("confirmation doit valoir true ou false");
        for(String k:Arrays.asList("name","perform_action","service","navigation_path","url_path","package"))if(a.containsKey(k))str(a,k,"");
        if(action.equals("toggle")&&entity.isEmpty())throw new IllegalArgumentException("toggle nécessite une entité sur la carte");
        if(action.equals("perform-action")||action.equals("call-service")) {
            String service=str(a,"perform_action",str(a,"service",""));if(!service.matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Action HA attendue : domaine.service");
        }
        if(a.containsKey("data"))map(a.get("data"),"data");
        if(a.containsKey("target")) {Map<String,Object> t=map(a.get("target"),"target");keys(t,"target","entity_id","device_id","area_id","floor_id","label_id");for(Object v:t.values())if(v instanceof List){for(Object id:(List<?>)v)if(!(id instanceof String))throw new IllegalArgumentException("Identifiants cibles : texte attendu");}else if(!(v instanceof String))throw new IllegalArgumentException("Cible : texte ou liste attendu");}
        if(action.equals("navigate")&&!Arrays.asList("home","dashboard","applications").contains(str(a,"navigation_path","")))throw new IllegalArgumentException("Navigation native : home, dashboard ou applications");
        if(action.equals("url")){try{URI u=URI.create(str(a,"url_path",""));if(!Arrays.asList("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Lien attendu : http(s) sans identifiants");}}
        if(action.equals("launch-app")&&!str(a,"package","").matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+"))throw new IllegalArgumentException("Nom de package Android invalide");
    }
    static void keys(Map<String,Object> m,String at,String... allowed){Set<String> set=new HashSet<>(Arrays.asList(allowed));for(String k:m.keySet())if(!set.contains(k))throw new IllegalArgumentException(at+" : option non prise en charge « "+k+" »");}
    static int integer(Map<String,Object> m,String k,int min,int max){Object n=m.get(k);if(!(n instanceof Integer||n instanceof Long)||((Number)n).longValue()<min||((Number)n).longValue()>max)throw new IllegalArgumentException(k+" : entier entre "+min+" et "+max);return ((Number)n).intValue();}
    static String str(Map<String,Object> m,String key,String def){Object v=m.get(key);if(v==null)return def;if(!(v instanceof String))throw new IllegalArgumentException(key+" doit être du texte (utilisez des guillemets)");return (String)v;}
    static boolean bool(Map<String,Object> m,String k,boolean def){return m.containsKey(k)?Boolean.TRUE.equals(m.get(k)):def;}
    static String dump(Object value){DumperOptions d=new DumperOptions();d.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);d.setPrettyFlow(true);d.setIndent(2);return new Yaml(d).dump(value);}
    static String deck(List<Map<String,Object>> cards){Map<String,Object> m=new LinkedHashMap<>();m.put("cards",cards);return dump(m);}
    static Map<String,Object> action(Map<String,Object> c,String key){return c.containsKey(key)?map(c.get(key),key):Collections.singletonMap("action",key.equals("tap_action")?"more-info":"none");}
}
