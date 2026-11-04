package fr.maison.tvlauncher;

import android.os.Bundle;
import android.widget.Button;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-app visibility and honest feed diagnostics. Never accesses an app's private storage. */
public final class PlaybackSourcesActivity extends CardScreen {
    static final String HIDDEN="hidden_resume_apps";
    WatchNextStore store;
    static boolean visible(android.content.SharedPreferences prefs,String pkg){return !prefs.getStringSet(HIDDEN,Collections.emptySet()).contains(pkg);}
    @Override public void onCreate(Bundle state){super.onCreate(state);}
    @Override protected void onResume(){super.onResume();render(Collections.emptyList(),false,true);
        store=new WatchNextStore(this,(items,available)->render(items,available,false));store.start();}
    @Override protected void onPause(){if(store!=null){store.close();store=null;}super.onPause();}
    void render(List<WatchNextStore.Item> items,boolean available,boolean loading){
        if(isFinishing())return;
        screen(I18n.t("Sources de reprise de lecture"),I18n.t("Uniquement les contenus partagés par les applications avec Android TV. Aucun accès aux comptes ou aux historiques privés."));
        Map<String,String> packages=new LinkedHashMap<>();
        packages.put("org.jellyfin.androidtv","Jellyfin");
        packages.put("com.wbd.stream","HBO / Max");packages.put("com.hbo.hbonow","HBO Max");
        packages.put("com.apple.atve.androidtv","Apple TV");packages.put("fr.maison.iptvnaptor","IPTVnaptor TV");
        for(WatchNextStore.Item item:items)packages.putIfAbsent(item.pkg,label(item.pkg));
        for(Map.Entry<String,String> app:packages.entrySet()){
            String pkg=app.getKey();boolean installed=installed(pkg);int count=0;
            for(WatchNextStore.Item item:items)if(item.pkg.equals(pkg)&&item.intent.resolveActivity(getPackageManager())!=null)count++;
            String state=!installed?I18n.t("Non installée"):loading?I18n.t("Vérification…"):!available?I18n.t("Flux inaccessible"):
                count==0?I18n.t("Aucune reprise partagée actuellement"):count+" "+I18n.t("contenu(s) partagé(s)");
            box.addView(text(app.getValue()+" · "+state,16,0xffc7d1da));
            if(installed){Button toggle=button(box,visibility(pkg),()->{});toggle.setOnClickListener(v->{
                Set<String> hidden=new HashSet<>(CardStore.prefs(this).getStringSet(HIDDEN,Collections.emptySet()));
                if(!hidden.remove(pkg))hidden.add(pkg);CardStore.prefs(this).edit().putStringSet(HIDDEN,hidden).apply();toggle.setText(visibility(pkg));
            });}
        }
        box.addView(text(I18n.t("Jellyfin : activez l’intégration de l’écran d’accueil dans ses réglages. HBO/Max et Apple TV dépendent des contenus qu’ils publient. IPTVNaptor TV nécessite une version qui publie le flux Android TV ; un simple raccourci n’est pas une reprise."),14,0xffc7d1da));
        if(!PlaybackPermission.granted(this))button(box,I18n.t("Autoriser la reprise de lecture"),()->PlaybackPermission.request(this));
        button(footer,I18n.t("Actualiser"),()->{if(store!=null)store.refresh();});
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    String visibility(String pkg){return I18n.t(visible(CardStore.prefs(this),pkg)?"Afficher dans Reprendre : oui":"Afficher dans Reprendre : non");}
    boolean installed(String pkg){try{getPackageManager().getApplicationInfo(pkg,0);return true;}catch(Exception absent){return false;}}
    String label(String pkg){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception absent){return pkg;}}
}
