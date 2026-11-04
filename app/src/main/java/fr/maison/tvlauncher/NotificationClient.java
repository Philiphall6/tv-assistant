package fr.maison.tvlauncher;

import android.os.Handler;
import java.net.URI;
import java.util.Collections;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

/** Dedicated message stream. No HA state subscription, persistent inbox or replay. */
class NotificationClient {
    interface Listener {
        void message(TvNotification message);
        void status(String status);
        boolean ready();
        String session();
    }
    final Handler ui;
    final Listener listener;
    final String base, token, identity;
    WebSocketClient socket;
    boolean stopped, subscribed;
    int generation, attempt, serial = 10;
    NotificationClient(Handler ui, Listener listener, String base, String token, String identity) {
        this.ui=ui; this.listener=listener; this.base=base; this.token=token; this.identity=identity;
    }
    void start() { connect(); }
    void stop() {
        stopped=true; generation++; subscribed=false;
        WebSocketClient previous=socket; socket=null;
        if (previous!=null) previous.close();
    }
    void availability() {
        if (!stopped && subscribed && socket!=null && socket.isOpen()) try {
            socket.send(new JSONObject().put("id",serial++).put("type","home_assistant_tv/notification_state")
                .put("subscription",1).put("ready",listener.ready()).put("session",listener.session()).toString());
        } catch (Exception ignored) { socket.close(); }
    }
    URI socketUri() throws Exception {
        URI origin=URI.create(ServerAddress.normalize(base));
        return new URI("wss",null,origin.getHost(),origin.getPort(),"/api/websocket",null,null);
    }
    void connect() {
        if (stopped) return;
        int gen=++generation; subscribed=false; listener.status("connecting");
        try {
            socket=new WebSocketClient(socketUri(),new org.java_websocket.drafts.Draft_6455(Collections.emptyList(),32768),null,8000) {
                @Override protected void onSetSSLParameters(javax.net.ssl.SSLParameters p) {
                    super.onSetSSLParameters(p); p.setEndpointIdentificationAlgorithm("HTTPS");
                }
                public void onOpen(ServerHandshake handshake) {}
                public void onMessage(String raw) {
                    if (raw.length()>8192) { close(); return; }
                    try {
                        JSONObject value=new JSONObject(raw);
                        // All session reads, lifecycle guards and rendering share the main thread.
                        ui.post(()->{
                            if (stopped || gen!=generation) return;
                            try {
                                String type=value.optString("type");
                                if (type.equals("auth_required")) send(new JSONObject().put("type","auth").put("access_token",token).toString());
                                else if (type.equals("auth_ok")) send(new JSONObject().put("id",1).put("type","home_assistant_tv/notification_subscribe")
                                    .put("device_id",identity).put("ready",listener.ready()).put("session",listener.session()).toString());
                                else if (type.equals("auth_invalid")) { listener.status("auth"); stop(); }
                                else if (type.equals("result") && value.optInt("id")==1) {
                                    if (!value.optBoolean("success")) { listener.status("refused"); stop(); }
                                    else { subscribed=true; attempt=0; listener.status("connected"); availability(); }
                                } else if (type.equals("result") && !value.optBoolean("success")) close();
                                else if (type.equals("event") && value.optInt("id")==1 && subscribed) {
                                    JSONObject event=value.getJSONObject("event");
                                    if (event.optString("kind").equals("notifications_closed")) { listener.status("refused"); stop(); return; }
                                    TvNotification message=new TvNotification(event);
                                    if (listener.ready() && listener.session().equals(message.session)) listener.message(message);
                                }
                            } catch (Exception ignored) { close(); }
                        });
                    } catch (Exception ignored) { close(); }
                }
                public void onClose(int code,String reason,boolean remote) {
                    ui.post(()->{
                        if (stopped || gen!=generation) return;
                        subscribed=false; listener.status("disconnected");
                        int delay=Math.min(30000,1000*(1<<Math.min(attempt++,5)));
                        ui.postDelayed(()->{if (!stopped && gen==generation) NotificationClient.this.connect();},delay);
                    });
                }
                public void onError(Exception error) { /* No URLs, tokens or messages in logs. */ }
            };
            socket.setConnectionLostTimeout(20); socket.connect();
            ui.postDelayed(()->{if (!stopped && gen==generation && !subscribed && socket!=null) socket.close();},15000);
        } catch (Exception ignored) { listener.status("configuration"); stop(); }
    }
}
