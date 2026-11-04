package fr.maison.tvlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.UUID;

/** User-enabled overlay. Never wakes, holds the screen, queues or replays a message. */
public class NotificationOverlayService extends Service implements NotificationClient.Listener {
    static final String ENABLED="tv_notifications_enabled", STATUS="tv_notifications_status";
    static final String CHANNEL="tv_assistant_message_receiver";
    static final int FOREGROUND_ID=410;
    final Handler ui=new Handler(Looper.getMainLooper());
    NotificationClient client;
    WindowManager windows;
    DisplayManager displays;
    LinearLayout banner;
    String visibleTag="", session=nonce();
    boolean dreaming, screenOff, lastReady, closing;
    SharedPreferences prefs;
    final Runnable hide=this::removeBanner;
    final Runnable monitor=new Runnable() {
        public void run() { if (closing) return; refreshPower(); ui.postDelayed(this,banner==null?5000:500); }
    };
    final BroadcastReceiver power=new BroadcastReceiver() {
        public void onReceive(Context context,Intent intent) {
            String action=intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) screenOff=true;
            if (Intent.ACTION_SCREEN_ON.equals(action)) screenOff=false;
            if (Intent.ACTION_DREAMING_STARTED.equals(action)) dreaming=true;
            if (Intent.ACTION_DREAMING_STOPPED.equals(action)) dreaming=false;
            refreshPower();
        }
    };
    final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) { refreshPower(); }
        public void onDisplayRemoved(int id) { refreshPower(); }
        public void onDisplayChanged(int id) { refreshPower(); }
    };
    final SharedPreferences.OnSharedPreferenceChangeListener preferences=(p,key)->{
        if (key==null || key.equals(ENABLED) || key.equals("ha_url") || key.equals(TokenStore.ENCRYPTED)
            || key.equals(TokenStore.ORIGIN) || key.equals("ha_panel_enabled") || key.equals("ha_panel_server")
            || key.equals("ha_panel_device_id")) {
            ui.post(()->{if (!closing) { removeBanner(); session=nonce(); if (client!=null) client.stop(); client=null; connect(); }});
        }
    };

    static String nonce() { return UUID.randomUUID().toString().replace("-",""); }
    static void startFromActivity(Activity activity) {
        if (!CardStore.prefs(activity).getBoolean(ENABLED,false)) return;
        try { activity.startForegroundService(new Intent(activity,NotificationOverlayService.class)); }
        catch (Exception ignored) { CardStore.prefs(activity).edit().putString(STATUS,"blocked").apply(); }
    }
    static void disable(Context context) {
        CardStore.prefs(context).edit().putBoolean(ENABLED,false).apply();
        context.stopService(new Intent(context,NotificationOverlayService.class));
    }
    @Override public void onCreate() {
        super.onCreate(); prefs=CardStore.prefs(this);
        windows=getSystemService(WindowManager.class); displays=getSystemService(DisplayManager.class);
        NotificationManager manager=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,I18n.t("Réception des notifications TV"),NotificationManager.IMPORTANCE_MIN);
        channel.setSound(null,null); channel.enableVibration(false); channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
        PendingIntent settings=PendingIntent.getActivity(this,0,new Intent(this,NotificationSettingsActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.installation_icon)
            .setContentTitle("TV Assistant").setContentText(I18n.t("Notifications actives · aucune notification en veille"))
            .setContentIntent(settings).setOngoing(true).setOnlyAlertOnce(true).setDefaults(0).setSound(null).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET).setCategory(Notification.CATEGORY_SERVICE).build();
        if (Build.VERSION.SDK_INT>=34) startForeground(FOREGROUND_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(FOREGROUND_ID,notification);
        IntentFilter filter=new IntentFilter();
        for (String action:new String[]{Intent.ACTION_SCREEN_OFF,Intent.ACTION_SCREEN_ON,Intent.ACTION_DREAMING_STARTED,Intent.ACTION_DREAMING_STOPPED,Intent.ACTION_USER_PRESENT}) filter.addAction(action);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(power,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(power,filter);
        if (displays!=null) displays.registerDisplayListener(displayListener,ui);
        prefs.registerOnSharedPreferenceChangeListener(preferences);
        ui.post(monitor);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        connect();
        // No boot receiver or sticky restart: Android must not start this during sleep.
        return START_NOT_STICKY;
    }
    void connect() {
        if (client!=null && client.stopped) client=null;
        if (closing || client!=null) return;
        if (!prefs.getBoolean(ENABLED,false) || !PanelStore.enabled(this) || !HaClient.isConfigured(this)) {
            status("configuration"); stopSelf(); return;
        }
        if (!Settings.canDrawOverlays(this)) { status("permission"); stopSelf(); return; }
        lastReady=ready(); session=nonce();
        client=new NotificationClient(ui,this,prefs.getString("ha_url",""),TokenStore.get(this),PanelStore.identity(this));
        client.start();
    }
    public boolean ready() {
        PowerManager powerManager=getSystemService(PowerManager.class);
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        Display display=displays==null?null:displays.getDisplay(Display.DEFAULT_DISPLAY);
        return TvNotification.allowed(prefs!=null && prefs.getBoolean(ENABLED,false) && PanelStore.enabled(this),
            Settings.canDrawOverlays(this),powerManager!=null && powerManager.isInteractive(),
            !screenOff && display!=null && display.getState()==Display.STATE_ON,
            dreaming,keyguard==null || keyguard.isKeyguardLocked());
    }
    public String session() { return session; }
    void refreshPower() {
        boolean available=ready();
        if (!available) removeBanner();
        if (available!=lastReady) {
            // All packets from the previous awake period become invalid, even in flight.
            session=nonce(); lastReady=available;
            if (client!=null) client.availability();
        }
    }
    public void status(String state) {
        prefs.edit().putString(STATUS,state).apply();
        if (!"connected".equals(state)) removeBanner();
    }
    static WindowManager.LayoutParams parameters(String position,int width,int margin) {
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity=(position.startsWith("top")?Gravity.TOP:Gravity.BOTTOM)|(position.endsWith("left")?Gravity.LEFT:Gravity.RIGHT);
        params.x=margin; params.y=margin;
        // Android 12+ can pass touches through an untrusted overlay up to this opacity.
        params.alpha=.8f; params.setTitle("TV Assistant notification");
        return params;
    }
    public void message(TvNotification message) {
        refreshPower();
        if (!ready() || !session.equals(message.session)) return;
        if (message.dismiss) { if (message.tag.equals(visibleTag)) removeBanner(); return; }
        removeBanner();
        LinearLayout view=new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(22),dp(16),dp(22),dp(18)); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        GradientDrawable background=new GradientDrawable(); background.setColor(0xff14232b); background.setCornerRadius(dp(18));
        background.setStroke(dp(1),0xff75c9d4); view.setBackground(background);
        TextView title=new TextView(this); title.setText(message.title); title.setTextColor(0xffa0e1e5); title.setTextSize(20); title.setTypeface(Typeface.DEFAULT_BOLD); title.setMaxLines(2);
        TextView body=new TextView(this); body.setText(message.message); body.setTextColor(Color.WHITE); body.setTextSize(17); body.setMaxLines(6); body.setEllipsize(android.text.TextUtils.TruncateAt.END); body.setPadding(0,dp(6),0,0);
        view.addView(title); view.addView(body);
        int width=Math.min(dp(520),(int)(getResources().getDisplayMetrics().widthPixels*.45f));
        try {
            if (!ready()) return;
            windows.addView(view,parameters(message.position,width,dp(28)));
            banner=view; visibleTag=message.tag;
            ui.postDelayed(hide,message.seconds*1000L);
        } catch (RuntimeException ignored) { status("permission"); }
    }
    void removeBanner() {
        ui.removeCallbacks(hide);
        if (banner!=null) { try { windows.removeViewImmediate(banner); } catch (RuntimeException ignored) {} banner=null; }
        visibleTag="";
    }
    int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        closing=true; removeBanner();
        if (client!=null) client.stop(); ui.removeCallbacksAndMessages(null);
        if (displays!=null) displays.unregisterDisplayListener(displayListener);
        unregisterReceiver(power); prefs.unregisterOnSharedPreferenceChangeListener(preferences);
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}
