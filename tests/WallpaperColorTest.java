package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Native pixels and real TV editor; never saves to actual user preferences. */
final class WallpaperColorTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void run(Instrumentation instrumentation)throws Exception{
        PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();
        memory.values.put("background_dim",0);memory.values.put("wallpaper_left_overlay",100);memory.values.put("wallpaper_bottom_overlay",100);
        Paint paint=new Paint(3);Bitmap image=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
        for(String hex:new String[]{"#000000","#202124","#102A43","#03A9F4","#3D5A50","#493548","#8C4F3B","#E8DFD0","#123456","#FFFFFF"}){
            memory.values.put("wallpaper_color",hex);
            check(WallpaperBackdrop.drawSolid(canvas,160,90,5,memory.prefs,paint),"solid mode handled");
            WallpaperBackdrop.overlays(canvas,160,90,5,memory.prefs,paint);
            for(int y=0;y<90;y+=5)for(int x=0;x<160;x+=5)check(image.getPixel(x,y)==Color.parseColor(hex),"solid fills every area without a photo gradient");
        }
        for(int mode=0;mode<5;mode++){
            image.eraseColor(Color.WHITE);check(!WallpaperBackdrop.drawSolid(canvas,160,90,mode,memory.prefs,paint),"existing wallpaper mode retained");
            check(image.getPixel(0,0)==Color.WHITE,"no solid override of photos");
            WallpaperBackdrop.overlays(canvas,160,90,mode,memory.prefs,paint);
            check(image.getPixel(0,0)!=image.getPixel(159,0),"photo gradients retained");
        }
        memory.values.put("wallpaper_color","#80A0C0");memory.values.put("background_dim",25);
        WallpaperBackdrop.drawSolid(canvas,160,90,5,memory.prefs,paint);WallpaperBackdrop.overlays(canvas,160,90,5,memory.prefs,paint);
        check(image.getPixel(1,1)==image.getPixel(159,89),"dimming remains uniform");
        check(Color.red(image.getPixel(1,1))<128,"dimming still applies");
        check(paint.getShader()==null,"no shader leaks");image.recycle();
        for(String invalid:new String[]{"", "red", "#abcd", "#12345678", "javascript:bad"})check(WallpaperBackdrop.color(invalid)==Color.parseColor(WallpaperBackdrop.DEFAULT_COLOR),"invalid local color safely falls back");

        SharedPreferences prefs=CardStore.prefs(instrumentation.getTargetContext());
        int oldMode=prefs.getInt("wallpaper_mode",0);String oldColor=prefs.getString("wallpaper_color",null);
        WallpaperColorActivity activity=(WallpaperColorActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),WallpaperColorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            instrumentation.runOnMainSync(()->{
                ((Button)find(activity.root,"Bleu HA")).performClick();
                check(find(activity.root,"Aperçu · #03A9F4")!=null,"preset shows immediate draft preview");
                EditText hex=edit(activity.root);check(hex.getText().toString().equals("#03A9F4"),"preset synchronizes hex editor");
                hex.setText("#123456");check(find(activity.root,"Aperçu · #123456")!=null,"custom hex updates preview");
                hex.setText("#invalid");((Button)find(activity.root,"Utiliser cette couleur")).performClick();
                check(!activity.isFinishing()&&hex.getError()!=null,"invalid color cannot be saved");
                ((Button)find(activity.root,"Annuler")).performClick();
            });
            instrumentation.waitForIdleSync();
            check(prefs.getInt("wallpaper_mode",0)==oldMode&&java.util.Objects.equals(prefs.getString("wallpaper_color",null),oldColor),"cancel preserves actual wallpaper and photo source");
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
    private static View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private static EditText edit(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=edit(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
}
