package fr.maison.tvlauncher;

import android.content.Context;
import java.util.function.Predicate;

/** Capability-based detection, without device names, serial numbers or Google services. */
final class TvPlatform {
    static final String FIRE_TV = "amazon.hardware.fire_tv";

    static boolean isFireTv(Context context) {
        return isFireTv(feature -> context.getPackageManager().hasSystemFeature(feature));
    }

    static boolean isFireTv(Predicate<String> hasFeature) {
        try { return hasFeature.test(FIRE_TV); }
        catch (RuntimeException unavailable) { return false; }
    }

    static String systemSearchLabel(boolean fireTv) {
        return I18n.t(fireTv ? "Recherche du système" : "Recherche Android / Google TV");
    }

    static String fireTvNotice() {
        return I18n.t("Fire TV · support expérimental, non validé sur appareil réel. Utilisez TV Assistant depuis vos applications. Le remplacement de l’accueil et le bouton Home ne sont pas garantis ; aucun composant Amazon n’est désactivé. Alexa n’est pas intégrée. La voix, la reprise et les cartes web dépendent des services disponibles sur cet appareil.");
    }
}
