package fr.maison.tvlauncher;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

/** User-driven setup; never disables another launcher or grants itself permissions. */
public final class LauncherSetupActivity extends CardScreen {
    TextView homeStatus, overlayStatus, playbackStatus;
    boolean fireTv;

    static boolean offerOnFirstLaunch(SharedPreferences prefs) {
        return !prefs.getBoolean("setup_seen", false) && !prefs.contains("home_redesign_v2");
    }
    static Intent homeIntent() { return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME); }
    static String effectiveHome(Context context) {
        ResolveInfo home=context.getPackageManager().resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return home==null||home.activityInfo==null?"":home.activityInfo.packageName;
    }
    static boolean roleHeld(Context context) {
        if(Build.VERSION.SDK_INT<29)return false;
        try {
            RoleManager roles=context.getSystemService(RoleManager.class);
            return roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)&&roles.isRoleHeld(RoleManager.ROLE_HOME);
        } catch (RuntimeException unavailable) { return false; }
    }
    static String homeMessage(String own, String actual, boolean held) {
        return homeMessage(own,actual,held,false);
    }
    static String homeMessage(String own, String actual, boolean held, boolean fireTv) {
        if(own.equals(actual))return I18n.t("✓ TV Assistant est l’accueil réellement utilisé par Android.");
        if(fireTv)return I18n.t("Mode application Fire TV · ouvrez TV Assistant depuis vos applications. Le bouton Home peut revenir à l’accueil Amazon, même si le rôle Accueil est accordé.");
        if(held)return "Le rôle Accueil est accordé, mais Android ouvre encore "+actual+". Ce système impose un autre launcher : la superposition ne corrige pas cette priorité. Une configuration ADB peut être nécessaire.";
        return I18n.t("À configurer · choisissez TV Assistant, puis Toujours si Android le propose.");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        renderSetup(TvPlatform.isFireTv(this));
    }
    void renderSetup(boolean fireTv) {
        this.fireTv=fireTv;
        screen(I18n.t("Installation et autorisations"), I18n.t("Chaque autorisation reste votre choix. Aucun launcher n’est supprimé ou désactivé par l’application."));
        if(fireTv)box.addView(text(TvPlatform.fireTvNotice(),14,TvFocus.PAPER));
        homeStatus=text("",15,TvFocus.PAPER);box.addView(homeStatus);
        if(!fireTv)button(box,I18n.t("Choisir TV Assistant comme accueil"),this::requestHome);
        button(box,I18n.t("Vérifier le bouton Accueil"),()->startActivity(homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        if(!fireTv)button(box,I18n.t("Google TV reprend la main · aide ADB"),()->confirm(I18n.t("Procédure avancée, depuis un ordinateur"),"Vérifiez d’abord le paquet du launcher avec ADB. Sur les appareils où il s’agit bien de com.google.android.tvlauncher, sa désactivation réversible peut être nécessaire.\n\nDésactiver :\npm disable-user --user 0 com.google.android.tvlauncher\n\nRestaurer AVANT de retirer cette application :\npm enable --user 0 com.google.android.tvlauncher\n\nPréfixez par adb -s SERIAL shell. Conservez ADB connecté et testez Home. N’appliquez pas ces commandes à un autre paquet. Le guide INSTALLATION.md détaille les vérifications. Cette fenêtre n’exécute aucune commande.",()->{}));
        overlayStatus=text("",14,0xffb1bdc8);overlayStatus.setPadding(0,dp(16),0,dp(4));box.addView(overlayStatus);
        button(box,I18n.t("Superposition · autorisation facultative"),()->confirm(I18n.t("Superposition facultative"), I18n.t("Les cartes HA, le menu d’appui long et l’accueil fonctionnent sans cette autorisation. Elle ne remplace pas le launcher par défaut. Ouvrir les réglages Android pour l’autoriser ?"),()->openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))));
        box.addView(text(I18n.t("Aucune autorisation de stockage global, d’accessibilité ou de notifications n’est nécessaire aux fonctions actuelles. Les photos sont choisies par le sélecteur Android."),12,0xffb1bdc8));
        playbackStatus=text("",14,0xffb1bdc8);box.addView(playbackStatus);
        button(box,I18n.t("Autoriser la reprise de lecture"),()->PlaybackPermission.request(this));
        button(box,I18n.t("Configurer la connexion Home Assistant"),()->startActivity(new Intent(this,SettingsActivity.class)));
        button(footer,I18n.t("Continuer vers l’accueil"),this::complete);
        button(footer,I18n.t("Actualiser l’état"),this::refresh);
        TvFocus.focusFirst(root);
    }
    @Override public void onResume(){super.onResume();refresh();}
    void refresh(){
        if(homeStatus==null)return;
        homeStatus.setText(homeMessage(getPackageName(),effectiveHome(this),roleHeld(this),fireTv));
        overlayStatus.setText(Settings.canDrawOverlays(this)?I18n.t("✓ Superposition autorisée · facultative"):I18n.t("Superposition non autorisée · facultative, le launcher fonctionne sans elle"));
        playbackStatus.setText(!PlaybackPermission.supported(this)?I18n.t("La reprise Android TV n’est pas disponible sur cet appareil."):PlaybackPermission.granted(this)?I18n.t("Reprise de lecture autorisée"):I18n.t("Reprise de lecture : autorisation Android nécessaire"));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==PlaybackPermission.REQUEST)refresh();}
    void requestHome(){
        try {
            if(Build.VERSION.SDK_INT>=29){
                RoleManager roles=getSystemService(RoleManager.class);
                if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)&&!roles.isRoleHeld(RoleManager.ROLE_HOME)){
                    try{startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),42);return;}catch(Exception ignored){}
                }
            }
        } catch(RuntimeException unavailable) { /* Try the public settings screen below. */ }
        openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    void openSettings(Intent intent){
        try{startActivity(intent);}catch(Exception unavailable){
            try{startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));}
            catch(Exception blocked){message(fireTv?TvPlatform.fireTvNotice():I18n.t("Ce système ne propose pas cet écran. Une configuration ADB peut être nécessaire ; aucun changement n’a été effectué."));}
        }
    }
    void complete(){CardStore.prefs(this).edit().putBoolean("setup_seen",true).apply();finish();}
    @Override public void onBackPressed(){complete();}
}
