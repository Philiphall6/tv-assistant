package fr.maison.tvlauncher;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import org.json.JSONObject;

/** AND conditions, exact HA states or finite numeric comparisons. No expressions. */
final class CardConditions {
    static void validate(Object raw){
        if(!(raw instanceof List)||((List<?>)raw).size()>8)throw new IllegalArgumentException("conditions : 8 maximum");
        for(Object item:(List<?>)raw){
            Map<String,Object> c=CardConfig.map(item,"Condition");CardConfig.keys(c,"Condition","entity","operator","value");
            if(!CardConfig.str(c,"entity","").matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new IllegalArgumentException("Entité de condition invalide");
            String op=CardConfig.str(c,"operator","");Object v=c.get("value");
            if(!Arrays.asList("state","not_state","above","below").contains(op))throw new IllegalArgumentException("Opérateur de condition invalide");
            if(op.equals("state")||op.equals("not_state")){if(!(v instanceof String)||((String)v).isEmpty()||((String)v).length()>255)throw new IllegalArgumentException("État de condition attendu");}
            else if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw new IllegalArgumentException("Seuil numérique attendu");
        }
    }
    static boolean visible(Map<String,Object> card,Map<String,JSONObject> states,boolean online){
        Object raw=card.get("conditions");if(raw==null)return true;
        try{
            validate(raw);List<?> conditions=(List<?>)raw;if(conditions.isEmpty())return true;if(!online)return false;
            for(Object item:conditions){Map<String,Object> c=CardConfig.map(item,"Condition");JSONObject entity=states.get(c.get("entity"));String state=entity==null?"unavailable":entity.optString("state","unavailable");
                if(state.equals("unknown")||state.equals("unavailable"))return false;
                String op=(String)c.get("operator");Object value=c.get("value");
                if(op.equals("state")&&!state.equals(value)||op.equals("not_state")&&state.equals(value))return false;
                if(op.equals("above")||op.equals("below")){double number=Double.parseDouble(state),limit=((Number)value).doubleValue();if(!Double.isFinite(number)||!(op.equals("above")?number>limit:number<limit))return false;}
            }return true;
        }catch(RuntimeException invalid){return false;}
    }
}
