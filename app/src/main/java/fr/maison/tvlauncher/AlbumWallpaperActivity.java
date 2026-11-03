package fr.maison.tvlauncher;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlbumWallpaperActivity extends AppearanceSettingsActivity {
    EditText album;TextView status;Button sync;boolean foreground;final android.os.Handler handler=new android.os.Handler();
    final Runnable update=()->refreshStatus();
    @Override public void onCreate(Bundle saved){getIntent().putExtra("section",4);super.onCreate(saved);}
    @Override String description(){return I18n.t("Diaporama natif depuis un album Google Photos partagé. Lecture non officielle, sans compte Google dans le launcher.");}
    @Override void buildSection(){
        heading(I18n.t("ALBUM GOOGLE PHOTOS"));
        album=new EditText(this);album.setSingleLine(true);album.setTextSize(14);album.setTextColor(Color.WHITE);album.setHintTextColor(0xff9fa9b4);album.setHint("https://photos.app.goo.gl/…");album.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);album.setText(prefs.getString("photo_album_url",""));album.setPadding(dp(14),0,dp(14),0);album.setBackground(round(0xff19202a,14));box.addView(album,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView privacy=text(I18n.t("Toute personne ayant ce lien peut voir l’album. Aucun mot de passe Google n’est demandé. Les originaux ne sont jamais modifiés."),12,0xffabb5c0,false);box.addView(privacy);
        sync=button(I18n.t("Enregistrer l’album et synchroniser"),true);sync.setOnClickListener(v->saveAlbum());box.addView(sync);
        status=text("",13,0xffc6d1dc,false);status.setPadding(dp(6),dp(8),dp(6),dp(10));box.addView(status);
        heading(I18n.t("DIAPORAMA"));
        option(I18n.t("Changer de photo"), "photo_album_seconds",30,new int[]{5,10,30,60,120,300,900},new String[]{I18n.t("5 secondes"),I18n.t("10 secondes"),I18n.t("30 secondes"),"1 minute",I18n.t("2 minutes"),I18n.t("5 minutes"),I18n.t("15 minutes")});
        option("Transition", "photo_album_fade_ms",1000,new int[]{0,500,1000,2000,3000},new String[]{I18n.t("Sans animation"),I18n.t("Fondu 0,5 s"),I18n.t("Fondu 1 s"),I18n.t("Fondu 2 s"),I18n.t("Fondu 3 s")});
        toggle(I18n.t("Ordre aléatoire"),"photo_album_shuffle",false,I18n.t("Sinon, suit l’ordre de l’album"));
        heading(I18n.t("SYNCHRONISATION ET STOCKAGE"));
        option(I18n.t("Vérifier les nouveautés"), "photo_album_sync_minutes",15,new int[]{1,5,15,30,60,360,1440},new String[]{I18n.t("Toutes les minutes"),I18n.t("Toutes les 5 minutes"),I18n.t("Toutes les 15 minutes"),I18n.t("Toutes les 30 minutes"),I18n.t("Toutes les heures"),I18n.t("Toutes les 6 heures"),I18n.t("Tous les jours")});
        option(I18n.t("Taille maximale du cache"), "photo_album_cache_mb",128,new int[]{32,64,128,256,512,1024},new String[]{I18n.t("32 Mo"),I18n.t("64 Mo"),I18n.t("128 Mo"),I18n.t("256 Mo"),I18n.t("512 Mo"),I18n.t("1 Go")});
        box.addView(text(I18n.t("La liste complète est lue par pages. Seules les photos à afficher sont téléchargées en taille TV. Le cache remplace les copies les moins récentes. Hors ligne, seules les photos déjà chargées défilent. La synchronisation fonctionne lorsque le launcher est visible et reprend à son ouverture."),12,0xffabb5c0,false));
        heading(I18n.t("CADRAGE"));choice(I18n.t("Recadrer et remplir"),0,"wallpaper_fit");choice(I18n.t("Ajuster sans couper (portraits)"),1,"wallpaper_fit");choice(I18n.t("Étirer"),2,"wallpaper_fit");
        Button stop=button(I18n.t("Désactiver le diaporama"),false);stop.setOnClickListener(v->{prefs.edit().putInt("wallpaper_mode",0).apply();Toast.makeText(this,I18n.t("Fond Salon zen réactivé"),Toast.LENGTH_SHORT).show();finish();});box.addView(stop);
        Button clear=button(I18n.t("Effacer les copies locales…"),false);clear.setOnClickListener(v->{AlertDialog d=new AlertDialog.Builder(this).setTitle(I18n.t("Effacer le cache du diaporama ?")).setMessage(I18n.t("Le diaporama sera désactivé. Les photos de Google Photos ne seront pas supprimées.")).setNegativeButton(I18n.t("Annuler"),null).setPositiveButton(I18n.t("Effacer"),(dialog,which)->{prefs.edit().putInt("wallpaper_mode",0).apply();PhotoAlbumStore.clear(this,(ok,msg)->{Toast.makeText(this,msg,Toast.LENGTH_LONG).show();refreshStatus();});}).create();d.setOnShowListener(x->TvFocus.dialog(d));d.show();});box.addView(clear);
        box.addView(text(I18n.t("Si Google modifie ses pages partagées, la synchronisation peut s’arrêter ; les copies locales restent disponibles. Les vidéos sont affichées comme images fixes."),12,0xffabb5c0,false));
    }
    void saveAlbum(){
        String value=album.getText().toString().trim();try{SharedPhotoAlbum.albumUri(value);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        prefs.edit().putString("photo_album_url",value).putInt("wallpaper_mode",4).apply();sync.setEnabled(false);sync.setText(I18n.t("Synchronisation en cours…"));
        PhotoAlbumStore.sync(this,true,(ok,msg)->{if(isFinishing()||isDestroyed())return;sync.setEnabled(true);sync.setText(I18n.t("Enregistrer l’album et synchroniser"));Toast.makeText(this,msg,Toast.LENGTH_LONG).show();refreshStatus();});refreshStatus();
    }
    void option(String title,String key,int def,int[] values,String[] labels){
        int selected=0;for(int i=0;i<values.length;i++)if(values[i]==prefs.getInt(key,def))selected=i;
        Button b=button(title+"\n"+labels[selected],false);b.setOnClickListener(v->{int checked=0;for(int i=0;i<values.length;i++)if(values[i]==prefs.getInt(key,def))checked=i;
            AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,checked,(dialog,index)->{prefs.edit().putInt(key,values[index]).apply();b.setText(title+"\n"+labels[index]);dialog.dismiss();}).setNegativeButton(I18n.t("Retour"),null).create();d.setOnShowListener(x->TvFocus.dialog(d));d.show();});box.addView(b);
    }
    void refreshStatus(){
        handler.removeCallbacks(update);if(status==null)return;SharedPreferences s=PhotoAlbumStore.state(this);String source=prefs.getString("photo_album_url","");
        if(!PhotoAlbumStore.hash(source).equals(s.getString("attempt_source","")))status.setText(I18n.t("Aucun album synchronisé"));
        else{String message=s.getString("status",I18n.t("Non synchronisé"));if(PhotoAlbumStore.hash(source).equals(s.getString("source",""))){long date=s.getLong("updated",0);message=s.getString("title","Album")+" · "+s.getInt("count",0)+" photos\n"+message+(date==0?"":"\nDernière lecture : "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,I18n.locale()).format(new Date(date)));}String error=s.getString("image_status","");status.setText(message+(error.isEmpty()?"":"\n"+error));}
        if(foreground)handler.postDelayed(update,5000);
    }
    @Override public void onResume(){super.onResume();foreground=true;refreshStatus();}
    @Override public void onPause(){foreground=false;handler.removeCallbacks(update);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacks(update);super.onDestroy();}
}
