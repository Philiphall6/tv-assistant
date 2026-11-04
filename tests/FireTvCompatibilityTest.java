package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.util.Map;

/** Synthetic feature checks on an isolated Android emulator, not Fire OS certification. */
final class FireTvCompatibilityTest {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static void run(Instrumentation test) {
        Context context = test.getTargetContext();
        check(context.getPackageName().endsWith(".audit"), "isolated package only");
        Map<String, ?> before = CardStore.prefs(context).getAll();
        check(TvPlatform.isFireTv(feature -> "amazon.hardware.fire_tv".equals(feature)), "official Fire TV feature");
        check(!TvPlatform.isFireTv(feature -> "android.software.leanback".equals(feature)), "Android TV is not Fire TV");
        check(!TvPlatform.isFireTv(feature -> false), "no manufacturer or model guessing");
        check(!TvPlatform.isFireTv(feature -> { throw new SecurityException("unavailable"); }), "feature lookup fails safely");
        check(TvPlatform.systemSearchLabel(true).equals(I18n.t("Recherche du système")), "Fire TV does not advertise Google search");
        check(TvPlatform.systemSearchLabel(false).equals(I18n.t("Recherche Android / Google TV")), "existing Android label preserved");
        String expected = I18n.t("Mode application Fire TV · ouvrez TV Assistant depuis vos applications. Le bouton Home peut revenir à l’accueil Amazon, même si le rôle Accueil est accordé.");
        for (boolean held : new boolean[]{false, true})
            check(LauncherSetupActivity.homeMessage("our.app", "other.home", held, true).equals(expected), "Fire TV HOME role is not a guarantee");
        check(LauncherSetupActivity.homeMessage("our.app", "our.app", false, true).equals(I18n.t("✓ TV Assistant est l’accueil réellement utilisé par Android.")), "real HOME resolver takes priority");
        check(!TvPlatform.fireTvNotice().contains("disable-user"), "no Amazon launcher disabling instructions");

        // The standard apps entry is independent from HOME and Android TV's Leanback category.
        for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.getPackageName());
            check(!context.getPackageManager().queryIntentActivities(launch, 0).isEmpty(), "apps entry reachable: " + category);
        }
        Intent home = LauncherSetupActivity.homeIntent().setPackage(context.getPackageName());
        check(context.getPackageManager().queryIntentActivities(home, 0).isEmpty(), "audit package never takes HOME");
        Context noManager = new ContextWrapper(context) {
            @Override public PackageManager getPackageManager() { throw new SecurityException("unavailable"); }
        };
        check(!TvPlatform.isFireTv(noManager), "context feature lookup failure is safe");
        check(!PlaybackPermission.supported(noManager), "missing provider access is unsupported, not a crash");
        check(!LauncherSetupActivity.roleHeld(new ContextWrapper(context) {
            @Override public Object getSystemService(String name) { return null; }
        }), "HOME role service is optional");
        LauncherSetupActivity screen = (LauncherSetupActivity) test.startActivitySync(
            new Intent(context, LauncherSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            test.runOnMainSync(() -> {
                // Exercise the Fire TV presentation without spoofing firmware or pressing any action.
                screen.renderSetup(true);
                screen.refresh();
                check(MaintenanceTest.find(screen.root, TvPlatform.fireTvNotice()), "experimental notice visible");
                check(!MaintenanceTest.find(screen.root, I18n.t("Google TV reprend la main · aide ADB")), "Google-only instructions hidden");
                check(!MaintenanceTest.find(screen.root, I18n.t("Choisir TV Assistant comme accueil")), "no unsupported HOME selection promise");
                check(MaintenanceTest.find(screen.root, I18n.t("Configurer la connexion Home Assistant")), "HA setup stays reachable");
                check(MaintenanceTest.find(screen.root, I18n.t("Continuer vers l’accueil")), "setup can be skipped without permissions");
                check(!AmbientModeTest.holdsScreen(screen.root), "Fire TV setup does not block idle sleep");
                screen.renderSetup(false);
                check(MaintenanceTest.find(screen.root, I18n.t("Choisir TV Assistant comme accueil")), "Android HOME setup preserved");
            });
        } finally { test.runOnMainSync(screen::finish); }
        check(before.equals(CardStore.prefs(context).getAll()), "capability checks do not change settings");
    }
}
