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
        for(String expected:new String[]{LinkStatus.MISSING,LinkStatus.OWNER,LinkStatus.REFUSED,LinkStatus.PENDING,LinkStatus.NO_PROFILE,LinkStatus.FORGOTTEN,LinkStatus.INVALID,LinkStatus.SYNCED}){
            PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();
            CountDownLatch started=new CountDownLatch(1),observed=new CountDownLatch(1);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            WebSocketServer server=new WebSocketServer(new InetSocketAddress("127.0.0.1",0)){
                public void onStart(){started.countDown();}
                public void onOpen(WebSocket c,ClientHandshake h){c.send("{\"type\":\"auth_required\"}");}
                public void onClose(WebSocket c,int code,String reason,boolean remote){}
                public void onError(WebSocket c,Exception e){failure.set(e);observed.countDown();}
                public void onMessage(WebSocket c,String raw){try{
                    JSONObject m=new JSONObject(raw);String type=m.getString("type");
                    if(type.equals("auth")){c.send("{\"type\":\"auth_ok\"}");return;}
                    int id=m.getInt("id");
                    check(!type.equals("call_service"),"status checks never call a service");
                    boolean registration=type.equals("home_assistant_tv/subscribe");
                    if(registration&&(expected.equals(LinkStatus.MISSING)||expected.equals(LinkStatus.OWNER)||expected.equals(LinkStatus.REFUSED))){
                        String code=expected.equals(LinkStatus.MISSING)?"unknown_command":"invalid_profile";
                        String message=expected.equals(LinkStatus.OWNER)?"Identifiant de TV déjà utilisé par un autre compte":"private-server-detail";
                        c.send(new JSONObject().put("id",id).put("type","result").put("success",false).put("error",new JSONObject().put("code",code).put("message",message)).toString());return;
                    }
                    Object result=type.equals("get_states")?new JSONArray():type.equals("auth/current_user")?new JSONObject().put("name","Fixture").put("id","fixture").put("is_admin",false):JSONObject.NULL;
                    c.send(new JSONObject().put("id",id).put("type","result").put("success",true).put("result",result).toString());
                    if(registration){
                        JSONObject snap=new JSONObject().put("profile","test").put("revision",0).put("config",JSONObject.NULL);
                        if(expected.equals(LinkStatus.PENDING))snap.put("approval_required",true);
                        if(expected.equals(LinkStatus.FORGOTTEN))snap.put("connection_removed",true).put("approval_required",true);
                        if(expected.equals(LinkStatus.INVALID))snap.put("revision",1).put("config",new JSONObject().put("schema",999));
                        if(expected.equals(LinkStatus.SYNCED))snap.put("revision",1).put("config",new JSONObject().put("schema",1));
                        c.send(new JSONObject().put("id",id).put("type","event").put("event",snap).toString());
                    }
                }catch(Throwable e){failure.set(e);observed.countDown();}}
            };
            HaLiveClient[] client=new HaLiveClient[1];server.start();
            try{
                check(started.await(5,TimeUnit.SECONDS),"status fixture started");String base="https://127.0.0.1:"+server.getPort();
                memory.values.put("ha_url",base);memory.values.put("ha_token","fake-loopback-token");memory.values.put("ha_panel_enabled",true);memory.values.put("ha_panel_server",base);memory.values.put("ha_panel_profile","test");
                Context context=new ContextWrapper(test.getTargetContext()){@Override public SharedPreferences getSharedPreferences(String n,int mode){return memory.prefs;}};
                test.runOnMainSync(()->{client[0]=new HaLiveClient(context,new HaLiveClient.Listener(){
                    public void states(Map<String,JSONObject> states){}
                    public void connection(boolean online){}
                    public void configuration(JSONObject snapshot)throws Exception{PanelStore.apply(context,snapshot);}
                    public void statusChanged(){if(expected.equals(LinkStatus.panelState(memory.prefs)))observed.countDown();}
                }){@Override java.net.URI socketUri(){return java.net.URI.create("ws://127.0.0.1:"+server.getPort()+"/api/websocket");}};client[0].start();});
                check(observed.await(15,TimeUnit.SECONDS),"editor status observed: "+expected);
                if(failure.get()!=null)throw new AssertionError("Status fixture",failure.get());
                check("connected".equals(memory.prefs.getString("ha_link_state","")),"HA data remains connected independently of editor");
                check(!memory.prefs.getString("ha_panel_status","").contains("private-server-detail"),"raw server error excluded");
            }finally{test.runOnMainSync(()->{if(client[0]!=null)client[0].stop();});server.stop(1000);}
        }
    }
}
