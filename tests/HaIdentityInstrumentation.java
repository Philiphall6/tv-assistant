package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Identify the configured HA account, without logging/copying its token or changing any TV setting. */
public final class HaIdentityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();WebSocketClient[] client=new WebSocketClient[1];
        try{
            SharedPreferences real=CardStore.prefs(getTargetContext());String token=TokenStore.get(getTargetContext());
            URI base=URI.create(real.getString("ha_url","").trim().replaceAll("/+$","")+"/api/websocket");
            URI ws=new URI("https".equals(base.getScheme())?"wss":"ws",null,base.getHost(),base.getPort(),base.getPath(),null,null);
            CountDownLatch connected=new CountDownLatch(1);String[] account={"not verified","unknown"};
            client[0]=new WebSocketClient(ws){public void onOpen(ServerHandshake h){}public void onClose(int c,String r,boolean remote){connected.countDown();}public void onError(Exception e){connected.countDown();}public void onMessage(String raw){try{JSONObject m=new JSONObject(raw);switch(m.optString("type")){case "auth_required":send(new JSONObject().put("type","auth").put("access_token",token).toString());break;case "auth_ok":send("{\"id\":1,\"type\":\"auth/current_user\"}");break;case "result":if(m.optBoolean("success")){JSONObject u=m.getJSONObject("result");account[0]=u.optString("name");account[1]=String.valueOf(u.optBoolean("is_admin"));}connected.countDown();break;case "auth_invalid":connected.countDown();break;}}catch(Exception ignored){connected.countDown();}}};
            client[0].connect();connected.await(20,TimeUnit.SECONDS);client[0].close();
            out.putString("stream","user="+account[0]+"; admin="+account[1]+"\n");finish(-1,out);
        }catch(Throwable e){if(client[0]!=null)client[0].close();out.putString("stream","Account verification unavailable: "+e.getClass().getSimpleName()+"\n");finish(1,out);}
    }
}
