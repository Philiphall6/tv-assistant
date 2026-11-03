package fr.maison.tvlauncher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Remote-friendly update notices and an inspectable, explicitly shared diagnostic. */
public final class MaintenanceActivity extends CardScreen {
    static final int SAVE_REPORT = 72;
    TextView version, privacy;
    Button automatic, checkButton;
    String pendingReport;
    boolean busy;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen(I18n.t("Mises à jour et diagnostic"), I18n.t("L’APK et l’intégration HACS se mettent à jour séparément."));
        version = text("", 17, TvFocus.PAPER); box.addView(version);
        privacy = text(I18n.t("Vérification auprès de GitHub, sans compte ni identifiant de TV. GitHub reçoit votre adresse IP comme pour toute connexion. Aucun téléchargement ou installation automatique."), 13, 0xffacb9c6);
        privacy.setPadding(0, dp(12), 0, dp(8)); box.addView(privacy);
        checkButton = button(box, I18n.t("Vérifier les mises à jour"), this::check);
        automatic = button(box, "", () -> {
            boolean enabled = AppUpdates.prefs(this).getBoolean("automatic", false);
            AppUpdates.prefs(this).edit().putBoolean("automatic", !enabled).apply(); refresh();
            if (!enabled && AppUpdates.due(true, System.currentTimeMillis(), AppUpdates.prefs(this).getLong("attempt", 0))) check();
        });
        button(box, I18n.t("Ouvrir la publication officielle"), this::openRelease);
        button(box, I18n.t("Afficher le diagnostic sans données personnelles"), this::showReport);
        button(footer, I18n.t("Retour"), this::finish);
        refresh(); TvFocus.focusFirst(root);
    }
    void refresh() {
        String label = I18n.t("Version installée : ") + AppUpdates.installed(this);
        AppUpdates.Release release = AppUpdates.cached(this);
        if (release != null) {
            label += "\n" + (AppUpdates.available(this) ? I18n.t("Mise à jour disponible : ") : I18n.t("Dernière version APK connue : ")) + release.version;
            if (!AppUpdates.available(this)) label += "\n" + I18n.t("Aucune version plus récente lors de la dernière vérification réussie.");
        } else label += "\n" + I18n.t("Aucune vérification réussie pour le moment.");
        version.setText(label);
        automatic.setText(I18n.t("Vérification automatique quotidienne : ") + I18n.t(AppUpdates.prefs(this).getBoolean("automatic", false) ? "Activée" : "Désactivée"));
        checkButton.setEnabled(!busy);
    }
    void check() {
        if (busy) return;
        busy = true; refresh(); message(I18n.t("Vérification en cours…"));
        AppUpdates.check(this, result -> {
            if (isFinishing() || isDestroyed()) return;
            busy = false; refresh(); message(AppUpdates.message(result));
        });
    }
    String releasePage() {
        AppUpdates.Release release = AppUpdates.cached(this);
        return release == null ? AppUpdates.RELEASES + "/latest" : release.page();
    }
    void openRelease() {
        String page = releasePage();
        confirm(I18n.t("Publication officielle"), I18n.t("Ouvrez cette page sur la TV ou un ordinateur pour télécharger l’APK. Aucune installation n’est lancée par ce bouton. Conservez la même signature pour mettre à jour sans perdre vos réglages.") + "\n\n" + page, () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page)).addCategory(Intent.CATEGORY_BROWSABLE)); }
            catch (Exception ignored) { message(I18n.t("Aucun navigateur disponible. Ouvrez le lien affiché sur un ordinateur.")); }
        });
    }
    void showReport() {
        String report = TechnicalDiagnostics.report(this);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle(I18n.t("Diagnostic sans données personnelles"))
            .setMessage(I18n.t("Contenu exact du rapport. Aucun journal brut, jeton, adresse, nom, profil, photo ou historique. L’état HA est le dernier observé sur l’accueil, pas un nouveau test de connexion. Rien n’est envoyé automatiquement.") + "\n\n" + report)
            .setNegativeButton(I18n.t("Retour"), null)
            .setPositiveButton(I18n.t("Partager ou enregistrer"), (d, which) -> exportChoices(report)).create();
        dialog.setOnShowListener(d -> TvFocus.dialog(dialog)); dialog.show();
    }
    void exportChoices(String report) {
        choices(I18n.t("Partager ou enregistrer"), new String[]{I18n.t("Partager le rapport"), I18n.t("Enregistrer un fichier"), I18n.t("Copier le rapport")}, selected -> {
            if (selected == 0) {
                try { startActivity(Intent.createChooser(shareIntent(report), I18n.t("Partager le rapport"))); }
                catch (Exception ignored) { message(I18n.t("Aucune application de partage disponible. Utilisez Copier ou Enregistrer.")); }
            } else if (selected == 1) {
                pendingReport = report;
                try { startActivityForResult(saveIntent(), SAVE_REPORT); }
                catch (Exception ignored) { pendingReport = null; message(I18n.t("Aucun sélecteur de fichiers disponible. Utilisez Copier ou Partager.")); }
            } else {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("TV Assistant diagnostic", report)); message(I18n.t("Diagnostic copié.")); }
            }
        });
    }
    static Intent shareIntent(String report) {
        return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report);
    }
    static Intent saveIntent() {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json").putExtra(Intent.EXTRA_TITLE, "tv-assistant-diagnostic.json");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_REPORT) return;
        String report = pendingReport; pendingReport = null;
        if (result != RESULT_OK || data == null || data.getData() == null || report == null) return;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { message(I18n.t("Enregistrement du diagnostic impossible.")); return; }
        new Thread(() -> {
            boolean saved = false;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out != null) { out.write(report.getBytes(StandardCharsets.UTF_8)); saved = true; }
            } catch (Exception ignored) { }
            boolean success = saved;
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) message(I18n.t(success ? "Diagnostic enregistré." : "Enregistrement du diagnostic impossible.")); });
        }, "save-technical-diagnostic").start();
    }
}
