package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.tv.TvContract;
import android.provider.Settings;
import android.widget.Toast;

/** TV providers may declare READ_TV_LISTINGS as a runtime permission. Never grant via ADB. */
final class PlaybackPermission {
    static final String READ="android.permission.READ_TV_LISTINGS", ASKED="playback_permission_offered";
    static final int REQUEST=704;
    static boolean granted(Context context){return context.checkSelfPermission(READ)==PackageManager.PERMISSION_GRANTED;}
    static boolean supported(Context context){
        try{
            context.getPackageManager().getPermissionInfo(READ,0);
            return context.getPackageManager().resolveContentProvider(TvContract.AUTHORITY,0)!=null;
        }catch(PackageManager.NameNotFoundException | RuntimeException missing){return false;}
    }
    static boolean offer(SharedPreferences prefs,boolean granted,boolean supported){return supported&&!granted&&prefs.getBoolean("show_continue",true)&&!prefs.getBoolean(ASKED,false);}
    static void request(Activity activity){
        if(!supported(activity)){Toast.makeText(activity,I18n.t("La reprise Android TV n’est pas disponible sur cet appareil."),Toast.LENGTH_LONG).show();return;}
        if(granted(activity)){Toast.makeText(activity,I18n.t("Reprise de lecture autorisée"),Toast.LENGTH_SHORT).show();return;}
        CardStore.prefs(activity).edit().putBoolean(ASKED,true).apply();
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle(I18n.t("Autoriser la reprise de lecture"))
            .setMessage(I18n.t("Android doit autoriser TV Assistant à lire les contenus de reprise partagés par vos applications. Cet accès est en lecture seule ; aucun historique n’est modifié. Vous pouvez refuser et continuer à utiliser le launcher."))
            .setNegativeButton(I18n.t("Plus tard"),null)
            .setPositiveButton(I18n.t("Continuer"),(d,w)->activity.requestPermissions(new String[]{READ},REQUEST))
            .setNeutralButton(I18n.t("Autorisations Android"),(d,w)->{
                try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));}catch(RuntimeException unavailable){Toast.makeText(activity,I18n.t("Application indisponible"),Toast.LENGTH_SHORT).show();}
            }).create();
        dialog.setOnShowListener(v->TvFocus.dialog(dialog));dialog.show();
    }
}
