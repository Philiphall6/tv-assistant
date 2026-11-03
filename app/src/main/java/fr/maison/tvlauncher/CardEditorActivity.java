package fr.maison.tvlauncher;

import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.util.*;

public class CardEditorActivity extends CardScreen {
    boolean deck,yamlMode,ready;int index;String original,draft;Map<String,Object> card;
    EditText yaml,name,entity,icon,content,color,data,target,service,hold;
    String type,action;boolean showState,confirmation,enabled;
    EditText[] pacEntities;boolean pacAnimated;
    @Override public void onCreate(Bundle state){super.onCreate(state);deck=getIntent().getBooleanExtra("deck",false);index=getIntent().getIntExtra("index",-1);yamlMode=deck;
        original=CardStore.local(this);try{List<Map<String,Object>> all=CardConfig.parse(original);draft=deck?original:index>=0&&index<all.size()?CardConfig.dump(all.get(index)):CardConfig.EXAMPLE;}catch(Exception e){draft=deck?original:CardConfig.EXAMPLE;}
        if(!deck&&index<0&&HeatPumpState.TYPE.equals(getIntent().getStringExtra("template")))draft=HeatPumpState.template();
        if(state!=null){draft=state.getString("draft",draft);original=state.getString("original",original);yamlMode=state.getBoolean("yaml",yamlMode);}
        build();NativeCards.init(this);
    }
    void build(){ready=false;screen(deck?"Configuration YAML du dashboard":"Créer / modifier une carte",yamlMode?"Validation avant enregistrement · aucune action exécutée par l’éditeur":"Éditeur visuel · les options avancées restent conservées lors du passage en YAML");
        if(yamlMode){yaml=field(deck?"Liste de cartes (cards:)":"Configuration de la carte",draft,true);yaml.setTypeface(Typeface.MONOSPACE);yaml.setMinLines(12);yaml.setTextSize(14);
            if(deck){button(box,"Charger la version locale précédente",()->confirm("Charger la version précédente ?","Le brouillon sera remplacé. Rien n’est enregistré avant votre validation.",()->{draft=CardStore.prefs(this).getString("custom_cards_previous",CardConfig.EMPTY);build();}));}
        }else{
            try{card=CardConfig.parse(draft).get(0);}catch(Exception e){yamlMode=true;build();message(e.getMessage());return;}
            if(HaCardSurface.isCard(card)){yamlMode=true;build();message("Vraie carte HA : modifiez son YAML depuis l’extension TV Assistant.");return;}
            type=CardConfig.str(card,"type","button");action=CardConfig.str(CardConfig.action(card,"tap_action"),"action","more-info");showState=CardConfig.bool(card,"show_state",true);enabled=CardConfig.bool(card,"enabled",true);confirmation=CardConfig.bool(CardConfig.action(card,"tap_action"),"confirmation",true);
            boolean pac=HeatPumpState.isCard(card);pacEntities=null;
            Button typeButton=button(box,"Type : "+(pac?"PAC native animée":type),()->{});if(pac)typeButton.setOnClickListener(v->message("Même logique que votre carte PAC : dégivrage, circulation, mode et fréquence. Aucun JavaScript exécuté."));else typeButton.setOnClickListener(v->choices("Type de carte",new String[]{"button — commande","entity — état d’un appareil","text — texte libre"},i->{type=new String[]{"button","entity","text"}[i];typeButton.setText("Type : "+type);}));
            name=field("Titre",CardConfig.str(card,"name",""),false);
            entity=field(pac?"Entité de priorité PAC":"Entité Home Assistant (facultatif)",CardConfig.str(card,"entity",""),false);button(box,"Rechercher une entité",this::chooseEntity);
            if(pac){
                pacEntities=new EditText[4];String[] labels={"Fréquence réelle du compresseur","Dégivrage de l’unité extérieure","Pompe de circulation","Interrupteur autorisant le chauffage"};for(int i=0;i<4;i++)pacEntities[i]=field(labels[i],CardConfig.str(card,HeatPumpState.KEYS[i],HeatPumpState.DEFAULTS[i]),false);
                pacAnimated=CardConfig.bool(card,"animate",true);Button animation=button(box,"Animer le ventilateur : "+(pacAnimated?"oui":"non"),()->{});animation.setOnClickListener(v->{pacAnimated=!pacAnimated;animation.setText("Animer le ventilateur : "+(pacAnimated?"oui":"non"));});
                box.addView(text("Dégradé bleu-vert · couleurs automatiques selon le mode · une rotation en 2 secondes si la fréquence dépasse 0 Hz. L’animation respecte aussi le réglage système.",13,0xffacb9c6));icon=null;content=null;color=null;
            }else{
                icon=field("Icône (mdi:lightbulb, mdi:sofa, mdi:weather-sunny…)",CardConfig.str(card,"icon","mdi:home"),false);
                content=field("Texte · variables : {state}, {name}, {entity}",CardConfig.str(card,"content",""),true);
                color=field("Couleur de l’icône et de l’état (#RRGGBB)",CardConfig.str(card,"color","#85DCF2"),false);
            }
            Button stateButton=button(box,"Afficher l’état : "+(showState?"oui":"non"),()->{});stateButton.setOnClickListener(v->{showState=!showState;stateButton.setText("Afficher l’état : "+(showState?"oui":"non"));});
            Button enabledButton=button(box,"Carte : "+(enabled?"visible":"masquée"),()->{});enabledButton.setOnClickListener(v->{enabled=!enabled;enabledButton.setText("Carte : "+(enabled?"visible":"masquée"));});
            Button actionButton=button(box,"Appui OK : "+action,()->{});actionButton.setOnClickListener(v->choices("Action sur OK",new String[]{"more-info","none","toggle","perform-action","navigate","url","launch-app"},i->{action=new String[]{"more-info","none","toggle","perform-action","navigate","url","launch-app"}[i];actionButton.setText("Appui OK : "+action);}));
            Map<String,Object> tap=CardConfig.action(card,"tap_action");String value=CardConfig.str(tap,"perform_action",CardConfig.str(tap,"service",CardConfig.str(tap,"navigation_path",CardConfig.str(tap,"url_path",CardConfig.str(tap,"package","")))));
            service=field("Action HA (light.turn_on) / destination native / URL / package",value,false);
            target=field("Cible (YAML) · vide = entité de la carte",tap.containsKey("target")?CardConfig.dump(tap.get("target")):"",true);
            data=field("Paramètres de l’action (YAML) · ex. brightness_pct: 50",tap.containsKey("data")?CardConfig.dump(tap.get("data")):"",true);
            Button confirmButton=button(box,"Confirmer avant d’exécuter : "+(confirmation?"oui":"non"),()->{});confirmButton.setOnClickListener(v->{confirmation=!confirmation;confirmButton.setText("Confirmer avant d’exécuter : "+(confirmation?"oui":"non"));});
            hold=field("Appui long OK (YAML) · facultatif",card.containsKey("hold_action")?CardConfig.dump(card.get("hold_action")):"",true);
            box.addView(text("Pour plusieurs commandes : ajoutez une liste actions dans le mode YAML. Les scripts HA peuvent regrouper autant d’étapes que nécessaire.",13,0xffacb9c6));
        }
        button(footer,"Retour",this::onBackPressed);
        if(!deck)button(footer,yamlMode?"Éditeur visuel":"Mode YAML",()->{try{capture();List<Map<String,Object>> parsed=CardConfig.parse(draft);if(parsed.size()!=1)throw new IllegalArgumentException("L’éditeur de carte attend exactement une carte");yamlMode=!yamlMode;build();}catch(Exception e){message(e.getMessage());}});
        button(footer,"Aperçu",this::preview);button(footer,"Enregistrer",this::save);
        ready=true;TvFocus.focusFirst(root);
    }
    String value(EditText e){return e.getText().toString().trim();}
    void capture(){if(yamlMode){draft=yaml.getText().toString();return;}Map<String,Object> c=new LinkedHashMap<>(card);c.put("type",type);c.put("name",value(name));c.put("entity",value(entity));if(pacEntities!=null){for(int i=0;i<4;i++)c.put(HeatPumpState.KEYS[i],value(pacEntities[i]));c.put("animate",pacAnimated);}else{c.put("icon",value(icon));c.put("content",content.getText().toString());c.put("color",value(color));}c.put("show_state",showState);c.put("enabled",enabled);
        Map<String,Object> tap=new LinkedHashMap<>(CardConfig.action(card,"tap_action"));for(String k:Arrays.asList("service","perform_action","navigation_path","url_path","package","target","data"))tap.remove(k);tap.put("action",action);tap.put("confirmation",confirmation);
        if(action.equals("perform-action")||action.equals("call-service"))tap.put("perform_action",value(service));if(action.equals("navigate"))tap.put("navigation_path",value(service));if(action.equals("url"))tap.put("url_path",value(service));if(action.equals("launch-app"))tap.put("package",value(service));
        if(!value(target).isEmpty())tap.put("target",CardConfig.map(CardConfig.load(value(target)),"Cible"));if(!value(data).isEmpty())tap.put("data",CardConfig.map(CardConfig.load(value(data)),"Paramètres"));
        c.put("tap_action",tap);if(value(hold).isEmpty())c.remove("hold_action");else c.put("hold_action",CardConfig.map(CardConfig.load(value(hold)),"Appui long"));draft=CardConfig.dump(c);
    }
    void save(){try{capture();List<Map<String,Object>> parsed=CardConfig.parse(draft);if(!original.equals(CardStore.local(this)))throw new IllegalArgumentException("La configuration a changé pendant l’édition. Revenez puis rouvrez la carte pour ne pas écraser ces changements.");
        if(deck)CardStore.save(this,draft);else{if(parsed.size()!=1)throw new IllegalArgumentException("Une seule carte attendue");Map<String,Object> c=parsed.get(0);if(CardConfig.str(c,"id","").isEmpty())c.put("id",UUID.randomUUID().toString());List<Map<String,Object>> all=CardConfig.parse(original);if(index>=0&&index<all.size())all.set(index,c);else all.add(c);CardStore.save(this,CardConfig.deck(all));}
        Toast.makeText(this,"Configuration enregistrée",Toast.LENGTH_SHORT).show();finish();
    }catch(Exception e){message(e.getMessage());}}
    void preview(){try{capture();List<Map<String,Object>> all=CardConfig.parse(draft);if(all.isEmpty()){message("Configuration valide : aucune carte");return;}Map<String,Object> c=all.get(0);final JSONObject[] state={null};View preview=new View(this){Paint p=new Paint(3);protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float scale=getWidth()/650f;canvas.scale(scale,scale);NativeCards.draw(canvas,p,c,state[0],12,12,626,215,false,state[0]!=null);canvas.restore();if(isShown()&&HeatPumpCard.animate(c,state[0],state[0]!=null))postInvalidateDelayed(50);}};
        LinearLayout frame=new LinearLayout(this);frame.setPadding(dp(12),dp(12),dp(12),dp(12));frame.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));AlertDialog d=new AlertDialog.Builder(this).setTitle("Aperçu — aucune action exécutée"+(deck?" (première carte)":"")).setView(frame).setPositiveButton("Retour",null).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();String id=CardConfig.str(c,"entity","");if(!id.isEmpty())HaClient.getStates(this,(ok,list,msg)->runOnUiThread(()->{if(!ok||!d.isShowing())return;if(HeatPumpState.isCard(c)){Map<String,JSONObject> states=new HashMap<>();for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null)states.put(item.optString("entity_id"),item);}state[0]=HeatPumpCard.displayState(c,states);}else{for(int i=0;i<list.length();i++)if(id.equals(list.optJSONObject(i).optString("entity_id"))){state[0]=list.optJSONObject(i);break;}}preview.invalidate();}));
    }catch(Exception e){message(e.getMessage());}}
    void chooseEntity(){message("Chargement des entités…");HaClient.getStates(this,(ok,list,msg)->runOnUiThread(()->{if(isFinishing())return;if(!ok){message(msg);return;}List<JSONObject> all=new ArrayList<>();for(int i=0;i<list.length();i++)all.add(list.optJSONObject(i));all.sort((a,b)->a.optString("entity_id").compareTo(b.optString("entity_id")));List<JSONObject> visible=new ArrayList<>();LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(18),dp(10),dp(18),dp(10));EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Rechercher nom ou identifiant");search.setPadding(dp(16),dp(10),dp(16),dp(10));TvFocus.edit(search);layout.addView(search);ListView results=new ListView(this);results.setSelector(android.R.color.transparent);layout.addView(results,new LinearLayout.LayoutParams(-1,dp(260)));ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>()){@Override public View getView(int pos,View recycled,android.view.ViewGroup parent){TextView t=(TextView)super.getView(pos,recycled,parent);TvFocus.listItem(t,TvFocus.SURFACE);t.setSingleLine(false);t.setMaxLines(2);return t;}};results.setAdapter(adapter);
        Runnable filter=()->{String q=EntitySearch.normalize(search.getText().toString());visible.clear();adapter.clear();for(JSONObject o:all){JSONObject attrs=o.optJSONObject("attributes");String label=attrs==null?o.optString("entity_id"):attrs.optString("friendly_name",o.optString("entity_id"));if(EntitySearch.matches(EntitySearch.normalize(label+" "+o.optString("entity_id")),q)){visible.add(o);adapter.add(label+" · "+o.optString("entity_id"));}}adapter.notifyDataSetChanged();};filter.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Choisir une entité").setView(layout).setNegativeButton("Retour",null).create();results.setOnItemClickListener((parent,v,pos,id)->{JSONObject o=visible.get(pos);entity.setText(o.optString("entity_id"));if(value(name).isEmpty()){JSONObject a=o.optJSONObject("attributes");name.setText(a==null?o.optString("entity_id"):a.optString("friendly_name",o.optString("entity_id")));}d.dismiss();});results.setOnKeyListener((v,key,event)->{if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){search.requestFocus();return true;}if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){d.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();return true;}return false;});d.setOnShowListener(v->{TvFocus.dialog(d);search.requestFocus();});d.show();message("");}));}
    @Override public void onBackPressed(){confirm("Quitter l’éditeur ?","Les modifications non enregistrées seront abandonnées.",this::finish);}
    @Override public void onSaveInstanceState(Bundle b){if(ready)try{capture();}catch(Exception ignored){}b.putString("draft",draft);b.putString("original",original);b.putBoolean("yaml",yamlMode);super.onSaveInstanceState(b);}
}
