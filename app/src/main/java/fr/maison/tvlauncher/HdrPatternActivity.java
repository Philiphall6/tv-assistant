package fr.maison.tvlauncher;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.VideoView;

/** VideoView uses SurfaceView, preserving the HDR video path; never a fake SDR canvas. */
public final class HdrPatternActivity extends Activity {
    VideoView video;
    final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(!HdrCapabilities.has(HdrCapabilities.types(this),Display.HdrCapabilities.HDR_TYPE_HDR10)){finish();return;}
        int kind=getIntent().getIntExtra("pattern",0);
        if(kind!=0&&kind!=1){finish();return;}
        int resource=getResources().getIdentifier(kind==0?"hdr_near_black":"hdr_highlights","raw",getPackageName());
        if(resource==0){finish();return;}
        video=new VideoView(this);video.setBackgroundColor(android.graphics.Color.BLACK);setContentView(video);
        video.setOnPreparedListener(player->{video.setBackgroundColor(android.graphics.Color.TRANSPARENT);player.setVolume(0,0);player.setLooping(false);video.start();});
        video.setOnCompletionListener(player->finish());
        video.setOnErrorListener((player,what,extra)->{Toast.makeText(this,I18n.t("Lecture HDR indisponible sur cet appareil."),Toast.LENGTH_LONG).show();finish();return true;});
        video.setVideoURI(Uri.parse("android.resource://"+getPackageName()+"/"+resource));
        // Safety bound includes preparation time, even if the decoder never calls back.
        handler.postDelayed(this::finish,20000);
    }
    @Override protected void onPause(){if(video!=null)video.stopPlayback();handler.removeCallbacksAndMessages(null);super.onPause();finish();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
