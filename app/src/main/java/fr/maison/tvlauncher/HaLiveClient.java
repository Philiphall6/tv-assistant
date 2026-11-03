package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import org.json.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.*;

/** One foreground subscription. Callbacks run on the UI thread; reconnects keep the last snapshot. */
class HaLiveClient {
    interface Listener {void states(Map<String,JSONObject> states);void connection(boolean online);default void configuration(JSONObject snapshot)throws Exception{}default Bitmap preview()throws Exception{throw new IllegalStateException("Capture indisponible");}}
    final Handler ui=new Handler(Looper.getMainLooper());
    final Listener listener;final String base,token;final Context context;final boolean panel;
    final Map<String,JSONObject> states=new LinkedHashMap<>();
    WebSocketClient socket;boolean stopped=false;int attempt=0,generation=0;
    HaLiveClient(Context c,Listener l){context=c;listener=l;SharedPreferences p=CardStore.prefs(c);base=p.getString("ha_url","").trim().replaceAll("/+$","");token=TokenStore.get(c);panel=PanelStore.enabled(c);}
    URI socketUri()throws Exception{URI u=URI.create(ServerAddress.normalize(base));return new URI("wss",null,u.getHost(),u.getPort(),"/api/websocket",null,null);}
    void start(){if(base.isEmpty()||token.isEmpty()){listener.connection(false);return;}connect();}
    void stop(){stopped=true;generation++;ui.removeCallbacksAndMessages(null);WebSocketClient old=socket;socket=null;if(old!=null)old.close();}
    void connect(){if(stopped)return;final int gen=++generation;final Map<String,JSONObject> pending=new LinkedHashMap<>();final boolean[] snapshot={false};
        try{
            URI ws=socketUri();
            socket=new WebSocketClient(ws,new org.java_websocket.drafts.Draft_6455(Collections.emptyList(),16*1024*1024),null,8000){
                int nextId=10;boolean capturing=false;long lastCapture=-10000;
                void preview(JSONObject request){
                    final String requestId=request.optString("request_id");
                    if(!requestId.matches("[a-f0-9]{32}"))return;
                    ui.post(()->{
                        if(stopped||gen!=generation||!PanelStore.enabled(context)||!isOpen())return;
                        long now=android.os.SystemClock.elapsedRealtime();
                        if(capturing||now-lastCapture<1800){previewError(requestId);return;}
                        capturing=true;lastCapture=now;
                        final Bitmap bitmap;
                        try{bitmap=listener.preview();if(bitmap==null)throw new IllegalStateException();}
                        catch(Exception e){capturing=false;previewError(requestId);return;}
                        new Thread(()->{JSONObject reply;
                            try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,bytes);if(bytes.size()>500000)throw new IllegalStateException();reply=new JSONObject().put("request_id",requestId).put("image",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).put("width",bitmap.getWidth()).put("height",bitmap.getHeight());}
                            catch(Exception e){reply=new JSONObject();try{reply.put("request_id",requestId).put("error","capture_unavailable");}catch(Exception ignored){}}
                            finally{bitmap.recycle();}
                            final JSONObject result=reply;ui.post(()->{capturing=false;if(stopped||gen!=generation||!isOpen())return;try{send(result.put("id",nextId++).put("type","home_assistant_tv/preview_result").toString());}catch(Exception ignored){}});
                        },"HA-native-preview").start();
                    });
                }
                void previewError(String requestId){try{send(new JSONObject().put("id",nextId++).put("type","home_assistant_tv/preview_result").put("request_id",requestId).put("error","capture_unavailable").toString());}catch(Exception ignored){}}
                @Override protected void onSetSSLParameters(javax.net.ssl.SSLParameters parameters){super.onSetSSLParameters(parameters);parameters.setEndpointIdentificationAlgorithm("HTTPS");}
                public void onOpen(ServerHandshake h){}
                public void onMessage(String message){try{JSONObject m=new JSONObject(message);String type=m.optString("type");
                    if(type.equals("auth_required")){send(new JSONObject().put("type","auth").put("access_token",token).toString());return;}
                    if(type.equals("auth_ok")){send("{\"id\":1,\"type\":\"auth/current_user\"}");send("{\"id\":2,\"type\":\"subscribe_events\",\"event_type\":\"state_changed\"}");return;}
                    if(type.equals("auth_invalid")){ui.post(()->{if(gen==generation){stopped=true;listener.connection(false);}});close();return;}
                    if(type.equals("result")&&m.optInt("id")==1){if(m.optBoolean("success")){JSONObject user=m.getJSONObject("result");SharedPreferences p=CardStore.prefs(context);if(token.equals(TokenStore.get(context)))p.edit().putString("ha_authenticated_user",user.optString("name")).putString("ha_authenticated_user_id",user.optString("id")).putBoolean("ha_authenticated_admin",user.optBoolean("is_admin")).putString("ha_authenticated_token_fingerprint",HaWebAuth.fingerprint(token)).apply();}return;}
                    if(type.equals("result")&&m.optInt("id")==2){if(!m.optBoolean("success")){close();return;}send("{\"id\":3,\"type\":\"get_states\"}");return;}
                    if(type.equals("result")&&m.optInt("id")==3){if(!m.optBoolean("success")){close();return;}JSONArray list=m.getJSONArray("result");Map<String,JSONObject> initial=new LinkedHashMap<>();for(int i=0;i<list.length();i++){JSONObject s=list.getJSONObject(i);initial.put(s.getString("entity_id"),s);}ui.post(()->{if(stopped||gen!=generation)return;states.clear();states.putAll(initial);for(Map.Entry<String,JSONObject> e:pending.entrySet())if(e.getValue()==null)states.remove(e.getKey());else states.put(e.getKey(),e.getValue());pending.clear();snapshot[0]=true;attempt=0;listener.connection(true);listener.states(states);if(panel)try{send(PanelStore.registration(context).put("id",4).put("type","home_assistant_tv/subscribe").toString());}catch(Exception e){PanelStore.status(context,"Liaison au panneau impossible : "+e.getMessage());}});return;}
                    if(type.equals("result")&&m.optInt("id")==4&&!m.optBoolean("success")){ui.post(()->{if(!stopped&&gen==generation)PanelStore.status(context,"Panneau HA indisponible : vérifiez l’intégration TV Assistant et les droits du compte");});return;}
                    if(type.equals("event")&&m.optInt("id")==4){JSONObject config=m.getJSONObject("event");if(config.optString("kind").equals("preview_request")){preview(config);return;}ui.post(()->{if(stopped||gen!=generation||!PanelStore.enabled(context))return;String error="";try{listener.configuration(config);}catch(Exception e){error="Profil refusé : "+e.getMessage();PanelStore.status(context,error);}try{if(isOpen()&&config.optLong("revision")>0)send(new JSONObject().put("id",nextId++).put("type","home_assistant_tv/ack").put("profile",config.optString("profile")).put("revision",config.optLong("revision")).put("error",error).toString());}catch(Exception ignored){}});return;}
                    if(type.equals("event")&&m.optInt("id")==2){JSONObject data=m.getJSONObject("event").getJSONObject("data");String id=data.getString("entity_id");JSONObject state=data.optJSONObject("new_state");ui.post(()->{if(stopped||gen!=generation)return;if(!snapshot[0]){pending.put(id,state);return;}if(state==null)states.remove(id);else states.put(id,state);listener.states(states);});}
                }catch(Exception e){close();}}
                public void onClose(int code,String reason,boolean remote){ui.post(()->{if(stopped||gen!=generation)return;listener.connection(false);int delay=Math.min(30000,1000*(1<<Math.min(attempt++,5)));ui.postDelayed(()->{if(!stopped&&gen==generation)HaLiveClient.this.connect();},delay);});}
                public void onError(Exception e){/* Never log URLs, state payloads or access tokens. onClose schedules retry. */}
            };
            socket.setConnectionLostTimeout(20);socket.connect();
            ui.postDelayed(()->{if(!stopped&&gen==generation&&!snapshot[0]&&socket!=null)socket.close();},15000);
        }catch(Exception e){listener.connection(false);}
    }
}
