package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class CardStore {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(HaClient.PREFS,Context.MODE_PRIVATE);}
    static String local(Context c){return prefs(c).getString("custom_cards_yaml",CardConfig.EMPTY);}
    static List<Map<String,Object>> active(Context c){SharedPreferences p=prefs(c);if(PanelStore.enabled(c))try{List<Map<String,Object>> cards=PanelStore.cards(c);if(cards!=null)return cards;}catch(Exception ignored){}String raw=p.getBoolean("cards_remote_enabled",false)?p.getString("cards_remote_cache",CardConfig.EMPTY):local(c);try{return CardConfig.parse(raw);}catch(Exception e){return new ArrayList<>();}}
    static void save(Context c,String raw){CardConfig.parse(raw);SharedPreferences p=prefs(c);p.edit().putString("custom_cards_previous",local(c)).putString("custom_cards_yaml",raw).apply();}
    interface Result {void done(boolean ok,String message,String raw);}
    static URI sourceUri(String base,String value){
        URI ha=URI.create(base.trim().replaceAll("/+$","")+"/");URI uri=ha.resolve(value.trim());
        if(!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Adresse http(s) attendue, sans identifiants ni fragment");
        if(!Objects.equals(ha.getScheme(),uri.getScheme())||!Objects.equals(ha.getHost(),uri.getHost())||port(ha)!=port(uri))throw new IllegalArgumentException("Le fichier doit être hébergé sur votre serveur Home Assistant");
        if(!uri.getPath().matches("(?i).*\\.(yaml|yml|json)$"))throw new IllegalArgumentException("Choisissez un fichier .yaml, .yml ou .json, pas l’adresse d’une page HA");
        return uri;
    }
    static int port(URI u){return u.getPort()>=0?u.getPort():"https".equals(u.getScheme())?443:80;}
    static void fetch(Context c,String source,Result callback){
        SharedPreferences p=prefs(c);String base=p.getString("ha_url","");String token=TokenStore.get(c);
        new Thread(()->{HttpURLConnection conn=null;try{
            URI uri=sourceUri(ServerAddress.normalize(base),source);conn=(HttpURLConnection)uri.toURL().openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(6000);conn.setReadTimeout(8000);conn.setRequestProperty("Accept","application/yaml, application/json, text/plain");
            if(!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
            int status=conn.getResponseCode();if(status!=200)throw new IllegalArgumentException("Lecture impossible (HTTP "+status+"). La dernière configuration reste disponible.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>CardConfig.MAX_LENGTH)throw new IllegalArgumentException("Fichier trop volumineux (256 Ko maximum)");bytes.write(buf,0,n);}}
            String raw=bytes.toString(StandardCharsets.UTF_8.name());List<Map<String,Object>> cards=CardConfig.parse(raw);
            callback.done(true,cards.size()+" carte(s) validée(s)",raw);
        }catch(Exception e){callback.done(false,e instanceof IllegalArgumentException?e.getMessage():"Serveur inaccessible. La dernière configuration est conservée.",null);}finally{if(conn!=null)conn.disconnect();}},"HA-card-config").start();
    }
}
