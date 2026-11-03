package fr.maison.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Small in-memory artwork cache. No HA credentials, cookies, disk history or URL logging. */
final class ContinueArtwork {
    static final int MAX_BYTES=4*1024*1024;
    final Context context;
    final Runnable changed;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newFixedThreadPool(2);
    final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}};
    final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    boolean closed;
    ContinueArtwork(Context context,Runnable changed){this.context=context.getApplicationContext();this.changed=changed;}
    Bitmap get(WatchNextStore.Item item){
        if(closed||item==null||item.artwork.isEmpty())return null;
        String key=item.pkg+"\n"+item.artwork;
        Bitmap cached=cache.get(key);if(cached!=null)return cached;
        if(!pending.contains(key)&&!failed.contains(key)&&pending.size()<8){
            pending.add(key);
            worker.execute(()->{
                Bitmap bitmap=null;
                try{bitmap=read(context,item.pkg,item.artwork);}catch(IOException|RuntimeException ignored){}
                Bitmap result=bitmap;
                main.post(()->{pending.remove(key);if(closed){if(result!=null)result.recycle();return;}if(result!=null)cache.put(key,result);else{if(failed.size()>=80)failed.clear();failed.add(key);}changed.run();});
            });
        }
        return null;
    }
    void close(){closed=true;worker.shutdownNow();cache.evictAll();pending.clear();failed.clear();}
    static Uri checked(String raw,String owner)throws IOException{
        try{
            if(raw==null||raw.isEmpty()||raw.length()>16384)throw new IOException("Invalid artwork URI");
            URI url=new URI(raw);String scheme=url.getScheme();
            if(url.getRawUserInfo()!=null||url.getRawFragment()!=null)throw new IOException("Invalid artwork URI");
            if("https".equalsIgnoreCase(scheme)){
                if(url.getHost()==null||url.getPort()==0||url.getPort()>65535)throw new IOException("Invalid artwork URI");
            }else if("content".equalsIgnoreCase(scheme)){
                if(url.getAuthority()==null||url.getAuthority().equals("fr.maison.tvlauncher"))throw new IOException("Invalid artwork URI");
            }else if(!"android.resource".equalsIgnoreCase(scheme)||!owner.equals(url.getAuthority()))throw new IOException("Invalid artwork URI");
            return Uri.parse(raw);
        }catch(java.net.URISyntaxException bad){throw new IOException("Invalid artwork URI");}
    }
    static Bitmap read(Context context,String owner,String raw)throws IOException{
        Uri uri=checked(raw,owner);
        if(!"https".equalsIgnoreCase(uri.getScheme())){
            try(InputStream in=context.getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Artwork unavailable");
                return decode(SharedPhotoAlbum.bounded(in,MAX_BYTES));
            }
        }
        URI url=URI.create(uri.toString());
        for(int redirects=0;redirects<4;redirects++){
            checked(url.toString(),owner);
            if(!"https".equalsIgnoreCase(url.getScheme()))throw new IOException("HTTPS required");
            HttpsURLConnection connection=(HttpsURLConnection)url.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setUseCaches(false);
            connection.setRequestProperty("Accept","image/*");
            try{
                int status=connection.getResponseCode();
                if(status>=300&&status<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Missing redirect");url=url.resolve(location);continue;}
                if(status!=200||connection.getContentLengthLong()>MAX_BYTES)throw new IOException("Artwork unavailable");
                try(InputStream in=connection.getInputStream()){return decode(SharedPhotoAlbum.bounded(in,MAX_BYTES));}
            }finally{connection.disconnect();}
        }
        throw new IOException("Too many redirects");
    }
    static Bitmap decode(byte[] bytes){
        if(bytes.length>MAX_BYTES)return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>30000||bounds.outHeight>30000)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(bounds.outWidth/options.inSampleSize>640||bounds.outHeight/options.inSampleSize>360)options.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    static Rect crop(int width,int height,float ratio){
        int w=width,h=height;if(width/(float)height>ratio)w=Math.max(1,Math.round(height*ratio));else h=Math.max(1,Math.round(width/ratio));
        return new Rect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);
    }
}
