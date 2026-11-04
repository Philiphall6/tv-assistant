package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import org.json.*;
import java.util.*;

/** Same validated layout schema as HA; no credentials or HA service actions. */
public class LayoutSettingsActivity extends CardScreen {
    String scope;
    String editing;
    @Override public void onCreate(Bundle b){super.onCreate(b);scope=getIntent().getStringExtra("scope");if(scope==null)scope="Accueil";menu();}
    void menu(){
        editing=null;
        screen(I18n.t(scope),I18n.t("Un réglage, un emplacement. Vos valeurs existantes sont conservées."));
        if(PanelStore.enabled(this))box.addView(text(I18n.t("Le profil HA est prioritaire lors de la synchronisation. Pour conserver vos changements, utilisez aussi l’onglet Disposition ou Apparence dans HA."),14,0xffc7d1da));
        Set<String> sections=new LinkedHashSet<>();JSONObject rules=PanelStore.schema(this);
        for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();JSONObject r=rules.optJSONObject(key);if(scope.equals(SettingsCatalog.category(key)))sections.add(SettingsCatalog.group(key,r.optString("section")));}
        for(String section:sections)button(box,I18n.t(section),()->edit(section));
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void edit(String section){
        editing=section;
        screen(I18n.t(section),I18n.t("Positions en % de la zone libre. Évitez les chevauchements ; la barre reste toujours accessible avec Retour."));
        SharedPreferences prefs=CardStore.prefs(this);JSONObject rules=PanelStore.schema(this);Map<String,EditText> fields=new LinkedHashMap<>();Map<String,Object> choices=new LinkedHashMap<>();
        try{
            for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();JSONObject r=rules.getJSONObject(key);if(!scope.equals(SettingsCatalog.category(key))||!section.equals(SettingsCatalog.group(key,r.optString("section"))))continue;
                Object value=prefs.getAll().get(key);if(value==null)value=r.get("default");JSONArray options=r.optJSONArray("options");
                if(r.getString("type").equals("boolean")){choices.put(key,value);android.widget.Button toggle=button(box,I18n.t(r.optString("label"))+" : "+(Boolean.TRUE.equals(value)?I18n.t("Oui"):I18n.t("Non")),()->{});toggle.setOnClickListener(v->{boolean enabled=!Boolean.TRUE.equals(choices.get(key));choices.put(key,enabled);toggle.setText(I18n.t(r.optString("label"))+" : "+(enabled?I18n.t("Oui"):I18n.t("Non")));});}
                else if(options!=null){final Object initial=value;String[] labels=new String[options.length()];for(int i=0;i<labels.length;i++)labels[i]=I18n.t(options.getJSONArray(i).getString(1));choices.put(key,initial);android.widget.Button choice=button(box,I18n.t(r.getString("label"))+" : "+labels[((Number)value).intValue()],()->{});choice.setOnClickListener(v->choices(I18n.t(r.optString("label")),labels,index->{try{choices.put(key,options.getJSONArray(index).get(0));choice.setText(I18n.t(r.optString("label"))+" : "+labels[index]);}catch(Exception ignored){}}));}
                else{EditText input=field(I18n.t(r.getString("label"))+(r.getString("type").equals("integer")?" · "+r.getInt("min")+"–"+r.getInt("max") :r.optString("format").equals("color")?" · #RRGGBB":""),String.valueOf(value),r.optString("format").equals("entities")||r.optString("format").equals("packages"));if(r.getString("type").equals("integer"))input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);fields.put(key,input);}
            }
        }catch(Exception e){message(I18n.t("Lecture des réglages impossible"));}
        button(footer,I18n.t("Retour"),this::menu);
        button(footer,I18n.t("Enregistrer"),()->{try{JSONObject values=new JSONObject(choices);for(Map.Entry<String,EditText> field:fields.entrySet()){String raw=field.getValue().getText().toString().trim();values.put(field.getKey(),rules.getJSONObject(field.getKey()).getString("type").equals("integer")?Integer.valueOf(raw):raw);}PanelStore.validate(this,new JSONObject().put("schema",1).put("settings",values));SharedPreferences.Editor editor=prefs.edit();for(Iterator<String> it=values.keys();it.hasNext();){String k=it.next();PanelStore.put(editor,k,values.get(k));}editor.apply();menu();message(I18n.t("Réglages enregistrés sur cette TV"));}catch(Exception e){message(I18n.t("Vérifiez les valeurs et les couleurs #RRGGBB"));}});TvFocus.focusFirst(root);
    }
    @Override public void onBackPressed(){if(editing!=null)menu();else super.onBackPressed();}
}
