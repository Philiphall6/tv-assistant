package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import org.json.*;
import java.util.*;

/** Same validated layout schema as HA; no credentials or HA service actions. */
public class LayoutSettingsActivity extends CardScreen {
    @Override public void onCreate(Bundle b){super.onCreate(b);menu();}
    void menu(){
        screen("Disposition et textes","Barre fixe, positions des blocs, tailles et couleurs");
        if(PanelStore.enabled(this))box.addView(text("Le profil HA est prioritaire lors de la synchronisation. Pour conserver vos changements, utilisez aussi l’onglet Disposition ou Apparence dans HA.",14,0xffc7d1da));
        Set<String> sections=new LinkedHashSet<>();JSONObject rules=PanelStore.schema(this);
        for(Iterator<String> it=rules.keys();it.hasNext();){JSONObject r=rules.optJSONObject(it.next());String section=r.optString("section");if(!section.isEmpty())sections.add(section);}
        for(String section:sections)button(box,section,()->edit(section));
        button(footer,"Retour",this::finish);TvFocus.focusFirst(root);
    }
    void edit(String section){
        screen(section,"Positions en % de la zone libre. Évitez les chevauchements ; la barre reste toujours accessible avec Retour.");
        SharedPreferences prefs=CardStore.prefs(this);JSONObject rules=PanelStore.schema(this);Map<String,EditText> fields=new LinkedHashMap<>();Map<String,Object> choices=new LinkedHashMap<>();
        try{
            for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();JSONObject r=rules.getJSONObject(key);if(!section.equals(r.optString("section")))continue;
                Object value=prefs.getAll().get(key);if(value==null)value=r.get("default");JSONArray options=r.optJSONArray("options");
                if(r.getString("type").equals("boolean")){choices.put(key,value);android.widget.Button toggle=button(box,r.optString("label")+" : "+(Boolean.TRUE.equals(value)?"Oui":"Non"),()->{});toggle.setOnClickListener(v->{boolean enabled=!Boolean.TRUE.equals(choices.get(key));choices.put(key,enabled);toggle.setText(r.optString("label")+" : "+(enabled?"Oui":"Non"));});}
                else if(options!=null){final Object initial=value;String[] labels=new String[options.length()];for(int i=0;i<labels.length;i++)labels[i]=options.getJSONArray(i).getString(1);choices.put(key,initial);android.widget.Button choice=button(box,r.getString("label")+" : "+labels[((Number)value).intValue()],()->{});choice.setOnClickListener(v->choices(r.optString("label"),labels,index->{try{choices.put(key,options.getJSONArray(index).get(0));choice.setText(r.optString("label")+" : "+labels[index]);}catch(Exception ignored){}}));}
                else{EditText input=field(r.getString("label")+(r.getString("type").equals("integer")?" · "+r.getInt("min")+"–"+r.getInt("max") :r.optString("format").equals("color")?" · #RRGGBB":""),String.valueOf(value),false);if(r.getString("type").equals("integer"))input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);fields.put(key,input);}
            }
        }catch(Exception e){message("Lecture des réglages impossible");}
        button(footer,"Retour",this::menu);
        button(footer,"Enregistrer",()->{try{JSONObject values=new JSONObject(choices);for(Map.Entry<String,EditText> field:fields.entrySet()){String raw=field.getValue().getText().toString().trim();values.put(field.getKey(),rules.getJSONObject(field.getKey()).getString("type").equals("integer")?Integer.valueOf(raw):raw);}PanelStore.validate(this,new JSONObject().put("schema",1).put("settings",values));SharedPreferences.Editor editor=prefs.edit();for(Iterator<String> it=values.keys();it.hasNext();){String k=it.next();PanelStore.put(editor,k,values.get(k));}editor.apply();menu();message("Réglages enregistrés sur cette TV");}catch(Exception e){message("Vérifiez les valeurs et les couleurs #RRGGBB");}});TvFocus.focusFirst(root);
    }
}
