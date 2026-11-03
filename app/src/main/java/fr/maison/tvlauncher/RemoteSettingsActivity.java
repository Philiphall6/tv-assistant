package fr.maison.tvlauncher;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local, opt-in key learning. No accessibility service or background key recording. */
public final class RemoteSettingsActivity extends CardScreen {
    boolean learning;
    int captured = -1;
    final Runnable timeout = () -> { learning = false; captured = -1; message(I18n.t("Aucune touche reçue. Certaines touches sont réservées par la TV.")); };
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    void render() {
        screen(I18n.t("Télécommande"), I18n.t("Raccourcis locaux au launcher. Home, Retour, volume et alimentation restent gérés par Android."));
        button(box, I18n.t("Associer une touche à une application"), () -> {
            learning = true; captured = -1;
            message(I18n.t("Appuyez sur la touche à associer. Retour pour annuler."));
            root.removeCallbacks(timeout); root.postDelayed(timeout, 15000);
        });
        List<String> keys = new ArrayList<>();
        for (String key : CardStore.prefs(this).getAll().keySet()) if (key.startsWith(RemoteShortcuts.PREFIX)) keys.add(key);
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String number = key.substring(RemoteShortcuts.PREFIX.length());
            if (!number.matches("[0-9]{1,5}")) continue;
            String pkg = CardStore.prefs(this).getString(key, ""), label = pkg;
            try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) { }
            String title = KeyEvent.keyCodeToString(Integer.parseInt(number)) + " → " + label;
            button(box, title, () -> choices(title, new String[]{I18n.t("Modifier"), I18n.t("Supprimer")}, which -> {
                if (which == 0) chooseApp(Integer.parseInt(number));
                else { CardStore.prefs(this).edit().remove(key).apply(); render(); }
            }));
        }
        button(footer, I18n.t("Retour"), this::finish);
    }
    void chooseApp(int code) {
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo info : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0))
                if (!getPackageName().equals(info.activityInfo.packageName)) found.putIfAbsent(info.activityInfo.packageName, info);
        List<ResolveInfo> apps = new ArrayList<>(found.values());
        apps.sort((a,b) -> a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        String[] labels = new String[apps.size()];
        for (int i=0;i<apps.size();i++) labels[i] = apps.get(i).loadLabel(getPackageManager()).toString();
        choices(I18n.t("Choisir une application"), labels, index -> {
            CardStore.prefs(this).edit().putString(RemoteShortcuts.PREFIX + code, apps.get(index).activityInfo.packageName).apply();
            render(); message(I18n.t("Raccourci enregistré"));
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!learning) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) { learning=false; captured=-1; root.removeCallbacks(timeout); message(""); }
            return true;
        }
        if (!RemoteShortcuts.assignable(key)) { message(I18n.t("Cette touche reste réservée à la navigation ou au système.")); return super.dispatchKeyEvent(event); }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) captured=key;
        if (event.getAction() == KeyEvent.ACTION_UP && key == captured) {
            learning=false; captured=-1; root.removeCallbacks(timeout);
            if (!event.isCanceled()) chooseApp(key);
        }
        return true;
    }
    @Override public void onPause() { learning=false; captured=-1; if(root!=null)root.removeCallbacks(timeout); super.onPause(); }
}
