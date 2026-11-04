package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.RectF;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.NetworkCapabilities;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Synthetic settings/counters and packaged media; never calls su or plays HDR. */
final class LauncherToolsTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void run(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();
        int[] calls={0};RootAccess.Probe probe=()->{calls[0]++;return RootAccess.Result.GRANTED;};
        check(RootAccess.check(m.prefs,probe)==RootAccess.Result.DISABLED&&calls[0]==0,"disabled root never starts a process");
        m.values.put(RootAccess.ENABLED,true);
        check(RootAccess.check(m.prefs,probe)==RootAccess.Result.GRANTED&&calls[0]==1,"explicit opt-in permits injected probe");
        check(RootAccess.check(m.prefs,()->RootAccess.Result.DENIED)==RootAccess.Result.DENIED,"root denial preserved");
        check(RootAccess.check(m.prefs,()->{throw new java.io.IOException();})==RootAccess.Result.UNAVAILABLE,"missing root safely handled");
        check(RootAccess.check(m.prefs,()->RootAccess.Result.TIMEOUT)==RootAccess.Result.TIMEOUT,"root timeout reported");
        m.values.clear();m.values.put("ha_token","test-only");m.values.put("favorite_apps_order","org.example.player");
        AppNames.save(m.prefs,"org.example.player","  Mon cinéma  ");
        check(AppNames.label(m.prefs,"org.example.player","Player").equals("Mon cinéma"),"display name trimmed");
        for(String bad:new String[]{"", " ", "x\ny", "x\u202ey", "x".repeat(65)})check(!AppNames.valid(bad),"invalid app label rejected");
        check(AppNames.valid("Écran 🎬"),"localized Unicode label supported");
        AppNames.save(m.prefs,"org.example.player",null);
        check(AppNames.label(m.prefs,"org.example.player","Player").equals("Player"),"restore original name");
        check(!m.values.containsKey(AppNames.KEY)&&m.values.size()==2,"rename leaves credentials and favorites unchanged");
        Map<String,Object> original=new HashMap<>(m.values);
        JSONArray presets=LayoutPresets.list(context);check(presets.length()==3,"three bundled layouts");
        for(int i=0;i<presets.length();i++){
            JSONObject values=presets.getJSONObject(i).getJSONObject("settings");
            LayoutPresets.apply(context,m.prefs,values);
            check(m.values.get("ha_token").equals("test-only")&&m.values.get("favorite_apps_order").equals("org.example.player"),"layout never changes connections or content");
            for(java.util.Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();check(m.values.get(key).equals(values.get(key)),"preset setting applied");}
            LayoutPresets.undo(context,m.prefs);check(m.values.equals(original),"undo restores absent and existing settings exactly");
        }
        check(!LayoutPresets.allowed("ha_token")&&!LayoutPresets.allowed("custom_cards_yaml")&&!LayoutPresets.allowed(RootAccess.ENABLED),"preset scope excludes sensitive or destructive keys");
        m.values.put("nav_width",67);LayoutPresets.apply(context,m.prefs,presets.getJSONObject(0).getJSONObject("settings"));LayoutPresets.undo(context,m.prefs);
        check(m.values.get("nav_width").equals(67),"undo preserves prior explicit value");
        check(HdrCapabilities.label(1).equals("Dolby Vision")&&HdrCapabilities.label(2).equals("HDR10")&&HdrCapabilities.label(3).equals("HLG")&&HdrCapabilities.label(4).equals("HDR10+"),"declared HDR names");
        check(!HdrCapabilities.has(new int[0],2)&&HdrCapabilities.has(new int[]{1,2},2),"no invented HDR capability");
        for(String name:new String[]{"hdr_near_black","hdr_highlights"}){
            int id=context.getResources().getIdentifier(name,"raw",context.getPackageName());
            MediaExtractor extractor=new MediaExtractor();
            try(android.content.res.AssetFileDescriptor file=context.getResources().openRawResourceFd(id)){
                extractor.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
                check(extractor.getTrackCount()==1,"silent local HDR pattern");MediaFormat f=extractor.getTrackFormat(0);
                check(f.getString(MediaFormat.KEY_MIME).equals("video/hevc"),"real HEVC video, not SDR drawing");
                check(f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)==MediaFormat.COLOR_TRANSFER_ST2084,"PQ HDR transfer");
                check(f.getInteger(MediaFormat.KEY_COLOR_STANDARD)==MediaFormat.COLOR_STANDARD_BT2020,"BT.2020 primaries");
                check(f.getLong(MediaFormat.KEY_DURATION)<=12000000,"pattern limited to twelve seconds");
            }finally{extractor.release();}
        }
        network();navigation();
    }
    static void network(){
        NetworkStatus.Rates r=new NetworkStatus.Rates();
        check(r.text(Locale.FRANCE).equals("↓— ↑—"),"no fabricated initial speed");
        r.sample(100,200,1000);check(r.rx<0,"two samples required");
        r.sample(250100,250200,3000);check(r.rx==1000000&&r.tx==1000000,"bytes to bits per second");
        check(r.text(Locale.FRANCE).equals("↓1,0 ↑1,0 Mb/s"),"localized megabits");
        r.sample(250100,250200,5000);check(r.text(Locale.US).equals("↓0.0 ↑0.0 kb/s"),"idle is real zero");
        r.sample(1,1,7000);check(r.rx<0,"counter reset not negative speed");
        r.sample(2,2,19000);check(r.rx<0,"long pause not treated as current speed");
        r.sample(-1,1,21000);check(r.lastTime==-1&&r.rx<0,"unsupported counters reset state");
        r.sample(1,1,21000);r.sample(2,2,21000);check(r.rx<0,"zero interval rejected");
        r.reset();check(r.lastTime==-1,"pause clears samples");
        check(NetworkStatus.transport(false,true,false)==NetworkStatus.Kind.WIFI,"Wi-Fi transport");
        check(NetworkStatus.transport(true,true,false)==NetworkStatus.Kind.ETHERNET,"Ethernet priority");
        check(NetworkStatus.transport(false,true,true)==NetworkStatus.Kind.VPN,"VPN not misreported as direct Wi-Fi");
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();check(!NetworkStatus.enabled(m.prefs),"network indicators default off");
        m.values.put(NetworkStatus.RATE,true);check(NetworkStatus.enabled(m.prefs),"rate independent of icon");
    }
    static void navigation()throws Exception{
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();
        m.values.put("nav_items",new JSONArray(NavigationConfig.defaults().subList(0,3)).toString());
        for(int edge=0;edge<4;edge++)for(int width:new int[]{40,60,80,100})for(int scale:new int[]{70,100,160})for(int info=0;info<16;info++)for(int large=0;large<2;large++){
            m.values.put("nav_position",edge);m.values.put("nav_width",width);m.values.put("nav_scale",scale);
            m.values.put("show_clock",(info&1)!=0);m.values.put("show_status",(info&2)!=0);m.values.put(NetworkStatus.ICON,(info&4)!=0);m.values.put(NetworkStatus.RATE,(info&8)!=0);
            m.values.put("nav_clock_size",large==0?20:40);m.values.put("nav_status_size",large==0?14:28);
            LauncherLayout layout=new LauncherLayout(m.prefs);NavigationConfig config=new NavigationConfig(m.prefs);
            NavigationStrip strip=new NavigationStrip(m.prefs,layout,config,config.code(2),0);RectF bar=layout.navigation();
            check(!strip.tabs.isEmpty()&&strip.tabs.get(strip.tabs.size()-1).index==2,"last tab remains reachable with network info");
            RectF[] bounds={strip.clock,strip.status,strip.network};
            for(RectF rect:bounds)if(!rect.isEmpty())check(bar.contains(rect),"information stays inside bar");
            check(strip.network.isEmpty()==((info&12)==0),"network hidden only when both toggles off");
            for(int a=0;a<bounds.length;a++)for(int b=a+1;b<bounds.length;b++)check(!RectF.intersects(bounds[a],bounds[b]),"information rows never overlap");
            for(NavigationStrip.Tab tab:strip.tabs)for(RectF rect:bounds)check(!RectF.intersects(tab.rect,rect),"tabs never overlap network, clock or HA status");
        }
    }
}
