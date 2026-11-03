package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import java.io.*;
import java.util.*;

/** Read-only gallery of the actual installed artwork. No preference or HA mutations. */
public final class AppIconInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        List<ResolveInfo> apps=getTargetContext().getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);
        apps.removeIf(a->a.activityInfo.packageName.equals(getTargetContext().getPackageName()));
        int rows=Math.min(6,apps.size());Bitmap output=Bitmap.createBitmap(1400,80+rows*220,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(output);canvas.drawColor(0xff384752);Paint text=new Paint(3);text.setColor(Color.WHITE);text.setTextSize(19);
        String[] labels={"Source","Ronde","Arrondie","Carrée","Cookie 4","Cookie 7","Arche"};
        for(int i=0;i<labels.length;i++)canvas.drawText(labels[i],i*200+30,35,text);
        StringBuilder info=new StringBuilder();
        for(int row=0;row<rows;row++){
            ResolveInfo app=apps.get(row);Drawable icon=app.loadIcon(getTargetContext().getPackageManager());int y=70+row*220;
            info.append(app.activityInfo.packageName).append(": ").append(icon.getClass().getSimpleName());
            if(icon instanceof AdaptiveIconDrawable){AdaptiveIconDrawable a=(AdaptiveIconDrawable)icon;info.append(" foreground=").append(a.getForeground().getClass().getSimpleName()).append(" background=").append(a.getBackground().getClass().getSimpleName());}
            info.append('\n');Rect previous=new Rect(icon.getBounds());icon.setBounds(25,y,175,y+150);icon.draw(canvas);icon.setBounds(previous);
            for(int shape=0;shape<6;shape++)new AppIconRenderer(app.loadIcon(getTargetContext().getPackageManager())).draw(canvas,300+shape*200,y+75,75,shape);
            canvas.drawText(app.loadLabel(getTargetContext().getPackageManager()).toString(),20,y+188,text);
        }
        File file=new File(getTargetContext().getExternalFilesDir(null),"icon-gallery.png");
        try(FileOutputStream stream=new FileOutputStream(file)){output.compress(Bitmap.CompressFormat.PNG,100,stream);}output.recycle();
        result.putString("stream",info+"Gallery: "+file+"\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(1,result);}}
}
