package fr.maison.tvlauncher;

import android.content.*;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Isolated runtime checks, no HA credentials, no device actions and no writes to the user's album. */
public final class PhotoAlbumTest {
    static int checks;
    interface Attempt{void run()throws Exception;}
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static void rejects(Attempt task,String name)throws Exception{boolean fail=false;try{task.run();}catch(IOException e){fail=true;}check(fail,name);}
    static JSONArray page(int start,int count,String next,int total)throws Exception{
        JSONArray items=new JSONArray();for(int i=start;i<start+count;i++)items.put(new JSONArray().put("id"+i).put(new JSONArray().put("https://lh3.googleusercontent.com/pw/test"+i).put(1920).put(1080)));
        JSONArray meta=new JSONArray();for(int i=0;i<22;i++)meta.put(JSONObject.NULL);meta.put(0,"AF1Album");meta.put(1,"Test ] album");meta.put(19,"sharedKey");meta.put(21,total);
        return new JSONArray().put(JSONObject.NULL).put(items).put(next==null?JSONObject.NULL:next).put(meta);
    }
    static String html(JSONArray page){return "<script>AF_initDataCallback({key: 'ds:1', hash: '1', data:"+page+", sideChannel: {}});</script>";}
    public static void run(android.app.Instrumentation instrumentation)throws Exception{
        for(String url:new String[]{"https://photos.app.goo.gl/abcd","https://photos.google.com/share/AF1test?key=xyz"})check(SharedPhotoAlbum.albumUri(url)!=null,"valid shared URL");
        for(String url:new String[]{"http://photos.app.goo.gl/abc","https://photos.google.com.evil.test/share/abc","https://secret@photos.google.com/share/abc","https://127.0.0.1/share/abc","https://photos.google.com/albums","https://photos.google.com:444/share/abc","file:///tmp/a","https://photos.app.goo.gl/abc#fragment"})rejects(()->SharedPhotoAlbum.albumUri(url),"reject unsafe source");
        for(String url:new String[]{"https://evilgoogleusercontent.com/a","http://lh3.googleusercontent.com/a","https://lh3.googleusercontent.com.evil.test/a","https://127.0.0.1/a"})rejects(()->SharedPhotoAlbum.imageUri(url),"reject unsafe image");
        SharedPhotoAlbum first=SharedPhotoAlbum.parse(html(page(0,300,"p300",2000)));
        check(first.photos.size()==300&&first.expected==2000,"first page is not mistaken for full album");
        final int[] calls={0};SharedPhotoAlbum full=SharedPhotoAlbum.complete(first,(album,key,token)->{try{calls[0]++;check(album.equals("AF1Album")&&key.equals("sharedKey"),"pagination stays within shared album");int start=Integer.parseInt(token.substring(1));int count=Math.min(300,2000-start);return page(start,count,start+count<2000?"p"+(start+count):null,2000);}catch(Exception e){throw new IOException(e);}});
        check(full.photos.size()==2000&&calls[0]==6,"all 2000 items across seven pages");
        rejects(()->SharedPhotoAlbum.complete(first,(a,k,t)->{try{return page(300,300,"p300",2000);}catch(Exception e){throw new IOException(e);}}),"repeated continuation rejected");
        rejects(()->SharedPhotoAlbum.complete(first,(a,k,t)->{try{return page(300,1,null,2000);}catch(Exception e){throw new IOException(e);}}),"partial response not committed as full album");
        check(SharedPhotoAlbum.complete(SharedPhotoAlbum.parse(html(page(0,0,null,0))),(a,k,t)->{throw new IOException();}).photos.isEmpty(),"empty album distinct from failure");
        JSONArray batch=new JSONArray().put(new JSONArray().put("wrb.fr").put("snAcKc").put(page(0,5,null,5).toString()));
        check(SharedPhotoAlbum.items(SharedPhotoAlbum.parseBatch(")]}'\n123\n"+batch+"\n")).size()==5,"batch response framing");
        rejects(()->SharedPhotoAlbum.parse("<html>Sign in</html>"),"private or changed page rejected");
        rejects(()->SharedPhotoAlbum.bounded(new ByteArrayInputStream(new byte[40]),20),"download bound");
        for(int n=0;n<100;n++){int next=WallpaperSlideshow.nextIndex(2000,1999,true,new Random(n));check(next>=0&&next<1999,"shuffle cannot immediately repeat");}
        check(WallpaperSlideshow.nextIndex(2000,1999,false,new Random())==0,"ordered wrap");
        check(WallpaperSlideshow.nextIndex(1,0,true,new Random())==0,"single-photo stable");
        Bitmap image=Bitmap.createBitmap(3840,2160,Bitmap.Config.ARGB_8888);image.eraseColor(Color.BLUE);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle();
        Bitmap scaled=PhotoAlbumStore.decode(bytes.toByteArray());check(scaled.getWidth()<=1920&&scaled.getHeight()<=1080,"TV bitmap memory bounded");scaled.recycle();
        File scratch=new File(instrumentation.getTargetContext().getCacheDir(),"photo-test-"+System.nanoTime());check(scratch.mkdir(),"isolated test directory");
        Context fake=new ContextWrapper(null){public File getNoBackupFilesDir(){return scratch;}public SharedPreferences getSharedPreferences(String n,int mode){return new PanelRuntimeTest.Memory().prefs;}};
        File dir=PhotoAlbumStore.directory(fake);check(dir.mkdir(),"cache directory");
        long mb=1024L*1024;List<File> files=new ArrayList<>();for(int i=0;i<4;i++){File f=new File(dir,PhotoAlbumStore.hash("cache"+i)+".jpg");try(RandomAccessFile out=new RandomAccessFile(f,"rw")){out.setLength(40*mb);}f.setLastModified(100000L+i*1000);files.add(f);}
        PhotoAlbumStore.prune(fake,8*mb,Collections.emptySet());long size=0;for(File f:dir.listFiles())size+=f.length();check(size+8*mb<=128*mb,"LRU budget respected");check(!files.get(0).exists()&&files.get(3).exists(),"oldest cached file evicted first");
        SharedPhotoAlbum.Photo offline=new SharedPhotoAlbum.Photo("offline","not-a-network-url",42);
        Bitmap local=Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888);local.eraseColor(Color.GREEN);
        try(FileOutputStream out=new FileOutputStream(PhotoAlbumStore.cached(fake,offline))){local.compress(Bitmap.CompressFormat.JPEG,90,out);}local.recycle();
        Bitmap fromCache=PhotoAlbumStore.obtain(fake,"unused",offline);check(fromCache!=null&&fromCache.getWidth()==64,"offline image reads without any network URL");fromCache.recycle();
        check(PhotoAlbumStore.name(offline).equals(PhotoAlbumStore.name(new SharedPhotoAlbum.Photo("offline","rotated-url",42))),"CDN URL rotation does not discard offline cache");
        check(!PhotoAlbumStore.name(offline).equals(PhotoAlbumStore.name(new SharedPhotoAlbum.Photo("offline","rotated-url",43))),"changed image revision gets a fresh cache entry");
        for(File f:dir.listFiles())f.delete();dir.delete();scratch.delete();
        System.out.println("PASS: "+checks+" album, pagination, URL safety, image and cache checks");
    }
}
