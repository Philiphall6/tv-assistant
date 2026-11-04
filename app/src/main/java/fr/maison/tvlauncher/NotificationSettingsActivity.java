package fr.maison.tvlauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Local consent is never enabled remotely by a HA profile. */
public final class NotificationSettingsActivity extends CardScreen {
    @Override public void onCreate(Bundle state) { super.onCreate(state); }
    @Override public void onResume() { super.onResume(); render(); }
    void render() {
        screen(I18n.t("Notifications Home Assistant"),I18n.t("Bandeaux au-dessus des applications. Aucun réveil, aucune file d’attente et aucun rattrapage à l’allumage."));
        boolean enabled=CardStore.prefs(this).getBoolean(NotificationOverlayService.ENABLED,false);
        button(box,I18n.t(enabled?"Désactiver les notifications TV":"Activer les notifications TV"),()->{
            if (enabled) NotificationOverlayService.disable(this);
            else {
                if (!PanelStore.enabled(this) || !HaClient.isConfigured(this)) { message(I18n.t("Activez d’abord la connexion HA et la liaison de l’éditeur.")); return; }
                if (!Settings.canDrawOverlays(this)) { message(I18n.t("Autorisez d’abord la superposition ci-dessous.")); return; }
                CardStore.prefs(this).edit().putBoolean(NotificationOverlayService.ENABLED,true).apply();
                NotificationOverlayService.startFromActivity(this);
            }
            render();
        });
        button(box,I18n.t("Autoriser l’affichage au-dessus des applications"),()->{
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); }
            catch (Exception ignored) { Toast.makeText(this,I18n.t("Ouvrez les autorisations spéciales dans les paramètres Android."),Toast.LENGTH_LONG).show(); }
        });
        box.addView(text(I18n.t("En veille, écran éteint, verrouillé ou pendant l’économiseur : bandeau retiré et messages ignorés. Une application peut interdire les superpositions."),15,0xffc6d1d9));
        box.addView(text(I18n.t("Dans HA : action TV Assistant — Envoyer une notification. L’identifiant de la TV est dans l’onglet Téléviseurs."),15,0xffc6d1d9));
        button(box,I18n.t("Actualiser l’état des notifications"),()->{NotificationOverlayService.startFromActivity(this); render();});
        String state=CardStore.prefs(this).getString(NotificationOverlayService.STATUS,"");
        String label=!enabled?I18n.t("Notifications désactivées"):"connected".equals(state)?I18n.t("Réception HA reliée"):
            "refused".equals(state)?I18n.t("Mettez à jour l’intégration HA et vérifiez l’autorisation de cette TV."):
            "auth".equals(state)?I18n.t("Vérifiez le jeton Home Assistant."):
            "permission".equals(state)?I18n.t("Autorisation de superposition manquante"):
            I18n.t("Réception non reliée : vérifiez la connexion et actualisez.");
        message(label);
        button(footer,I18n.t("Retour"),this::finish); TvFocus.focusFirst(root);
    }
}
