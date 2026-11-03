package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full lightweight album index, bounded LRU photo cache; never downloads the whole album. */
final class PhotoAlbumStore {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static final Object DISK=new Object();
    static volatile long cacheEpoch;
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    interface Callback { void done(boolean ok,String message); }
    static SharedPreferences config(Context c){return c.getSharedPreferences(HaClient.PREFS,Context.MODE_PRIVATE);}
    static SharedPreferences state(Context c){return c.getSharedPreferences("photo_album_state",Context.MODE_PRIVATE);}
    static File directory(Context c){return new File(c.getNoBackupFilesDir(),"photo_album");}
    static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    static String hash(String input){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String hex="0123456789abcdef";for(byte b:bytes){s.append(hex.charAt((b&255)>>4));s.append(hex.charAt(b&15));}return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static boolean sameSource(Context c,String source){return source.equals(config(c).getString("photo_album_url",""));}
    static String name(SharedPhotoAlbum.Photo p){return hash(p.id+"\n"+p.revision)+".jpg";}
    static File cached(Context c,SharedPhotoAlbum.Photo p){return new File(directory(c),name(p));}
    static final class Snapshot {
        final String source;final List<SharedPhotoAlbum.Photo> photos;
        Snapshot(String s,List<SharedPhotoAlbum.Photo> p){source=s;photos=p;}
    }
    static Snapshot read(Context c,String source){
        List<SharedPhotoAlbum.Photo> photos=new ArrayList<>();
        synchronized(DISK){try(InputStream in=new AtomicFile(new File(directory(c),"album.json")).openRead()){
            JSONObject root=new JSONObject(new String(SharedPhotoAlbum.bounded(in,16*1024*1024),StandardCharsets.UTF_8));
            if(!hash(source).equals(root.optString("source")))return new Snapshot(source,photos);
            JSONArray items=root.getJSONArray("photos");
            for(int i=0;i<items.length();i++){JSONArray p=items.getJSONArray(i);SharedPhotoAlbum.imageUri(p.getString(1));photos.add(new SharedPhotoAlbum.Photo(p.getString(0),p.getString(1),p.optLong(2,0)));}
        }catch(Exception ignored){photos.clear();}}
        return new Snapshot(source,photos);
    }
    static void reply(Callback cb,boolean ok,String msg){if(cb!=null)new Handler(Looper.getMainLooper()).post(()->cb.done(ok,msg));}
    static void sync(Context original,boolean force,Callback cb){
        Context app=original.getApplicationContext();final Context c=app==null?original:app;
        String source=config(c).getString("photo_album_url","").trim();
        try{SharedPhotoAlbum.albumUri(source);}catch(IOException e){reply(cb,false,e.getMessage());return;}
        SharedPreferences status=state(c);long now=System.currentTimeMillis(),interval=clamp(config(c).getInt("photo_album_sync_minutes",15),1,1440)*60000L;
        if(!force&&hash(source).equals(status.getString("attempt_source",""))&&now>=status.getLong("attempt",0)&&now-status.getLong("attempt",0)<interval){reply(cb,true,"À jour");return;}
        if(!BUSY.compareAndSet(false,true)){reply(cb,false,"Une synchronisation est déjà en cours");return;}
        status.edit().putLong("attempt",now).putString("attempt_source",hash(source)).putString("status","Lecture de l’album et de ses pages…").apply();
        IO.execute(()->{try{
            SharedPhotoAlbum album=SharedPhotoAlbum.fetch(source);
            JSONArray entries=new JSONArray();Set<String> keep=new HashSet<>();
            for(SharedPhotoAlbum.Photo p:album.photos){entries.put(new JSONArray().put(p.id).put(p.url).put(p.revision));keep.add(name(p));}
            byte[] manifest=new JSONObject().put("source",hash(source)).put("photos",entries).toString().getBytes(StandardCharsets.UTF_8);
            if(manifest.length>16*1024*1024)throw new IOException("Index photo trop volumineux pour cet appareil");
            synchronized(DISK){
                if(!sameSource(c,source))throw new IOException("Album changé : résultat ignoré");
                File dir=directory(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cache photo inaccessible");
                AtomicFile file=new AtomicFile(new File(dir,"album.json"));FileOutputStream out=null;
                try{out=file.startWrite();out.write(manifest);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
                File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[a-f0-9]{64}\\.jpg(\\.part)?")&&!keep.contains(f.getName()))f.delete();
                prune(c,0,Collections.emptySet());
            }
            String message=album.photos.isEmpty()?"Album vide — aucune photo à afficher":album.photos.size()+" photos disponibles · chargement progressif";
            status.edit().putString("source",hash(source)).putString("title",album.title).putInt("count",album.photos.size()).putLong("updated",System.currentTimeMillis()).putString("status",message).apply();
            reply(cb,true,message);
        }catch(Exception e){String msg=error(e);if(sameSource(c,source))status.edit().putString("status",msg+" — dernière copie conservée").apply();reply(cb,false,msg);}finally{BUSY.set(false);}});
    }
    /** Decoder thread only. At most one incoming image in addition to the cache budget. */
    static Bitmap obtain(Context c,String source,SharedPhotoAlbum.Photo photo)throws IOException{
        long epoch=cacheEpoch;
        File target=cached(c,photo);
        synchronized(DISK){if(target.isFile()){Bitmap b=BitmapFactory.decodeFile(target.toString());if(b!=null){target.setLastModified(System.currentTimeMillis());return b;}target.delete();}}
        byte[] bytes=SharedPhotoAlbum.fetch(SharedPhotoAlbum.imageUri(photo.url+"=w1920-h1080"),true);
        Bitmap b=decode(bytes);if(b==null)throw new IOException("Photo Google illisible");
        synchronized(DISK){
            if(!sameSource(c,source)||epoch!=cacheEpoch){b.recycle();throw new IOException("Album changé ou cache effacé");}
            File dir=directory(c);if(!dir.isDirectory()&&!dir.mkdirs()){b.recycle();throw new IOException("Cache inaccessible");}
            File temp=new File(dir,name(photo)+".part");
            try{
                if(dir.getUsableSpace()<16*1024*1024L)throw new IOException("Stockage photo insuffisant");
                try(FileOutputStream out=new FileOutputStream(temp)){if(!b.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Copie photo impossible");}
                prune(c,temp.length(),Collections.singleton(target.getName()));
                if(!temp.renameTo(target))throw new IOException("Enregistrement de photo impossible");
            }catch(IOException e){temp.delete();b.recycle();throw e;}
        }
        return b;
    }
    static void prune(Context c,long incoming,Set<String> protect)throws IOException{
        long budget=clamp(config(c).getInt("photo_album_cache_mb",128),32,1024)*1024L*1024L;
        File[] all=directory(c).listFiles((d,n)->n.matches("[a-f0-9]{64}\\.jpg"));if(all==null)return;
        Arrays.sort(all,Comparator.comparingLong(File::lastModified));long total=incoming;for(File f:all)total+=f.length();
        for(File f:all)if(total>budget&&!protect.contains(f.getName())){long bytes=f.length();if(f.delete())total-=bytes;}
        if(total>budget)throw new IOException("Cache photo plein");
    }
    static String error(Exception e){
        if(e instanceof java.net.UnknownHostException)return "Adresse Google introuvable : vérifiez le DNS";
        if(e instanceof java.net.SocketTimeoutException)return "Google Photos ne répond pas";
        if(e instanceof javax.net.ssl.SSLException)return "Connexion sécurisée à Google impossible";
        return e instanceof IOException&&e.getMessage()!=null?e.getMessage():"Synchronisation Google Photos impossible";
    }
    static Bitmap decode(byte[] bytes){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>30000||bounds.outHeight>30000)return null;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(bounds.outWidth/opts.inSampleSize>1920||bounds.outHeight/opts.inSampleSize>1080)opts.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
    }
    static void clear(Context c,Callback cb){
        if(!BUSY.compareAndSet(false,true)){reply(cb,false,"Attendez la fin de la synchronisation");return;}
        IO.execute(()->{try{synchronized(DISK){cacheEpoch++;File[] all=directory(c).listFiles();if(all!=null)for(File f:all)if(f.getName().matches("([a-f0-9]{64}\\.jpg(\\.part)?|album\\.json(\\.bak|\\.new)?)")&&!f.delete())throw new IOException("Une copie locale ne peut pas être supprimée");state(c).edit().clear().apply();}reply(cb,true,"Copies locales supprimées ; les photos Google sont intactes");}catch(Exception e){reply(cb,false,error(e));}finally{BUSY.set(false);}});
    }
}
