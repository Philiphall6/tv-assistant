package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Local, allowlisted connection states. Never stores exception text or credentials. */
final class LinkStatus {
    static final String WAITING="waiting", DISABLED="disabled", SERVER_CHANGED="server_changed",
        PENDING="pending", NO_PROFILE="no_profile", SYNCED="synced", MISSING="missing",
        OWNER="owner", REFUSED="refused", TIMEOUT="timeout", INVALID="invalid", FORGOTTEN="forgotten";
    static void panel(Context c,String state){CardStore.prefs(c).edit().putString("ha_panel_link_state",state).apply();}
    static void home(Context c,boolean active){CardStore.prefs(c).edit().putBoolean("ha_link_foreground",active).apply();}
    static void ha(Context c,String state){CardStore.prefs(c).edit().putString("ha_link_state",state).putLong("ha_link_checked",System.currentTimeMillis()).apply();}
    static String panelState(SharedPreferences p){
        if(!p.getBoolean("ha_panel_enabled",false))return DISABLED;
        if(!p.getString("ha_panel_server","").equals(p.getString("ha_url","").trim()))return SERVER_CHANGED;
        return p.getString("ha_panel_link_state",WAITING);
    }
    static String summary(SharedPreferences p,boolean online){
        if(!online)return I18n.t("● Mode local");
        switch(panelState(p)){
            case SYNCED:return I18n.t("● HA + éditeur reliés");
            case DISABLED:return I18n.t("● HA · éditeur désactivé");
            case PENDING:return I18n.t("● HA · à autoriser");
            case NO_PROFILE:return I18n.t("● HA · sans profil");
            case WAITING:return I18n.t("● HA · liaison en cours");
            default:return I18n.t("● HA · éditeur non relié");
        }
    }
    static int color(SharedPreferences p,boolean online){
        if(!online)return 0xffd09b4c;
        String state=panelState(p);
        return state.equals(SYNCED)?0xff69d39e:state.equals(DISABLED)?0xff7bc8e8:0xffd09b4c;
    }
    static String haLabel(SharedPreferences p){
        switch(p.getString("ha_link_state","unknown")){
            case "connected":return I18n.t("Données HA : connexion vérifiée");
            case "auth":return I18n.t("Données HA : jeton refusé");
            case "credentials":return I18n.t("Données HA : adresse ou jeton manquant");
            case "address":return I18n.t("Données HA : adresse HTTPS invalide");
            case "network":return I18n.t("Données HA : connexion interrompue ou serveur inaccessible");
            case "connecting":return I18n.t("Données HA : connexion en cours");
            default:return I18n.t("Données HA : pas encore vérifiées depuis l’accueil");
        }
    }
    static String panelLabel(SharedPreferences p){
        switch(panelState(p)){
            case DISABLED:return I18n.t("Éditeur TV Assistant : liaison désactivée");
            case SERVER_CHANGED:return I18n.t("Éditeur TV Assistant : adresse HA modifiée");
            case PENDING:return I18n.t("Éditeur TV Assistant : autorisation en attente");
            case NO_PROFILE:return I18n.t("Éditeur TV Assistant : aucun profil reçu");
            case SYNCED:return I18n.t("Éditeur TV Assistant : profil reçu et appliqué");
            case MISSING:return I18n.t("Éditeur TV Assistant : intégration absente ou non chargée");
            case OWNER:return I18n.t("Éditeur TV Assistant : association liée à un autre compte");
            case REFUSED:return I18n.t("Éditeur TV Assistant : inscription refusée");
            case TIMEOUT:return I18n.t("Éditeur TV Assistant : aucune réponse");
            case INVALID:return I18n.t("Éditeur TV Assistant : profil incompatible");
            case FORGOTTEN:return I18n.t("Éditeur TV Assistant : connexion supprimée dans HA");
            default:return I18n.t("Éditeur TV Assistant : liaison en attente");
        }
    }
    static String hint(SharedPreferences p){
        switch(panelState(p)){
            case DISABLED:return I18n.t("Activez la liaison ci-dessous pour faire apparaître cette TV dans l’éditeur HA.");
            case SERVER_CHANGED:return I18n.t("L’adresse HA a changé. Vérifiez-la puis mettez à jour la liaison ci-dessous ; ne réassociez pas la TV si le compte n’a pas changé.");
            case PENDING:case NO_PROFILE:return I18n.t("Dans HA → TV Assistant → Téléviseurs, comparez l’identifiant ci-dessous puis affectez un profil enregistré avec votre compte administrateur.");
            case MISSING:return I18n.t("Installez TV Assistant via HACS, redémarrez HA puis ajoutez l’intégration dans Paramètres → Appareils et services. Revenez ensuite à l’accueil de la TV.");
            case OWNER:return I18n.t("Le jeton appartient à un autre compte que celui de l’association initiale. Vérifiez le compte, puis utilisez « Réassocier cette TV » si ce changement est volontaire.");
            case REFUSED:return I18n.t("Vérifiez la version de l’intégration et l’association de cette TV dans HA. Le compte TV ne doit pas devenir administrateur.");
            case INVALID:return I18n.t("Mettez l’APK à jour et vérifiez le profil dans l’éditeur HA. Le dernier affichage valide est conservé.");
            case FORGOTTEN:return I18n.t("La connexion a été supprimée dans HA. Désactivez la liaison pour rester en mode local, ou revenez à l’accueil puis autorisez à nouveau cette TV dans HA.");
            case SYNCED:return I18n.t("Les données HA et l’éditeur sont deux liaisons distinctes. Le profil est synchronisé lorsque le launcher est à l’accueil ; une autre application ou la veille peut interrompre l’aperçu.");
            default:return I18n.t("Revenez à l’accueil pendant quelques secondes puis consultez cet état. Vérifiez aussi que HA et la TV utilisent la même instance et que l’intégration est chargée.");
        }
    }
    static String errorState(String code,String message){
        if("unknown_command".equals(code))return MISSING;
        if("Identifiant de TV déjà utilisé par un autre compte".equals(message))return OWNER;
        return REFUSED;
    }
}
