package fr.maison.tvlauncher;

import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

public class CardManagerActivity extends CardScreen {
    List<Map<String,Object>> cards;SharedPreferences prefs;boolean fetching;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=CardStore.prefs(this);}
    @Override public void onResume(){super.onResume();build();}
    void build(){screen(I18n.t("Cartes & dashboard"),I18n.t("Cartes natives · éditeur visuel et YAML · états HA en direct, sans page web"));
        boolean remote=prefs.getBoolean("cards_remote_enabled",false);
        button(box,I18n.t("Édition depuis Home Assistant · plusieurs TV"),()->startActivity(new Intent(this,PanelSettingsActivity.class)));
        if(PanelStore.enabled(this))box.addView(text(I18n.t("Le profil du panneau HA est prioritaire. Vos cartes locales et le fichier HA sont conservés."),13,0xffacb9c6));
        button(box,I18n.t("Créer une carte"),()->edit(-1));
        button(box,I18n.t("Ajouter une carte PAC animée"),()->startActivity(new Intent(this,CardEditorActivity.class).putExtra("template",HeatPumpState.TYPE)));
        button(box,I18n.t("Éditer / importer tout le YAML local"),()->startActivity(new Intent(this,CardEditorActivity.class).putExtra("deck",true)));
        button(box,I18n.t("Source : ")+(remote?I18n.t("fichier HA (cache local)"):I18n.t("configuration locale")),this::source);
        button(box,I18n.t("Disposition : ")+prefs.getInt("dashboard_columns",3)+I18n.t(" colonnes"),()->choices(I18n.t("Colonnes du dashboard"),new String[]{I18n.t("2 colonnes"),I18n.t("3 colonnes"),I18n.t("4 colonnes"),I18n.t("5 colonnes")},i->{prefs.edit().putInt("dashboard_columns",i+2).apply();build();}));
        button(box,I18n.t("Au démarrage : ")+(prefs.getBoolean("start_dashboard",false)?I18n.t("dashboard Maison"):I18n.t("launcher habituel")),()->{prefs.edit().putBoolean("start_dashboard",!prefs.getBoolean("start_dashboard",false)).apply();build();});
        button(box,I18n.t("Cartes existantes : ")+(prefs.getBoolean("dashboard_include_entities",true)?I18n.t("visibles avec les cartes personnalisées"):I18n.t("masquées")),()->{prefs.edit().putBoolean("dashboard_include_entities",!prefs.getBoolean("dashboard_include_entities",true)).apply();build();});
        button(box,I18n.t("Aide : format et fichier dans HA"),this::help);
        try{cards=CardConfig.parse(CardStore.local(this));}catch(Exception e){cards=new ArrayList<>();message(e.getMessage());}
        box.addView(text(I18n.t("VOS CARTES LOCALES")+(remote?I18n.t(" — conservées, mais source HA active"):""),13,0xffacb9c6));
        for(int i=0;i<cards.size();i++){final int index=i;Map<String,Object> c=cards.get(i);button(box,(i+1)+". "+NativeCards.title(c,null)+(CardConfig.bool(c,"enabled",true)?"":I18n.t(" · masquée")),()->cardMenu(index));}
        button(footer,I18n.t("Retour"),this::finish);button(footer,I18n.t("Ouvrir Maison"),()->{startActivity(new Intent(this,MainActivity.class).putExtra("page","dashboard").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
        if(!cards.isEmpty()||remote)message(remote?I18n.t("Le fichier HA est actif. Les modifications locales n’effacent pas ce fichier."):cards.size()+I18n.t(" carte(s) locale(s). Maintenez OK sur une carte pour son action longue."));
        TvFocus.focusFirst(root);
    }
    void edit(int index){startActivity(new Intent(this,CardEditorActivity.class).putExtra("index",index));}
    void save(){CardStore.save(this,CardConfig.deck(cards));build();}
    void cardMenu(int index){Map<String,Object> c=cards.get(index);choices(NativeCards.title(c,null),new String[]{I18n.t("Modifier"),I18n.t("Dupliquer"),I18n.t("Monter"),I18n.t("Descendre"),CardConfig.bool(c,"enabled",true)?I18n.t("Masquer"):I18n.t("Afficher"),I18n.t("Supprimer")},i->{switch(i){case 0:edit(index);break;case 1:Map<String,Object> copy=CardConfig.parse(CardConfig.dump(c)).get(0);copy.put("id",UUID.randomUUID().toString());copy.put("name",NativeCards.title(c,null)+I18n.t(" (copie)"));cards.add(index+1,copy);save();break;case 2:if(index>0){Collections.swap(cards,index,index-1);save();}break;case 3:if(index+1<cards.size()){Collections.swap(cards,index,index+1);save();}break;case 4:c.put("enabled",!CardConfig.bool(c,"enabled",true));save();break;case 5:confirm(I18n.t("Supprimer la carte ?"),I18n.t("Une version précédente du YAML reste disponible dans l’éditeur."),()->{cards.remove(index);save();});break;}});}
    void source(){screen(I18n.t("Source des cartes"),I18n.t("Votre configuration locale est conservée si vous activez un fichier HA."));EditText url=field(I18n.t("Fichier sur le même serveur HA (.yaml ou .json)"),prefs.getString("cards_remote_url","/local/tv-launcher.yaml"),false);
        android.view.View sourceRoot=root;
        button(box,I18n.t("Valider et activer le fichier HA"),()->{if(fetching)return;fetching=true;String source=url.getText().toString().trim();message(I18n.t("Lecture et validation…"));CardStore.fetch(this,source,(ok,msg,raw)->runOnUiThread(()->{fetching=false;if(isFinishing()||root!=sourceRoot)return;message(msg);if(ok){prefs.edit().putString("cards_remote_url",source).putString("cards_remote_cache",raw).putBoolean("cards_remote_enabled",true).apply();message(msg+I18n.t(" · source activée. Actualisation à l’ouverture de Maison, pas de rechargement de page."));}}));});
        button(box,I18n.t("Utiliser mes cartes locales"),()->{prefs.edit().putBoolean("cards_remote_enabled",false).apply();build();});
        button(box,I18n.t("Copier le dernier fichier HA dans l’éditeur local"),()->confirm(I18n.t("Copier dans la configuration locale ?"),I18n.t("Le YAML local actuel sera remplacé et conservé dans « Version précédente ». Le fichier HA ne sera pas modifié."),()->{try{CardStore.save(this,prefs.getString("cards_remote_cache",CardConfig.EMPTY));prefs.edit().putBoolean("cards_remote_enabled",false).apply();build();}catch(Exception e){message(e.getMessage());}}));
        button(footer,I18n.t("Retour"),this::build);TvFocus.focusFirst(root);
    }
    void help(){android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle("Un dashboard natif, pas une page web").setMessage("Dans HA, créez /config/www/tv-launcher.yaml puis utilisez /local/tv-launcher.yaml ici. Ce fichier /local est public : aucun jeton ni mot de passe dedans.\n\nFormat : cards: suivi d’une liste de cartes. Types : button, entity, text. Titre name, icon mdi:…, entity, content, color '#RRGGBB'. Texte dynamique : {state}, {name}, {entity}.\n\nActions : tap_action, hold_action et liste actions (menu sur appui long). Actions HA : perform-action, perform_action: light.turn_on, target et data. Les scripts HA permettent des scénarios complexes.\n\nPas de cartes HACS, JavaScript ou Jinja exécutés dans la TV. Une URL Lovelace n’est pas un fichier de configuration.\n\nPour reprendre un fichier modifié dans HA : retournez sur Maison ou validez sa source. Les états, eux, arrivent automatiquement en direct.").setPositiveButton(I18n.t("Compris"),null).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();}
}
