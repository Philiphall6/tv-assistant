package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import org.json.JSONObject;

/** Uses the configured TV account. No redirects, transcript logging or automatic retry. */
final class HaConversation {
    interface Callback {void done(boolean ok,String answer,String conversationId);}
    static JSONObject payload(String text,String agent,String language,String conversationId)throws Exception{
        if(text.trim().isEmpty()||text.length()>4096)throw new IllegalArgumentException("Demande vide ou trop longue (4096 caractères maximum)");
        JSONObject body=new JSONObject().put("text",text.trim()).put("language",language);
        if(!agent.trim().isEmpty())body.put("agent_id",agent.trim());
        if(!conversationId.isEmpty())body.put("conversation_id",conversationId);
        return body;
    }
    static String answer(JSONObject response){JSONObject speech=response.optJSONObject("speech"),plain=speech==null?null:speech.optJSONObject("plain");return plain==null?"Réponse reçue de Home Assistant.":plain.optString("speech","Réponse reçue de Home Assistant.");}
    static void process(Context context,String text,String conversationId,Callback callback){
        SharedPreferences prefs=CardStore.prefs(context);
        String base=prefs.getString("ha_url","").trim().replaceAll("/+$",""),token=TokenStore.get(context).trim();
        final String body;
        try{body=payload(text,prefs.getString("voice_agent_id",""),prefs.getString("voice_language","fr"),conversationId).toString();}
        catch(Exception invalid){callback.done(false,invalid.getMessage(),"");return;}
        if(base.isEmpty()||token.isEmpty()){callback.done(false,"Home Assistant n’est pas configuré","");return;}
        new Thread(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(ServerAddress.normalize(base)+"/api/conversation/process").openConnection();
                connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(6000);connection.setReadTimeout(60000);
                connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
                try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
                int code=connection.getResponseCode();
                if(code<200||code>=300){callback.done(false,"Assist indisponible (HTTP "+code+"). Vérifiez l’agent et les droits du compte TV.","");return;}
                JSONObject result;try(InputStream in=connection.getInputStream()){result=new JSONObject(new String(SharedPhotoAlbum.bounded(in,256*1024),StandardCharsets.UTF_8));}
                JSONObject response=result.getJSONObject("response");callback.done(!"error".equals(response.optString("response_type")),answer(response),result.optString("conversation_id",""));
            }catch(Exception failure){callback.done(false,"Réponse Assist non reçue. La demande a pu être exécutée : vérifiez avant de la renvoyer.","");}
            finally{if(connection!=null)connection.disconnect();}
        },"HA-TV-Assist").start();
    }
}
