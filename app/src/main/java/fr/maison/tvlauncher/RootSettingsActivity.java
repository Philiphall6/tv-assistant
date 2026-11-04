package fr.maison.tvlauncher;

import android.os.Bundle;
import android.widget.Button;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class RootSettingsActivity extends CardScreen {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    Future<?> task;
    Button probe;
    int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);menu();}
    void menu(){
        boolean enabled=CardStore.prefs(this).getBoolean(RootAccess.ENABLED,false);
        screen(I18n.t("Accès root optionnel"),I18n.t("Pour un appareil déjà rooté uniquement. Aucun root, déverrouillage, téléchargement ni changement système automatique."));
        box.addView(text(I18n.t("Le test demande au gestionnaire root l’autorisation d’exécuter uniquement « id -u ». Aucune donnée d’application n’est lue. Le refus laisse le launcher fonctionner normalement."),15,0xffc7d1da));
        button(box,I18n.t(enabled?"Désactiver les outils root":"Activer les outils root"),()->{
            CardStore.prefs(this).edit().putBoolean(RootAccess.ENABLED,!enabled).apply();
            generation++;if(task!=null)task.cancel(true);menu();
        });
        probe=button(box,I18n.t("Tester l’accès root"),this::check);
        probe.setEnabled(enabled);
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void check(){
        final int request=++generation;
        probe.setEnabled(false);message(I18n.t("Demande au gestionnaire root…"));
        task=worker.submit(()->{
            RootAccess.Result result=RootAccess.check(CardStore.prefs(this),RootAccess::probe);
            runOnUiThread(()->{
                if(request!=generation||isFinishing()||isDestroyed()||!CardStore.prefs(this).getBoolean(RootAccess.ENABLED,false))return;
                probe.setEnabled(true);
                switch(result){
                    case GRANTED:message(I18n.t("Accès root confirmé. Aucun réglage système n’a été modifié."));break;
                    case DENIED:message(I18n.t("Accès root refusé. Le launcher reste utilisable sans root."));break;
                    case TIMEOUT:message(I18n.t("Délai dépassé. Aucune commande supplémentaire ne sera exécutée."));break;
                    default:message(I18n.t("Root non disponible. Cette fonction ne roote pas l’appareil."));
                }
            });
        });
    }
    @Override protected void onResume(){super.onResume();if(probe!=null)probe.setEnabled(CardStore.prefs(this).getBoolean(RootAccess.ENABLED,false));}
    @Override protected void onStop(){generation++;if(task!=null)task.cancel(true);super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
