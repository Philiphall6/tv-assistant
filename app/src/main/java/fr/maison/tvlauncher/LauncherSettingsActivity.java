package fr.maison.tvlauncher;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/** One entry point per task. Connection, appearance and system permissions stay separate. */
public final class LauncherSettingsActivity extends CardScreen {
    String section="";
    @Override public void onCreate(Bundle state) { super.onCreate(state); menu(state==null?"":state.getString("section","")); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("section",section); }
    void open(Class<?> target) { startActivity(new Intent(this,target)); }
    void layout(String scope) { startActivity(new Intent(this,LayoutSettingsActivity.class).putExtra("scope",scope)); }
    void appearance(int number) { startActivity(new Intent(this,AppearanceSettingsActivity.class).putExtra("section",number)); }
    void menu(String selected) {
        section=selected;
        screen(I18n.t(selected.isEmpty()?"Paramètres":selected),I18n.t("Un réglage, un emplacement. Vos valeurs existantes sont conservées."));
        switch (selected) {
            case "Applications et favoris":
                button(box,I18n.t("Afficher ou masquer des applications"),()->open(AppManagerActivity.class));
                button(box,I18n.t("Icônes, favoris et grille des applications"),()->layout("Applications"));
                box.addView(text(I18n.t("Les favoris se choisissent par appui long sur une application."),14,0xffc7d1da));
                break;
            case "Cartes et dashboard":
                button(box,I18n.t("Cartes personnalisées et dashboard natif"),()->open(CardManagerActivity.class));
                button(box,I18n.t("Choisir les appareils et fonctions affichés"),()->open(EntityManagerActivity.class));
                button(box,I18n.t("Disposition et présentation des cartes"),()->layout("Cartes"));
                break;
            case "Télécommande et démarrage":
                button(box,I18n.t("Télécommande"),()->open(RemoteSettingsActivity.class));
                button(box,I18n.t("Page au démarrage"),()->choices(I18n.t("Page au démarrage"),new String[]{I18n.t("Accueil"),I18n.t("Applications"),I18n.t("Maison")},index->{
                    CardStore.prefs(this).edit().putBoolean("start_apps",index==1).putBoolean("start_dashboard",index==2).apply();
                    message(I18n.t("Réglages enregistrés sur cette TV"));
                }));
                button(box,I18n.t("Autoriser la reprise de lecture"),()->PlaybackPermission.request(this));
                break;
            case "Système et maintenance":
                button(box,I18n.t("Installation et autorisations"),()->open(LauncherSetupActivity.class));
                button(box,I18n.t("Paramètres de la TV"),()->{try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { message(I18n.t("Action indisponible")); }});
                button(box,I18n.t("Mises à jour et diagnostic"),()->open(MaintenanceActivity.class));
                button(box,I18n.t("Sauvegarde et réinitialisation"),()->appearance(5));
                break;
            default:
                section="";
                button(box,I18n.t("Accueil et disposition"),()->layout("Accueil"));
                button(box,I18n.t("Barre de navigation"),()->layout("Barre"));
                button(box,I18n.t("Applications et favoris"),()->menu("Applications et favoris"));
                button(box,I18n.t("Cartes et dashboard"),()->menu("Cartes et dashboard"));
                button(box,I18n.t("Fond d’écran"),()->appearance(4));
                button(box,I18n.t("Connexion Home Assistant"),()->open(SettingsActivity.class));
                button(box,I18n.t("Notifications Home Assistant"),()->open(NotificationSettingsActivity.class));
                button(box,I18n.t("Télécommande et démarrage"),()->menu("Télécommande et démarrage"));
                button(box,I18n.t("Système et maintenance"),()->menu("Système et maintenance"));
        }
        button(footer,I18n.t(section.isEmpty()?"Retour à l’accueil":"Retour"),()->{if (section.isEmpty()) finish(); else menu("");});
        TvFocus.focusFirst(root);
    }
    @Override public void onBackPressed() { if (section.isEmpty()) super.onBackPressed(); else menu(""); }
}
