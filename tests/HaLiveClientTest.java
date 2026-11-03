package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.json.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Loopback fake HA only; real credentials/settings/devices are never used. */
final class HaLiveClientTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static void run(Instrumentation test)throws Exception {
        for(boolean panel:new boolean[]{false,true}) {
            PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();
            CountDownLatch started=new CountDownLatch(1),done=new CountDownLatch(1),updated=new CountDownLatch(1);
            AtomicReference<Throwable> error=new AtomicReference<>();List<Integer> ids=new CopyOnWriteArrayList<>();
            WebSocketServer server=new WebSocketServer(new InetSocketAddress("127.0.0.1",0)) {
                int last;
                public void onStart(){started.countDown();}
                public void onOpen(WebSocket c,ClientHandshake h){c.send("{\"type\":\"auth_required\"}");}
                public void onClose(WebSocket c,int code,String reason,boolean remote){}
                public void onError(WebSocket c,Exception e){error.set(e);done.countDown();}
                public void onMessage(WebSocket c,String raw){try {
                    JSONObject m=new JSONObject(raw);String type=m.getString("type");
                    if(type.equals("auth")){check(m.getString("access_token").equals("fake-loopback-token"),"fake credential only");c.send("{\"type\":\"auth_ok\"}");return;}
                    int id=m.getInt("id");check(id>last,"HA message IDs strictly increase");last=id;ids.add(id);
                    Object result=JSONObject.NULL;
                    switch(type){
                        case "auth/current_user": result=new JSONObject().put("id","fake-tv").put("name","Tv test").put("is_admin",false);break;
                        case "subscribe_events": check(m.getString("event_type").equals("state_changed"),"state subscription");break;
                        case "get_states":result=new JSONArray().put(new JSONObject().put("entity_id","sensor.fake").put("state","1"));break;
                        case "home_assistant_tv/subscribe":check(panel,"panel explicitly enabled");break;
                        case "home_assistant_tv/ack":check(m.getLong("revision")==7&&m.getString("error").isEmpty(),"profile acknowledged");done.countDown();break;
                        default:throw new AssertionError("Unexpected command "+type);
                    }
                    c.send(new JSONObject().put("id",id).put("type","result").put("success",true).put("result",result).toString());
                    if(type.equals("get_states"))c.send(new JSONObject().put("id",2).put("type","event").put("event",new JSONObject().put("data",new JSONObject().put("entity_id","sensor.fake").put("new_state",new JSONObject().put("entity_id","sensor.fake").put("state","2")))).toString());
                    if(type.equals("home_assistant_tv/subscribe"))c.send(new JSONObject().put("id",id).put("type","event").put("event",new JSONObject().put("profile","test").put("revision",7)).toString());
                }catch(Throwable e){error.set(e);done.countDown();}}
            };
            HaLiveClient[] client=new HaLiveClient[1];server.start();
            try {
                check(started.await(5,TimeUnit.SECONDS),"loopback server started");String base="https://127.0.0.1:"+server.getPort();
                memory.values.put("ha_url",base);memory.values.put("ha_token","fake-loopback-token");memory.values.put("ha_panel_enabled",panel);memory.values.put("ha_panel_server",base);memory.values.put("ha_panel_profile","test");
                Context context=new ContextWrapper(test.getTargetContext()){@Override public SharedPreferences getSharedPreferences(String n,int mode){return memory.prefs;}};
                test.runOnMainSync(()->{client[0]=new HaLiveClient(context,new HaLiveClient.Listener(){
                    public void connection(boolean online){}
                    public void states(Map<String,JSONObject> states){if("2".equals(states.get("sensor.fake").optString("state"))){updated.countDown();if(!panel)done.countDown();}}
                    public void configuration(JSONObject snapshot)throws Exception{check(snapshot.getLong("revision")==7,"profile received");}
                }){@Override java.net.URI socketUri(){return java.net.URI.create("ws://127.0.0.1:"+server.getPort()+"/api/websocket");}};client[0].start();});
                check(done.await(15,TimeUnit.SECONDS),"connection sequence completed");if(error.get()!=null)throw new AssertionError("Fake HA protocol",error.get());
                check(updated.await(2,TimeUnit.SECONDS),"live state update received");
                check(ids.equals(panel?Arrays.asList(1,2,3,4,10):Arrays.asList(1,2,3)),"ordered auth, states, registration and ack");
                check("Tv test".equals(memory.prefs.getString("ha_authenticated_user",""))&&!memory.prefs.getBoolean("ha_authenticated_admin",true),"non-admin identity saved in fake preferences");
            }finally{test.runOnMainSync(()->{if(client[0]!=null)client[0].stop();});server.stop(1000);}
        }
    }
}
