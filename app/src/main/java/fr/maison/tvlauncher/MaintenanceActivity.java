package fr.maison.tvlauncher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Remote-friendly update notices and an inspectable, explicitly shared diagnostic. */
public final class MaintenanceActivity extends CardScreen {
    static final int SAVE_REPORT = 72, INSTALL_UPDATE = 73;
    TextView version, privacy;
    Button automatic, checkButton, downloadButton, installButton, cancelDownload;
    String pendingReport;
    boolean busy;
    boolean verifying, visible;
    ApkUpdates.Task downloadTask;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        screen(I18n.t("Mises à jour et diagnostic"), I18n.t("L’APK et l’intégration HACS se mettent à jour séparément."));
        version = text("", 17, TvFocus.PAPER); box.addView(version);
        privacy = text(I18n.t("GitHub reçoit votre adresse IP, mais aucun jeton HA ni identifiant de TV. Téléchargement sur demande uniquement. L’installation nécessite votre autorisation Android et conserve les réglages."), 13, 0xffacb9c6);
        privacy.setPadding(0, dp(12), 0, dp(8)); box.addView(privacy);
        checkButton = button(box, I18n.t("Vérifier les mises à jour"), this::check);
        downloadButton = button(box, I18n.t("Télécharger la mise à jour depuis GitHub"), this::offerDownload);
        cancelDownload = button(box, I18n.t("Annuler le téléchargement"), () -> { if (downloadTask != null) downloadTask.cancel(); });
        installButton = button(box, "", this::offerInstall);
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
        boolean working = busy || downloadTask != null || verifying;
        checkButton.setEnabled(!working);
        AppUpdates.Release ready = ApkUpdates.ready(this);
        downloadButton.setEnabled(!working && AppUpdates.available(this) && (ready == null || !ready.version.equals(release.version)));
        cancelDownload.setVisibility(downloadTask == null ? View.GONE : View.VISIBLE);
        installButton.setVisibility(ready == null ? View.GONE : View.VISIBLE);
        installButton.setEnabled(!working);
        installButton.setText(getPackageManager().canRequestPackageInstalls()
            ? I18n.t("Installer la mise à jour téléchargée") + (ready == null ? "" : " · " + ready.version)
            : I18n.t("Autoriser l’installation pour TV Assistant"));
    }
    void check() {
        if (busy || downloadTask != null || verifying) return;
        busy = true; refresh(); message(I18n.t("Vérification en cours…"));
        AppUpdates.check(this, result -> {
            if (isFinishing() || isDestroyed()) return;
            busy = false; refresh(); message(AppUpdates.message(result));
        });
    }
    @Override protected void onResume() { super.onResume(); visible = true; if (version != null) refresh(); }
    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onDestroy() { if (downloadTask != null) downloadTask.cancel(); super.onDestroy(); }
    void offerDownload() {
        if (busy || downloadTask != null || verifying || !AppUpdates.available(this)) return;
        AppUpdates.Release selected = AppUpdates.cached(this);
        confirm(I18n.t("Télécharger la mise à jour depuis GitHub"), "TV Assistant " + selected.version + "\n\n"
            + I18n.t("L’APK sera téléchargé depuis le dépôt officiel, puis son empreinte, sa version et son certificat seront contrôlés. Android vous demandera ensuite de confirmer l’installation. Aucun réglage ne sera effacé."), () -> {
                if (downloadTask != null || verifying) return;
                java.lang.ref.WeakReference<MaintenanceActivity> owner = new java.lang.ref.WeakReference<>(this);
                downloadTask = ApkUpdates.download(this, selected, new ApkUpdates.Listener() {
                    public void progress(int percent) {
                        MaintenanceActivity a = owner.get();
                        if (a != null && !a.isDestroyed()) a.message(I18n.t("Téléchargement de l’APK : ") + percent + " %");
                    }
                    public void done(ApkUpdates.Error error) {
                        MaintenanceActivity a = owner.get();
                        if (a == null || a.isDestroyed()) return;
                        a.downloadTask = null; a.refresh(); a.message(ApkUpdates.message(error));
                        if (error == ApkUpdates.Error.NONE && a.visible) a.installButton.requestFocus();
                    }
                });
                refresh(); message(I18n.t("Téléchargement de l’APK : ") + "0 %");
            });
    }
    void offerInstall() {
        if (busy || downloadTask != null || verifying || ApkUpdates.ready(this) == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            confirm(I18n.t("Autoriser l’installation pour TV Assistant"),
                I18n.t("Dans l’écran Android, autorisez uniquement TV Assistant à installer cette mise à jour. Revenez ensuite ici et choisissez Installer. Vous pouvez retirer cette autorisation après l’installation."), () -> {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                    catch (Exception ignored) { message(I18n.t("Cet écran Android est indisponible. Utilisez les paramètres système ou la publication officielle.")); }
                });
            return;
        }
        verifying = true; refresh(); message(I18n.t("Dernier contrôle de l’APK avant installation…"));
        new Thread(() -> {
            ApkUpdates.Error error = ApkUpdates.Error.NONE;
            try {
                AppUpdates.Release ready = ApkUpdates.ready(this);
                if (ready == null) throw new ApkUpdates.Failure(ApkUpdates.Error.INVALID);
                ApkUpdates.verify(this, ApkUpdates.file(this), ready, AppUpdates.prefs(this).getString("ready_sha", ""));
            } catch (ApkUpdates.Failure e) { error = e.error; }
              catch (Exception e) { error = ApkUpdates.Error.INVALID; }
            ApkUpdates.Error result = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                verifying = false;
                if (result != ApkUpdates.Error.NONE) { ApkUpdates.clear(this); refresh(); message(ApkUpdates.message(result)); return; }
                refresh();
                if (!visible) { message(ApkUpdates.message(ApkUpdates.Error.NONE)); return; }
                try { startActivityForResult(ApkUpdates.installer(this), INSTALL_UPDATE); }
                catch (Exception ignored) { message(I18n.t("Installateur Android indisponible. Utilisez la publication officielle ou réessayez.")); }
            });
        }, "verify-downloaded-apk").start();
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
        if (request == INSTALL_UPDATE) {
            message(I18n.t(result == RESULT_OK ? "Installation terminée." : "Installation annulée ou refusée par Android. Vos réglages sont conservés."));
            refresh(); return;
        }
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
