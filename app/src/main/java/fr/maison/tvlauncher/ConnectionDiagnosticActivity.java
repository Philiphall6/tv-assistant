package fr.maison.tvlauncher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Button;
import android.content.SharedPreferences;

/** Local, readable connection stages. No identifiers, address, SSID or token displayed. */
public final class ConnectionDiagnosticActivity extends CardScreen {
    final NetworkStatus network=new NetworkStatus();
    final Handler handler=new Handler(Looper.getMainLooper());
    TextView stages;boolean visible;
    final Runnable refresh=new Runnable(){public void run(){if(!visible)return;renderStatus();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        screen(I18n.t("Diagnostic des connexions"),I18n.t("Réseau, Internet, données HA et éditeur sont quatre liaisons différentes. Aucun identifiant ni jeton n’est affiché."));
        stages=text("",17,0xffdce9ef);box.addView(stages);
        Button check=button(box,I18n.t("Vérifier HA maintenant"),()->{});
        check.setOnClickListener(v->{check.setEnabled(false);message(I18n.t("Vérification…"));HaClient.test(this,(ok,ignored)->runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;check.setEnabled(true);message(I18n.t(ok?"HA répond : accès API confirmé. Cela ne confirme pas la liaison de l’éditeur.":"Test HA échoué. Vérifiez l’adresse et le jeton dans Connexion Home Assistant."));
        }));});
        box.addView(text(I18n.t("Les états HA ci-dessus sont ceux du dernier passage sur l’accueil. L’aperçu peut être hors ligne pendant les réglages, une autre application ou la veille. Internet n’est pas indispensable pour un HA local."),14,0xffacb9c6));
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void renderStatus(){
        SharedPreferences p=CardStore.prefs(this);String transport;
        switch(network.kind){case WIFI:transport="Wi-Fi";break;case ETHERNET:transport="Ethernet";break;case VPN:transport="VPN";break;case OFFLINE:transport=I18n.t("Déconnecté");break;case OTHER:transport=I18n.t("Autre réseau");break;default:transport=I18n.t("Vérification…");}
        stages.setText("1 · "+I18n.t("Réseau")+" : "+transport+"\n\n2 · Internet : "+I18n.t(network.validated?"Confirmé par Android":"Non confirmé par Android")+"\n\n3 · "+LinkStatus.haLabel(p)+"\n\n4 · "+LinkStatus.panelLabel(p)+"\n\n"+LinkStatus.hint(p));
    }
    @Override protected void onResume(){super.onResume();visible=true;network.start(this);refresh.run();}
    @Override protected void onPause(){visible=false;network.stop();handler.removeCallbacks(refresh);super.onPause();}
}
