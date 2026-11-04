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
        if(scope.equals("Accueil"))button(box,I18n.t("Dispositions prêtes à l’emploi"),()->startActivity(new android.content.Intent(this,LayoutPresetActivity.class)));
        if(PanelStore.enabled(this))box.addView(text(I18n.t("Le profil HA est prioritaire lors de la synchronisation. Pour conserver vos changements, utilisez aussi l’onglet Disposition ou Apparence dans HA."),14,0xffc7d1da));
        Set<String> sections=new LinkedHashSet<>();JSONObject rules=PanelStore.schema(this);
        for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();JSONObject r=rules.optJSONObject(key);if(scope.equals(SettingsCatalog.category(key)))sections.add(SettingsCatalog.group(key,r.optString("section")));}
        for(String section:sections)button(box,I18n.t(section),()->edit(section));
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void edit(String section){
        editing=section;
        final boolean icons=section.equals("Icônes des applications");
        screen(I18n.t(section),I18n.t(icons?"100 % = carré ; 160 % = rectangle. Le logo garde ses proportions.":"Positions en % de la zone libre. Évitez les chevauchements ; la barre reste toujours accessible avec Retour."));
        if(scope.equals("Barre")&&section.equals("Informations"))box.addView(text(I18n.t("Réseau et HA sont indépendants. Icône orange : Internet non confirmé par Android. Débit ↓ / ↑ : trafic total de la TV, réseau local compris, pas la vitesse maximale de la ligne. Aucun test ni téléchargement ; affichage uniquement sur l’accueil."),14,0xffc7d1da));
        SharedPreferences prefs=CardStore.prefs(this);JSONObject rules=PanelStore.schema(this);Map<String,EditText> fields=new LinkedHashMap<>();Map<String,Object> choices=new LinkedHashMap<>();
        android.widget.ImageView preview=icons?new android.widget.ImageView(this):null;
        if(preview!=null){preview.setTag("icon-shape-preview");preview.setContentDescription(I18n.t("Forme des icônes"));android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(dp(80),dp(80));lp.gravity=android.view.Gravity.CENTER_HORIZONTAL;box.addView(preview,lp);}
        Runnable refresh=()->{if(preview==null)return;int shape=((Number)choices.getOrDefault("app_icon_shape",0)).intValue();
            preview.setImageDrawable(AppIconShape.preview(shape,0xff66b5cc,iconNumber(fields,"app_icon_ratio",160),iconNumber(fields,"app_icon_corners",18)));
            for(Map.Entry<String,EditText> field:fields.entrySet())field.getValue().setEnabled(shape==AppIconRenderer.CUSTOM);
        };
        try{
            for(Iterator<String> it=rules.keys();it.hasNext();){String key=it.next();JSONObject r=rules.getJSONObject(key);if(!scope.equals(SettingsCatalog.category(key))||!section.equals(SettingsCatalog.group(key,r.optString("section"))))continue;
                Object value=prefs.getAll().get(key);if(value==null)value=r.get("default");JSONArray options=r.optJSONArray("options");
                if(r.getString("type").equals("boolean")){choices.put(key,value);android.widget.Button toggle=button(box,I18n.t(r.optString("label"))+" : "+(Boolean.TRUE.equals(value)?I18n.t("Oui"):I18n.t("Non")),()->{});toggle.setOnClickListener(v->{boolean enabled=!Boolean.TRUE.equals(choices.get(key));choices.put(key,enabled);toggle.setText(I18n.t(r.optString("label"))+" : "+(enabled?I18n.t("Oui"):I18n.t("Non")));});}
                else if(options!=null){final Object initial=value;String[] labels=new String[options.length()];for(int i=0;i<labels.length;i++)labels[i]=I18n.t(options.getJSONArray(i).getString(1));choices.put(key,initial);android.widget.Button choice=button(box,I18n.t(r.getString("label"))+" : "+labels[((Number)value).intValue()],()->{});choice.setTag(key);choice.setOnClickListener(v->choices(I18n.t(r.optString("label")),labels,index->{try{choices.put(key,options.getJSONArray(index).get(0));choice.setText(I18n.t(r.optString("label"))+" : "+labels[index]);refresh.run();}catch(Exception ignored){}}));}
                else{EditText input=field(I18n.t(r.getString("label"))+(r.getString("type").equals("integer")?" · "+r.getInt("min")+"–"+r.getInt("max") :r.optString("format").equals("color")?" · #RRGGBB":""),String.valueOf(value),r.optString("format").equals("entities")||r.optString("format").equals("packages"));if(r.getString("type").equals("integer"))input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);fields.put(key,input);input.setTag(key);if(icons)input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});}
            }
        }catch(Exception e){message(I18n.t("Lecture des réglages impossible"));}
        refresh.run();
        button(footer,I18n.t("Retour"),this::menu);
        button(footer,I18n.t("Enregistrer"),()->{try{JSONObject values=new JSONObject(choices);for(Map.Entry<String,EditText> field:fields.entrySet()){String raw=field.getValue().getText().toString().trim();values.put(field.getKey(),rules.getJSONObject(field.getKey()).getString("type").equals("integer")?Integer.valueOf(raw):raw);}PanelStore.validate(this,new JSONObject().put("schema",1).put("settings",values));SharedPreferences.Editor editor=prefs.edit();for(Iterator<String> it=values.keys();it.hasNext();){String k=it.next();PanelStore.put(editor,k,values.get(k));}editor.apply();menu();message(I18n.t("Réglages enregistrés sur cette TV"));}catch(Exception e){message(I18n.t("Vérifiez les valeurs et les couleurs #RRGGBB"));}});TvFocus.focusFirst(root);
    }
    static int iconNumber(Map<String,EditText> fields,String key,int fallback){try{return Integer.parseInt(fields.get(key).getText().toString());}catch(Exception ignored){return fallback;}}
    @Override public void onBackPressed(){if(editing!=null)menu();else super.onBackPressed();}
}
