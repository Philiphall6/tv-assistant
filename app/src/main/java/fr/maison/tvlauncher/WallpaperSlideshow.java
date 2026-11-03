package fr.maison.tvlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native canvas background. A small image pipeline, no WebView and no launcher refresh. */
final class WallpaperSlideshow {
    private static final ExecutorService DECODE=Executors.newSingleThreadExecutor();
    final View view;final Context context;final Paint paint=new Paint(3);final Random random=new Random();
    SharedPreferences prefs;String source="",currentId="";List<SharedPhotoAlbum.Photo> photos=Collections.emptyList();
    Bitmap current,previous,ready;String readyId="";boolean active,loading;int generation;long changed,networkRetry;
    final Runnable refresh=this::refresh,prepare=this::prepare,advance=this::advance;
    WallpaperSlideshow(View view){this.view=view;context=view.getContext().getApplicationContext();}
    void start(){
        stop();active=true;prefs=PhotoAlbumStore.config(context);String next=prefs.getString("photo_album_url","");
        if(!next.equals(source)){current=null;previous=null;currentId="";photos=Collections.emptyList();networkRetry=0;}
        source=next;readIndex();refresh();
    }
    void stop(){active=false;generation++;loading=false;ready=null;previous=null;view.removeCallbacks(refresh);view.removeCallbacks(prepare);view.removeCallbacks(advance);}
    void refresh(){if(!active)return;PhotoAlbumStore.sync(context,false,(ok,msg)->{if(active)readIndex();});view.postDelayed(refresh,60000);}
    void readIndex(){
        int run=generation;String album=source;
        DECODE.execute(()->{PhotoAlbumStore.Snapshot snapshot=PhotoAlbumStore.read(context,album);view.post(()->{
            if(!active||run!=generation||!source.equals(album))return;
            boolean same=photos.size()==snapshot.photos.size();
            if(same)for(int i=0;i<photos.size();i++)if(!photos.get(i).id.equals(snapshot.photos.get(i).id)||!photos.get(i).url.equals(snapshot.photos.get(i).url)){same=false;break;}
            if(!same){generation++;loading=false;ready=null;view.removeCallbacks(prepare);view.removeCallbacks(advance);photos=snapshot.photos;
                if(indexOf(currentId)<0){current=null;previous=null;currentId="";view.invalidate();}}
            if(!loading&&ready==null)prepare();
        });});
    }
    int indexOf(String id){for(int i=0;i<photos.size();i++)if(photos.get(i).id.equals(id))return i;return -1;}
    static int nextIndex(int count,int current,boolean shuffle,Random random){if(count<=1)return 0;if(current<0)return shuffle?random.nextInt(count):0;if(!shuffle)return(current+1)%count;int n=random.nextInt(count-1);return n>=current?n+1:n;}
    long interval(){return PhotoAlbumStore.clamp(prefs.getInt("photo_album_seconds",30),5,3600)*1000L;}
    long fade(){return Build.VERSION.SDK_INT>=26&&!ValueAnimator.areAnimatorsEnabled()?0:PhotoAlbumStore.clamp(prefs.getInt("photo_album_fade_ms",1000),0,5000);}
    void prepare(){
        if(!active||loading||photos.isEmpty()||(photos.size()==1&&current!=null))return;
        previous=null;loading=true;int run=generation;String album=source,exclude=currentId;
        List<SharedPhotoAlbum.Photo> list=photos;int choice=nextIndex(list.size(),indexOf(currentId),prefs.getBoolean("photo_album_shuffle",false),random);
        boolean network=SystemClock.uptimeMillis()>=networkRetry;
        DECODE.execute(()->{
            Bitmap bitmap=null;String id="";String problem=null;
            try{
                SharedPhotoAlbum.Photo target=list.get(choice);
                if(network||PhotoAlbumStore.cached(context,target).isFile()){bitmap=PhotoAlbumStore.obtain(context,album,target);id=target.id;}
            }catch(Exception e){problem=PhotoAlbumStore.error(e);}catch(OutOfMemoryError e){problem="Mémoire photo insuffisante";}
            if(bitmap==null)for(int n=0;n<list.size();n++){
                SharedPhotoAlbum.Photo p=list.get((choice+n)%list.size());if(p.id.equals(exclude)||!PhotoAlbumStore.cached(context,p).isFile())continue;
                try{bitmap=PhotoAlbumStore.obtain(context,album,p);id=p.id;if(bitmap!=null)break;}catch(Exception ignored){}catch(OutOfMemoryError ignored){break;}
            }
            Bitmap result=bitmap;String resultId=id,error=problem;
            view.post(()->{
                if(!active||generation!=run)return;loading=false;
                if(error!=null){networkRetry=SystemClock.uptimeMillis()+120000;PhotoAlbumStore.state(context).edit().putString("image_status",error+" · lecture du cache").apply();}
                else if(result!=null)PhotoAlbumStore.state(context).edit().remove("image_status").apply();
                if(result==null){view.postDelayed(prepare,30000);return;}
                ready=result;readyId=resultId;
                if(current==null)advance();else view.postDelayed(advance,Math.max(0,interval()-(SystemClock.uptimeMillis()-changed)));
            });
        });
    }
    void advance(){if(!active||ready==null)return;previous=current;current=ready;currentId=readyId;ready=null;changed=SystemClock.uptimeMillis();view.invalidate();view.postDelayed(prepare,fade()+50);}
    boolean draw(Canvas c,float w,float h){
        if(current==null)return false;
        paint.setShader(null);paint.setColor(0xff080b0f);paint.setAlpha(255);c.drawRect(0,0,w,h,paint);
        long duration=fade();float progress=previous==null||duration==0?1:Math.min(1,(SystemClock.uptimeMillis()-changed)/(float)duration);
        if(previous!=null&&progress<1)drawImage(c,previous,w,h,(int)(255*(1-progress)));
        drawImage(c,current,w,h,(int)(255*progress));paint.setAlpha(255);
        if(progress<1&&active)view.postInvalidateOnAnimation();else previous=null;
        return true;
    }
    void drawImage(Canvas c,Bitmap image,float w,float h,int alpha){
        paint.setColor(Color.WHITE);paint.setAlpha(alpha);int fit=prefs.getInt("wallpaper_fit",0);
        if(fit==2){c.drawBitmap(image,null,new RectF(0,0,w,h),paint);return;}
        float iw=image.getWidth(),ih=image.getHeight(),scale=fit==1?Math.min(w/iw,h/ih):Math.max(w/iw,h/ih);
        float dw=iw*scale,dh=ih*scale;
        if(fit==1){float x=(w-dw)/2,y=(h-dh)/2;c.drawBitmap(image,null,new RectF(x,y,x+dw,y+dh),paint);return;}
        float sw=w/scale,sh=h/scale,x=(iw-sw)*PhotoAlbumStore.clamp(prefs.getInt("wallpaper_focus_x",50),0,100)/100f,y=(ih-sh)*PhotoAlbumStore.clamp(prefs.getInt("wallpaper_focus_y",50),0,100)/100f;
        c.drawBitmap(image,new Rect((int)x,(int)y,(int)(x+sw),(int)(y+sh)),new RectF(0,0,w,h),paint);
    }
}
