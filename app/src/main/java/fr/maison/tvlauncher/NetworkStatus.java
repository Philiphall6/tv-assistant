package fr.maison.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;

/** Foreground-only, passive counters. No speed test, SSID, IP, history or upload. */
final class NetworkStatus {
    static final String ICON="nav_show_network", RATE="nav_show_network_rate";
    enum Kind { UNKNOWN, OFFLINE, ETHERNET, WIFI, VPN, OTHER }
    Kind kind=Kind.UNKNOWN;
    boolean validated;
    final Rates rates=new Rates();
    final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    ConnectivityManager manager;
    ConnectivityManager.NetworkCallback callback;
    Network current;
    static boolean enabled(SharedPreferences prefs){return !CinemaMode.enabled(prefs)&&(prefs.getBoolean(ICON,false)||prefs.getBoolean(RATE,false));}

    void start(Context context){
        if(callback!=null)return;
        manager=context.getSystemService(ConnectivityManager.class);
        if(manager==null)return;
        callback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){if(callback!=this)return;current=network;kind=Kind.UNKNOWN;validated=false;rates.reset();}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){
                if(callback!=this||!network.equals(current))return;
                Kind next=transport(caps);
                if(next!=kind)rates.reset();
                kind=next;validated=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            @Override public void onLost(Network network){if(callback!=this||!network.equals(current))return;current=null;kind=Kind.OFFLINE;validated=false;rates.reset();}
        };
        try {
            // Only a snapshot for the initial disconnected state. Live changes come from callbacks.
            if(manager.getActiveNetwork()==null)kind=Kind.OFFLINE;
            manager.registerDefaultNetworkCallback(callback,new Handler(Looper.getMainLooper()));
        } catch(RuntimeException unavailable){callback=null;kind=Kind.UNKNOWN;}
    }
    void stop(){
        ConnectivityManager.NetworkCallback previous=callback;callback=null;
        if(previous!=null&&manager!=null)try{manager.unregisterNetworkCallback(previous);}catch(RuntimeException ignored){}
        current=null;kind=Kind.UNKNOWN;validated=false;rates.reset();
    }
    void sample(boolean showRate){
        if(!showRate||kind==Kind.UNKNOWN||kind==Kind.OFFLINE){rates.reset();return;}
        try{rates.sample(TrafficStats.getTotalRxBytes(),TrafficStats.getTotalTxBytes(),SystemClock.elapsedRealtime());}
        catch(RuntimeException unavailable){rates.reset();}
    }
    static Kind transport(NetworkCapabilities caps){
        return transport(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }
    static Kind transport(boolean ethernet,boolean wifi,boolean vpn){
        if(vpn)return Kind.VPN;
        if(ethernet)return Kind.ETHERNET;
        if(wifi)return Kind.WIFI;
        return Kind.OTHER;
    }
    static final class Rates {
        long lastRx=-1,lastTx=-1,lastTime=-1;
        double rx=-1,tx=-1;
        void reset(){lastRx=lastTx=lastTime=-1;rx=tx=-1;}
        void sample(long received,long sent,long now){
            if(received<0||sent<0||now<0){reset();return;}
            long dt=now-lastTime;
            if(lastTime>=0&&dt>0&&dt<=10000&&received>=lastRx&&sent>=lastTx){rx=(received-lastRx)*8000d/dt;tx=(sent-lastTx)*8000d/dt;}
            else rx=tx=-1;
            lastRx=received;lastTx=sent;lastTime=now;
        }
        String text(Locale locale){
            if(rx<0||tx<0)return "↓— ↑—";
            double max=Math.max(rx,tx),divisor=max>=1e9?1e9:max>=1e6?1e6:1e3;
            String unit=max>=1e9?"Gb/s":max>=1e6?"Mb/s":"kb/s";
            return "↓"+number(rx/divisor,locale)+" ↑"+number(tx/divisor,locale)+" "+unit;
        }
        static String number(double value,Locale locale){return value>=999.9?"999+":String.format(locale,"%.1f",value);}
    }
    void drawIcon(Canvas canvas,RectF box,int color){
        canvas.save();canvas.translate(box.left,box.top);canvas.scale(box.width()/24f,box.height()/24f);
        ink.setColor(kind==Kind.OFFLINE||kind==Kind.UNKNOWN?0xff9ba6af:validated?color:0xffe7b56d);
        ink.setStrokeWidth(2.5f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStyle(Paint.Style.STROKE);
        if(kind==Kind.WIFI){
            canvas.drawArc(2,5,22,25,225,90,false,ink);canvas.drawArc(6,10,18,22,225,90,false,ink);
            ink.setStyle(Paint.Style.FILL);canvas.drawCircle(12,19,1.8f,ink);
        }else if(kind==Kind.ETHERNET){
            canvas.drawRoundRect(6,3,18,13,2,2,ink);canvas.drawLine(12,13,12,20,ink);canvas.drawLine(4,20,20,20,ink);
            for(int x=9;x<=15;x+=3)canvas.drawLine(x,4,x,7,ink);
        }else{
            canvas.drawCircle(12,12,8,ink);
            if(kind==Kind.OFFLINE)canvas.drawLine(5,5,19,19,ink);
            else {ink.setStyle(Paint.Style.FILL);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(12);canvas.drawText(kind==Kind.VPN?"V":kind==Kind.UNKNOWN?"?":"↔",12,16,ink);}
        }
        canvas.restore();
    }
}
