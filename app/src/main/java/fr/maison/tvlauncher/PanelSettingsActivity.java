package fr.maison.tvlauncher;

import android.content.*;
import android.os.*;
import android.widget.*;

public class PanelSettingsActivity extends CardScreen {
    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    void build(){
        SharedPreferences p=CardStore.prefs(this);
        screen(I18n.t("Édition depuis Home Assistant"),"Panneau TV Assistant · plusieurs téléviseurs · profils indépendants ou partagés");
        box.addView(text(I18n.t("État des deux liaisons"),22,0xffffffff));
        box.addView(text(LinkStatus.haLabel(p),16,0xffd5e7ef));
        box.addView(text(LinkStatus.panelLabel(p),16,0xffd5e7ef));
        box.addView(text(LinkStatus.hint(p),14,0xffacb9c6));
        long checked=p.getLong("ha_link_checked",0);
        box.addView(text(I18n.t("Dernier contrôle depuis l’accueil : ")+(checked>0?java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,I18n.locale()).format(new java.util.Date(checked)):"—"),12,0xffacb9c6));
        box.addView(text(I18n.t("Cet écran affiche le dernier état observé. L’aperçu HA peut être hors ligne pendant que les réglages sont ouverts."),12,0xffacb9c6));
        String identity=p.getString("ha_panel_device_id","");
        if(!identity.isEmpty())box.addView(text(I18n.t("Identifiant de cette TV : ")+identity,12,0xffacb9c6));
        box.addView(text("Cette liaison transmet à votre HA le nom de la TV, la liste des applications et les réglages d’affichage/cartes (dont le lien d’album éventuel). Aucun jeton ou mot de passe n’est inclus dans le profil. Un administrateur HA peut demander une capture de l’accueil visible pour l’aperçu réel ; elle n’est pas conservée dans HA. Aucune autre application n’est capturée. Les changements de configuration ne déclenchent aucune action sur la maison.",14,0xffacb9c6));
        EditText name=field(I18n.t("Nom de ce téléviseur"),p.getString("ha_panel_name",Build.MODEL),false);
        EditText profile=field(I18n.t("Profil initial (ex. salon, chambre, cuisine)"),p.getString("ha_panel_profile","salon"),false);
        button(box,p.getBoolean("ha_panel_enabled",false)?I18n.t("Mettre à jour la liaison"):I18n.t("Activer la liaison avec le panneau HA"),()->{
            try{PanelStore.enable(this,name.getText().toString(),profile.getText().toString().trim());startActivity(new Intent(this,MainActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}catch(Exception e){message(e.getMessage());}
        });
        box.addView(text("Dans HA : ouvrez « TV Assistant », importez les réglages de cette TV dans un profil, puis envoyez-le. Vous pourrez ensuite affecter ce profil à une ou plusieurs TV. L’affectation faite dans HA est prioritaire. La liaison fonctionne lorsque l’accueil du launcher est visible, sans réveiller une TV en veille.",14,0xffacb9c6));
        box.addView(text(I18n.t("Compte du dernier jeton vérifié : ")+p.getString("ha_authenticated_user",I18n.t("non vérifié"))+(p.contains("ha_authenticated_admin")?(p.getBoolean("ha_authenticated_admin",false)?I18n.t(" · administrateur"):I18n.t(" · utilisateur")):""),14,0xffacb9c6));
        button(box,"Réassocier cette TV après un changement de compte HA",()->confirm("Nouvelle association pour ce compte ?","Renseignez d’abord le jeton du nouveau compte. Une nouvelle identité TV sera créée, avec vos réglages et votre profil conservés. L’ancienne association restera hors ligne dans HA ; les autres TV ne seront pas modifiées.",()->{
            p.edit().putString("ha_panel_device_id",java.util.UUID.randomUUID().toString()).apply();
            try{PanelStore.enable(this,name.getText().toString(),profile.getText().toString().trim());startActivity(new Intent(this,MainActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}catch(Exception e){message(e.getMessage());}
        }));
        if(PanelStore.enabled(this))button(box,I18n.t("Désactiver et restaurer mes réglages locaux"),()->confirm("Revenir aux réglages locaux ?","Les réglages conservés avant l’activation seront restaurés sur cette TV. Aucun profil HA ni autre TV ne sera modifié.",()->{try{PanelStore.disable(this);build();}catch(Exception e){message(I18n.t("Restauration impossible"));}}));
        button(footer,I18n.t("Retour"),this::finish);message(LinkStatus.panelLabel(p));TvFocus.focusFirst(root);
    }
}
