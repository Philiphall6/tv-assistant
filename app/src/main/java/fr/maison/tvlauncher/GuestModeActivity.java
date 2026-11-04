package fr.maison.tvlauncher;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Button;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GuestModeActivity extends CardScreen {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    java.util.concurrent.Future<?> task;
    Button submit;int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        boolean locked=GuestMode.active(this);
        screen(I18n.t("Mode invité"),I18n.t("Protège uniquement les réglages de TV Assistant. Les applications, Android et les actions Home Assistant restent accessibles. Le PIN reste sur cette TV, hors des sauvegardes et de HA."));
        EditText pin=field(I18n.t(locked?"Code PIN":"Nouveau PIN (4 à 8 chiffres)"),"",false);secret(pin);
        EditText repeat=locked?null:field(I18n.t("Confirmer le PIN"),"",false);if(repeat!=null)secret(repeat);
        submit=button(box,I18n.t(locked?"Quitter le mode invité":"Activer le mode invité"),()->{});
        submit.setOnClickListener(v->{String value=pin.getText().toString();
            if(!GuestMode.valid(value)||repeat!=null&&!value.equals(repeat.getText().toString())){message(I18n.t("PIN invalide ou confirmation différente."));return;}
            final int request=++generation;submit.setEnabled(false);task=worker.submit(()->{
                boolean ok=false;try{if(locked)ok=GuestMode.unlock(GuestMode.prefs(this),value,System.currentTimeMillis());else{GuestMode.configure(GuestMode.prefs(this),value);ok=true;}}catch(Exception ignored){}
                final boolean success=ok;runOnUiThread(()->{if(request!=generation||isDestroyed()||isFinishing())return;pin.setText("");if(repeat!=null)repeat.setText("");submit.setEnabled(true);
                    if(success){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));finish();}
                    else message(I18n.t("PIN incorrect ou délai de sécurité. Après 5 échecs, attendez 30 secondes."));
                });
            });
        });
        box.addView(text(I18n.t("Conservez votre PIN : aucun code maître ni récupération distante. Effacer les données Android retire aussi les réglages et la connexion HA."),14,0xffc7d1da));
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void secret(EditText field){field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});field.setSaveEnabled(false);field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(submit!=null)submit.setEnabled(true);}
    @Override protected void onPause(){generation++;if(task!=null)task.cancel(true);super.onPause();}
}
