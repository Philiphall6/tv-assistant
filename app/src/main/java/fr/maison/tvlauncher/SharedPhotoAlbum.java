package fr.maison.tvlauncher;

import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the public shared-album page. No Google login, cookies, JS execution or HA token. */
final class SharedPhotoAlbum {
    static final int MAX_HTML = 8 * 1024 * 1024, MAX_IMAGE = 12 * 1024 * 1024;
    static final class Photo {
        final String id, url; final long revision;
        Photo(String id,String url){this(id,url,0);}
        Photo(String id,String url,long revision){this.id=id;this.url=url;this.revision=revision;}
    }
    final String title;
    final List<Photo> photos;
    final String albumKey, authKey, next;
    final int expected;
    SharedPhotoAlbum(String title,List<Photo> photos,String albumKey,String authKey,String next,int expected) {
        this.title=title;this.photos=photos;this.albumKey=albumKey;this.authKey=authKey;this.next=next;this.expected=expected;
    }

    static URI albumUri(String value) throws IOException {
        try {
            URI u=new URI(value.trim()); String host=u.getHost(), path=u.getPath();
            if(!"https".equalsIgnoreCase(u.getScheme()) || u.getRawUserInfo()!=null || u.getFragment()!=null ||
                    (u.getPort()!=-1 && u.getPort()!=443) || host==null || path==null) throw new Exception();
            boolean shortLink=host.equalsIgnoreCase("photos.app.goo.gl") && path.matches("/[A-Za-z0-9_-]+/?");
            boolean shared=host.equalsIgnoreCase("photos.google.com") && path.matches("/share/[A-Za-z0-9_-]+/?");
            if(!shortLink && !shared) throw new Exception();
            return u;
        } catch(Exception e) { throw new IOException("Collez un lien d’album partagé https://photos.app.goo.gl/… ou https://photos.google.com/share/…"); }
    }
    static URI imageUri(String value) throws IOException {
        try {
            URI u=new URI(value); String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme()) || host==null ||
                    !host.toLowerCase(java.util.Locale.ROOT).matches("lh[0-9]+\\.googleusercontent\\.com") ||
                    u.getRawUserInfo()!=null || u.getFragment()!=null || (u.getPort()!=-1 && u.getPort()!=443)) throw new Exception();
            return u;
        } catch(Exception e) { throw new IOException("Adresse de photo Google non reconnue"); }
    }
    static byte[] fetch(URI uri, boolean image) throws IOException {
        for(int hop=0;hop<5;hop++) {
            uri=image?imageUri(uri.toString()):albumUri(uri.toString());
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV) HomeAssistantTV/2.6");
            c.setRequestProperty("Accept",image?"image/jpeg,image/png,image/webp":"text/html");
            try {
                int status=c.getResponseCode();
                if(status==301 || status==302 || status==303 || status==307 || status==308) {
                    String next=c.getHeaderField("Location"); if(next==null) throw new IOException("Redirection Google incomplète");
                    uri=uri.resolve(next); continue; // Each hop is checked before making another request.
                }
                if(status==403 || status==404 || status==410) throw new IOException("Album ou photo inaccessible : vérifiez que le partage par lien est encore activé");
                if(status!=200) throw new IOException("Google Photos répond HTTP "+status);
                int max=image?MAX_IMAGE:MAX_HTML;
                if(c.getContentLengthLong()>max) throw new IOException("Réponse Google trop volumineuse");
                String type=c.getContentType();
                if(image && (type==null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))) throw new IOException("La réponse reçue n’est pas une image");
                try(InputStream in=c.getInputStream()) { return bounded(in,max); }
            } finally { c.disconnect(); }
        }
        throw new IOException("Trop de redirections Google Photos");
    }
    static byte[] bounded(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[16384]; int n;
        while((n=in.read(buffer))!=-1) { if(out.size()+n>max) throw new IOException("Téléchargement trop volumineux"); out.write(buffer,0,n); }
        return out.toByteArray();
    }
    interface PageLoader { JSONArray load(String album,String key,String token) throws IOException; }
    static SharedPhotoAlbum fetch(String url) throws IOException {
        return complete(parse(new String(fetch(albumUri(url),false),StandardCharsets.UTF_8)),SharedPhotoAlbum::fetchPage);
    }
    static SharedPhotoAlbum complete(SharedPhotoAlbum first,PageLoader loader) throws IOException {
        LinkedHashMap<String,Photo> all=new LinkedHashMap<>();for(Photo p:first.photos)all.put(p.id,p);
        Set<String> tokens=new HashSet<>();String next=first.next;long deadline=System.nanoTime()+180000000000L;
        while(!next.isEmpty()) {
            if(!tokens.add(next))throw new IOException("Google répète une page de l’album ; ancienne liste conservée");
            if(tokens.size()>1000||all.size()>20000||System.nanoTime()>deadline)throw new IOException("Album trop long à synchroniser en une fois ; ancienne liste conservée");
            JSONArray data=loader.load(first.albumKey,first.authKey,next);
            for(Photo p:items(data))all.put(p.id,p);
            next=data.isNull(2)?"":data.optString(2,"");
        }
        if(first.expected>all.size())throw new IOException("Google ne fournit qu’une partie de cet album ; ancienne liste conservée");
        if(all.size()>20000)throw new IOException("L’index dépasse la limite de sécurité de 20 000 images");
        return new SharedPhotoAlbum(first.title,new ArrayList<>(all.values()),first.albumKey,first.authKey,"",first.expected);
    }
    static JSONArray fetchPage(String album,String key,String token) throws IOException {
        // Same read-only continuation request as the public shared page, not the authenticated Library API.
        if(!album.matches("[A-Za-z0-9_-]+")||!key.matches("[A-Za-z0-9_-]+"))throw new IOException("Pagination de l’album indisponible");
        HttpURLConnection c=null;
        try {
            URI endpoint=new URI("https://photos.google.com/u/0/_/PhotosUi/data/batchexecute?rpcids=snAcKc&source-path="+URLEncoder.encode("/share/"+album,"UTF-8"));
            c=(HttpURLConnection)endpoint.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
            c.setRequestProperty("Origin","https://photos.google.com");
            c.setRequestProperty("Referer","https://photos.google.com/share/"+album+"?key="+key);
            String request=new JSONArray().put(album).put(token).put(org.json.JSONObject.NULL).put(key).toString();
            JSONArray call=new JSONArray().put("snAcKc").put(request).put(org.json.JSONObject.NULL).put("generic");
            String body="f.req="+URLEncoder.encode(new JSONArray().put(new JSONArray().put(call)).toString(),"UTF-8");
            try(java.io.OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IOException("Lecture de la suite de l’album impossible (HTTP "+c.getResponseCode()+")");
            try(InputStream in=c.getInputStream()){return parseBatch(new String(bounded(in,MAX_HTML),StandardCharsets.UTF_8));}
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Pagination Google Photos illisible");}finally{if(c!=null)c.disconnect();}
    }
    static JSONArray parseBatch(String text) throws IOException {
        for(String line:text.split("\n"))if(line.trim().startsWith("[["))try {
            JSONArray rows=new JSONArray(line.trim());
            for(int i=0;i<rows.length();i++){JSONArray r=rows.optJSONArray(i);if(r!=null&&"wrb.fr".equals(r.optString(0))&&"snAcKc".equals(r.optString(1)))return new JSONArray(r.getString(2));}
        }catch(Exception ignored){}
        throw new IOException("Google a modifié la pagination ; ancienne liste conservée");
    }
    static List<Photo> items(JSONArray data) throws IOException {
        JSONArray items=data.optJSONArray(1);List<Photo> photos=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(items==null && !data.isNull(1))throw new IOException("Liste des photos Google illisible");
        if(items!=null)for(int i=0;i<items.length();i++) {
            JSONArray item=items.optJSONArray(i),media=item==null?null:item.optJSONArray(1);
            if(media==null||item.optString(0).isEmpty())throw new IOException("Le format de cet album Google Photos a changé");
            String image=media.optString(0);imageUri(image);
            if(media.optInt(1)<=0||media.optInt(2)<=0)throw new IOException("Dimensions de photo Google invalides");
            if(seen.add(item.optString(0)))photos.add(new Photo(item.optString(0),image,item.optLong(2,0)));
        }
        return photos;
    }
    static SharedPhotoAlbum parse(String html) throws IOException {
        // Read the structured album payload, NOT every image URL (which would include avatars).
        Matcher starts=Pattern.compile("AF_initDataCallback\\(\\{key:\\s*'[^']+'[^<]*?data:\\s*(\\[)").matcher(html);
        while(starts.find()) {
            try {
                int from=starts.start(1), to=arrayEnd(html,from);
                JSONArray data=new JSONArray(html.substring(from,to));
                JSONArray meta=data.optJSONArray(3);
                if(meta==null || !meta.optString(0).startsWith("AF1")) continue;
                return new SharedPhotoAlbum(meta.optString(1,"Album Google Photos"),items(data),meta.optString(0),meta.optString(19),data.isNull(2)?"":data.optString(2,""),meta.optInt(21,-1));
            } catch(IOException e) { throw e; }
            catch(Exception ignored) { /* Other page datasets are not albums. */ }
        }
        throw new IOException("Album illisible ou privé. Vérifiez le lien partagé. Google a peut-être modifié son format.");
    }
    static int arrayEnd(String text,int start) throws IOException {
        int depth=0; boolean quoted=false,escaped=false;
        for(int i=start;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='"') quoted=false; }
            else if(c=='"') quoted=true;
            else if(c=='[') depth++;
            else if(c==']' && --depth==0) return i+1;
        }
        throw new IOException("Données Google Photos incomplètes");
    }
}
