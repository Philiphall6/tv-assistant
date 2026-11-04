package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Read-only UI smoke test. No listening, external app, speech or HA request. */
public final class VoiceUiInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    static List<Button> buttons(View view){
        List<Button> found=new ArrayList<>();if(view instanceof Button)found.add((Button)view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)found.addAll(buttons(((ViewGroup)view).getChildAt(i)));
        return found;
    }
    @Override public void onStart(){
        Bundle result=new Bundle();Activity chooser=null,custom=null;
        try{
            chooser=startActivitySync(new Intent(getTargetContext(),VoiceSearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("provider",2).putExtra("manual",true));
            final Activity screen=chooser;waitForIdleSync();
            runOnMainSync(()->{
                List<Button> choices=buttons(screen.getWindow().getDecorView());
                if(choices.size()!=4||!choices.get(0).getText().toString().equals(TvPlatform.systemSearchLabel(TvPlatform.isFireTv(screen)))||!choices.get(1).getText().toString().contains("Assist")||screen.getCurrentFocus()==null)throw new AssertionError("Three visible choices and initial focus required");
            });
            Thread.sleep(500);
            File image=new File(getTargetContext().getExternalFilesDir(null),"voice-chooser.png");
            Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
            if(CardStore.prefs(getTargetContext()).getInt("voice_custom_mode",0)!=0)throw new AssertionError("This smoke test requires app mode to avoid starting transcription");
            ActivityMonitor monitor=addMonitor(VoiceSearchActivity.class.getName(),null,false);
            runOnMainSync(()->buttons(screen.getWindow().getDecorView()).get(2).performClick());
            custom=waitForMonitorWithTimeout(monitor,4000);removeMonitor(monitor);
            if(custom==null)throw new AssertionError("Custom service unreachable");
            final VoiceSearchActivity third=(VoiceSearchActivity)custom;waitForIdleSync();
            runOnMainSync(()->{if(third.prefs.getInt("voice_custom_mode",0)==0&&buttons(third.getWindow().getDecorView()).stream().noneMatch(b->b.getText().toString().equals("Ouvrir le service vocal")))throw new AssertionError("Custom app launch remains explicit");});
            result.putString("stream","PASS: three voice choices, visible focus, custom screen accessible; no voice or HA command sent. Screenshot: "+image+"\n");
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}
        finally{final Activity a=chooser,b=custom;runOnMainSync(()->{if(b!=null)b.finish();if(a!=null)a.finish();});}
        finish(result.getString("stream").startsWith("PASS")?-1:1,result);
    }
}
