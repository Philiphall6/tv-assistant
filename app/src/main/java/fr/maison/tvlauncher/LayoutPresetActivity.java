package fr.maison.tvlauncher;

import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LayoutPresetActivity extends CardScreen {
    @Override public void onCreate(Bundle state){super.onCreate(state);menu();}
    void menu(){
        screen(I18n.t("Dispositions prêtes à l’emploi"),I18n.t("Change uniquement le placement des rangées classiques et la barre. Applications, connexions et cartes libres conservées. Vérifiez leurs chevauchements dans l’aperçu avant d’envoyer."));
        if(PanelStore.enabled(this)){
            box.addView(text(I18n.t("Cette disposition est pilotée par Home Assistant. Choisissez le modèle dans l’éditeur HA, onglet Écran, puis envoyez le profil."),17,0xffc7d1da));
        }else try{
            JSONArray presets=LayoutPresets.list(this);
            for(int i=0;i<presets.length();i++){
                JSONObject preset=presets.getJSONObject(i);
                button(box,I18n.t(preset.getString("name")),()->{
                    try{LayoutPresets.apply(this,CardStore.prefs(this),preset.getJSONObject("settings"));menu();message(I18n.t("Réglages enregistrés sur cette TV"));}
                    catch(Exception invalid){message(I18n.t("Modèle incompatible avec cette version"));}
                });box.addView(text(I18n.t(preset.getString("description")),13,0xffc7d1da));
            }
            if(CardStore.prefs(this).contains(LayoutPresets.UNDO))button(box,I18n.t("Annuler le dernier modèle"),()->{
                try{LayoutPresets.undo(this,CardStore.prefs(this));menu();}catch(Exception invalid){message(I18n.t("Modèle incompatible avec cette version"));}
            });
        }catch(Exception invalid){message(I18n.t("Modèle incompatible avec cette version"));}
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
}
