package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;

final class NotificationTest {
    static int checks;
    static void check(boolean value,String label) { checks++; if(!value)throw new AssertionError(label); }
    static JSONObject payload(String session) throws Exception {
        return new JSONObject().put("kind","notification").put("tag","fixture").put("session",session)
            .put("title","TV Assistant").put("message","Isolated test only").put("duration",8).put("position","top_right");
    }
    static void run(Instrumentation test) throws Exception {
        check(test.getTargetContext().getPackageName().endsWith(".audit"),"Isolated notification tests");
        for(int mask=0;mask<64;mask++) {
            boolean enabled=(mask&1)!=0,permission=(mask&2)!=0,interactive=(mask&4)!=0,on=(mask&8)!=0,dream=(mask&16)!=0,locked=(mask&32)!=0;
            check(TvNotification.allowed(enabled,permission,interactive,on,dream,locked)==(mask==15),"Fail-closed display policy "+mask);
        }
        for(String position:new String[]{"top_right","top_left","bottom_right","bottom_left"}) {
            TvNotification message=new TvNotification(payload("a".repeat(32)).put("position",position));
            WindowManager.LayoutParams p=NotificationOverlayService.parameters(position,400,20);
            check((p.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0,"Does not steal remote focus");
            check((p.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0,"Does not consume touches");
            check((p.flags&(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED))==0,"No wake, screen hold or lock bypass");
            check(p.alpha<=.8f,"Touch-through opacity limit");
            check(p.type==WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,"Modern overlay type");
        }
        for(String key:new String[]{"tag","title","message","session","duration","position","url"}) {
            JSONObject value=payload("a".repeat(32));
            value.put(key,key.equals("duration")?true:key.equals("message")?"x".repeat(601):"");
            boolean rejected=false;try{new TvNotification(value);}catch(Exception expected){rejected=true;}
            check(rejected,"Reject invalid notification field "+key);
        }
        for(Iterator<String> keys=PanelStore.schema(test.getTargetContext()).keys();keys.hasNext();) {
            String key=keys.next(),category=SettingsCatalog.category(key);
            check(Arrays.asList("Accueil","Barre","Applications","Cartes","Fond","Démarrage","Cinéma").contains(category),"Every preference has one category");
            if(key.startsWith("nav_"))check(category.equals("Barre"),"Navigation controls stay together");
        }
        check(SettingsCatalog.category("background_dim").equals("Fond"),"Dimming has one location");
        check(!PanelStore.schema(test.getTargetContext()).has(NotificationOverlayService.ENABLED),"HA cannot enable overlay consent remotely");
        actualOverlay(test);
        protocol(test);
        realPowerAndService(test);
    }
    static class FixtureOverlay extends NotificationOverlayService {
        boolean awake=true;
        void attach(Context context) { attachBaseContext(context); prefs=CardStore.prefs(context); windows=context.getSystemService(WindowManager.class);lastReady=true; }
        @Override public boolean ready() { return awake; }
    }
    static void actualOverlay(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        check(Settings.canDrawOverlays(context),"Audit overlay permission granted by isolated runner");
        AtomicReference<Throwable> error=new AtomicReference<>();
        FixtureOverlay overlay=new FixtureOverlay();
        test.runOnMainSync(()->{
            overlay.attach(context);
            try { overlay.message(new TvNotification(payload(overlay.session))); }
            catch (Exception failure) { error.set(failure); }
        });
        test.waitForIdleSync();
        test.runOnMainSync(()->{
            try {
                String first=overlay.session;
                check(overlay.banner!=null && overlay.banner.isAttachedToWindow(),"Real overlay window attached");
                check(!AmbientModeTest.holdsScreen(overlay.banner),"Overlay never keeps screen awake");
                overlay.awake=false;overlay.refreshPower();
                check(overlay.banner==null,"Sleep removes existing banner immediately");
                overlay.message(new TvNotification(payload(overlay.session)));
                check(overlay.banner==null,"Messages while asleep are ignored");
                overlay.awake=true;overlay.refreshPower();
                check(overlay.banner==null,"Wake-up replays nothing");
                overlay.message(new TvNotification(payload(first)));
                check(overlay.banner==null,"In-flight previous-session message discarded after wake");
                overlay.message(new TvNotification(payload(overlay.session)));
                check(overlay.banner!=null,"New awake-session message renders");
                overlay.message(new TvNotification(new JSONObject().put("kind","notification_dismiss").put("tag","fixture").put("session",overlay.session)));
                check(overlay.banner==null,"Explicit matching-tag dismiss");
            } catch(Throwable failure) { error.set(failure); }
            finally { overlay.removeBanner();overlay.ui.removeCallbacksAndMessages(null); }
        });
        if(error.get()!=null)throw new AssertionError("Overlay lifecycle",error.get());
        Map<String,?> before=new HashMap<>(CardStore.prefs(context).getAll());
        LauncherSettingsActivity settings=(LauncherSettingsActivity)test.startActivitySync(new Intent(context,LauncherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->{
            settings.menu("Applications et favoris");settings.onBackPressed();
            check(settings.section.isEmpty(),"Back returns to settings categories");
            AmbientModeTest.idleWindow(settings);settings.finish();
        });
        test.waitForIdleSync();
        check(before.equals(CardStore.prefs(context).getAll()),"Opening reorganized settings preserves preferences");
        CardManagerActivity cards=(CardManagerActivity)test.startActivitySync(new Intent(context,CardManagerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->{
            boolean duplicate=false;
            for(int i=0;i<cards.box.getChildCount();i++)if(cards.box.getChildAt(i) instanceof android.widget.Button){
                String label=((android.widget.Button)cards.box.getChildAt(i)).getText().toString();
                for(String prefix:new String[]{"Disposition : ","Au démarrage : ","Cartes existantes : "})duplicate|=label.startsWith(I18n.t(prefix));
            }
            check(!duplicate,"Card editor does not duplicate layout and startup settings");cards.finish();
        });
    }
    static void shell(Instrumentation test,String command) throws Exception {
        try (java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer=new byte[1024];while(in.read(buffer)!=-1) {}
        }
    }
    static class RealGate extends NotificationOverlayService {
        void attach(Context context) {
            attachBaseContext(context);prefs=CardStore.prefs(context);windows=context.getSystemService(WindowManager.class);
            displays=context.getSystemService(android.hardware.display.DisplayManager.class);
        }
    }
    static void realPowerAndService(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        android.content.SharedPreferences prefs=CardStore.prefs(context);
        Map<String,?> original=new HashMap<>(prefs.getAll());
        android.app.Activity activity=null;
        RealGate gate=new RealGate();
        try {
            shell(test,"input keyevent 224"); shell(test,"wm dismiss-keyguard");
            TokenStore.save(prefs,"https://127.0.0.1:1","fixture-only-token");
            prefs.edit().putBoolean(NotificationOverlayService.ENABLED,true).putBoolean("ha_panel_enabled",true)
                .putString("ha_panel_server","https://127.0.0.1:1").putString("ha_panel_device_id","fixture-tv").commit();
            activity=test.startActivitySync(new Intent(context,NotificationSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.app.Activity visible=activity;
            test.runOnMainSync(()->{
                gate.attach(context);
                gate.refreshPower();
                NotificationOverlayService.startFromActivity(visible);
            });
            android.os.SystemClock.sleep(500);test.waitForIdleSync();
            android.app.ActivityManager manager=context.getSystemService(android.app.ActivityManager.class);
            check(manager.getRunningServices(100).stream().anyMatch(s->s.service.getClassName().equals(NotificationOverlayService.class.getName())&&s.foreground),"Real Android foreground receiver starts from visible activity");
            check(gate.ready(),"Real display is awake and available");
            String beforeSleep=gate.session;
            shell(test,"input keyevent 223");android.os.SystemClock.sleep(700);
            test.runOnMainSync(gate::refreshPower);
            check(!gate.ready(),"Real screen-off denies notifications");
            test.runOnMainSync(()->{try{gate.message(new TvNotification(payload(gate.session)));}catch(Exception e){throw new AssertionError(e);}});
            check(gate.banner==null,"Real standby discards messages");
            shell(test,"input keyevent 224");shell(test,"wm dismiss-keyguard");android.os.SystemClock.sleep(700);
            test.runOnMainSync(gate::refreshPower);
            check(gate.ready()&&!beforeSleep.equals(gate.session),"Real wake rotates session");
            test.runOnMainSync(()->{
                try { gate.message(new TvNotification(payload(beforeSleep))); }
                catch(Exception e){throw new AssertionError(e);}
                check(gate.banner==null,"No stale message reappears after real wake");
                gate.power.onReceive(context,new Intent(Intent.ACTION_DREAMING_STARTED));
                check(!gate.ready(),"Screensaver broadcast pauses receiver");
                gate.power.onReceive(context,new Intent(Intent.ACTION_DREAMING_STOPPED));
                check(gate.ready(),"Screensaver exit restores readiness without replay");
            });
        } finally {
            NotificationOverlayService.disable(context);
            android.app.Activity closing=activity;
            test.runOnMainSync(()->{gate.removeBanner();gate.ui.removeCallbacksAndMessages(null);if(closing!=null)closing.finish();});
            test.waitForIdleSync();
            android.content.SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()) {
                Object v=entry.getValue();String key=entry.getKey();
                if(v instanceof String)restore.putString(key,(String)v);
                else if(v instanceof Boolean)restore.putBoolean(key,(Boolean)v);
                else if(v instanceof Integer)restore.putInt(key,(Integer)v);
                else if(v instanceof Long)restore.putLong(key,(Long)v);
                else if(v instanceof Float)restore.putFloat(key,(Float)v);
            }
            restore.commit();shell(test,"input keyevent 224");shell(test,"wm dismiss-keyguard");
        }
    }
    static void protocol(Instrumentation test) throws Exception {
        CountDownLatch started=new CountDownLatch(1),delivered=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        List<String> commands=new CopyOnWriteArrayList<>();
        WebSocketServer server=new WebSocketServer(new InetSocketAddress("127.0.0.1",0)) {
            public void onStart(){started.countDown();}
            public void onOpen(WebSocket c,ClientHandshake h){c.send("{\"type\":\"auth_required\"}");}
            public void onClose(WebSocket c,int code,String reason,boolean remote){}
            public void onError(WebSocket c,Exception error){failure.set(error);delivered.countDown();}
            public void onMessage(WebSocket c,String raw){try{
                JSONObject message=new JSONObject(raw);String type=message.getString("type");commands.add(type);
                if(type.equals("auth")){check(message.getString("access_token").equals("fixture-only-token"),"Fake token only");c.send("{\"type\":\"auth_ok\"}");return;}
                c.send(new JSONObject().put("type","result").put("id",message.getInt("id")).put("success",true).put("result",JSONObject.NULL).toString());
                if(type.equals("home_assistant_tv/notification_subscribe")){
                    check(message.getString("session").equals("b".repeat(32)),"Awake session bound to subscription");
                    for(String session:new String[]{"a".repeat(32),"b".repeat(32)})c.send(new JSONObject().put("type","event").put("id",1).put("event",payload(session)).toString());
                }
            }catch(Throwable error){failure.set(error);delivered.countDown();}}
        };
        NotificationClient[] client=new NotificationClient[1];List<TvNotification> received=new CopyOnWriteArrayList<>();
        server.start();
        try{
            check(started.await(5,TimeUnit.SECONDS),"Loopback message server started");
            test.runOnMainSync(()->{
                client[0]=new NotificationClient(new Handler(Looper.getMainLooper()),new NotificationClient.Listener(){
                    public void message(TvNotification message){received.add(message);delivered.countDown();}
                    public void status(String status){}
                    public boolean ready(){return true;}
                    public String session(){return "b".repeat(32);}
                },"https://fixture.invalid","fixture-only-token","fixture-tv"){
                    @Override java.net.URI socketUri(){return java.net.URI.create("ws://127.0.0.1:"+server.getPort()+"/api/websocket");}
                };
                client[0].start();
            });
            check(delivered.await(10,TimeUnit.SECONDS),"Authenticated notification stream delivered");
            if(failure.get()!=null)throw new AssertionError(failure.get());
            test.waitForIdleSync();
            check(received.size()==1 && received.get(0).session.equals("b".repeat(32)),"Old session is rejected by transport");
            check(commands.stream().allMatch(x->x.equals("auth")||x.startsWith("home_assistant_tv/notification_")),"No HA state subscription or service execution");
        }finally{
            test.runOnMainSync(()->{if(client[0]!=null)client[0].stop();});server.stop(1000);
        }
    }
}
