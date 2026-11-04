package fr.maison.tvlauncher;

import android.os.Bundle;
import android.widget.EditText;

/** Explicit save/reset, with no modifications to the installed application. */
public final class AppNameActivity extends CardScreen {
    String pkg, original;
    EditText name;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pkg=getIntent().getStringExtra("package");
        try { original=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString(); }
        catch(Exception missing) { finish();return; }
        screen(I18n.t("Renommer l’application"),I18n.t("Nom affiché sur cette TV uniquement. L’application, ses données et les titres des films restent inchangés."));
        box.addView(text(original,18,0xffacc4cd));
        name=field(I18n.t("Nom personnalisé"),AppNames.label(CardStore.prefs(this),pkg,original),false);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(AppNames.MAX)});
        button(box,I18n.t("Rétablir le nom d’origine"),()->save(null));
        button(footer,I18n.t("Annuler"),this::finish);
        button(footer,I18n.t("Enregistrer"),()->save(name.getText().toString()));
        TvFocus.focusFirst(root);
    }
    void save(String value) {
        try { AppNames.save(CardStore.prefs(this),pkg,value);setResult(RESULT_OK);finish(); }
        catch(Exception invalid) { message(I18n.t("Saisissez un nom de 1 à 64 caractères, sans caractères de contrôle.")); }
    }
}
