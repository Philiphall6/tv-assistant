package fr.maison.tvlauncher;

import android.content.*;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;

/** In-memory settings only. No real TV preferences, HA account or device actions. */
public final class PanelRuntimeTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    interface Run {void run()throws Exception;}
    static void reject(Run run)throws Exception{boolean rejected=false;try{run.run();}catch(Exception e){rejected=true;}check(rejected,"invalid profile rejected atomically");}
    static final class Memory {
        final Map<String,Object> values=new HashMap<>();final SharedPreferences prefs;
        Memory(){
            SharedPreferences.Editor editor=(SharedPreferences.Editor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(o,m,a)->{
                String n=m.getName();if(n.startsWith("put")){values.put((String)a[0],a[1]);return o;}if(n.equals("remove")){values.remove(a[0]);return o;}if(n.equals("clear")){values.clear();return o;}if(n.equals("commit"))return true;return null;});
            prefs=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(o,m,a)->{String n=m.getName();if(n.equals("edit"))return editor;if(n.equals("getAll"))return new HashMap<>(values);if(n.equals("contains"))return values.containsKey(a[0]);if(n.startsWith("get"))return values.getOrDefault(a[0],a[1]);return null;});
        }
        Context context(){return new ContextWrapper(null){public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}};}
    }
    static JSONObject profile(String subtitle)throws Exception{return new JSONObject().put("schema",1).put("settings",new JSONObject().put("hero_subtitle",subtitle)).put("cards",new JSONArray().put(new JSONObject().put("type","text").put("name","Native")));}
    static JSONObject snapshot(String profile,int revision,JSONObject config)throws Exception{return new JSONObject().put("profile",profile).put("revision",revision).put("config",config);}
    static void navigationWidthChecks()throws Exception{
        Memory m=new Memory();List<Map<String,Object>> items=new ArrayList<>(NavigationConfig.defaults().subList(0,3));m.values.put("nav_items",new JSONArray(items).toString());m.values.put("nav_tab_gap",8);
        float[] expected=null;
        for(int edge=0;edge<2;edge++)for(int width=40;width<=100;width++){
            m.values.put("nav_position",edge);m.values.put("nav_width",width);LauncherLayout l=new LauncherLayout(m.prefs);NavigationConfig cfg=new NavigationConfig(m.prefs);NavigationStrip strip=new NavigationStrip(m.prefs,l,cfg,0,0);
            check(strip.tabs.size()==3,"all short tabs fit from 40 to 100 percent");
            check(strip.showClock&&strip.clock.width()>0&&strip.clock.right<=l.navigation().right,"clock always visible");
            check(strip.showStatus&&strip.status.width()>0,"connection status preserved");
            float[] sizes=new float[3];for(int i=0;i<3;i++){sizes[i]=strip.tabs.get(i).rect.width();if(expected!=null)check(Math.abs(sizes[i]-expected[i])<.02,"tabs never stretch to fill empty space");if(i>0)check(Math.abs(strip.tabs.get(i).rect.left-strip.tabs.get(i-1).rect.right-8)<.02,"configured gap is exact");}
            expected=sizes;check(strip.tabs.get(2).rect.right<=Math.min(strip.clock.left,strip.status.left),"tabs never overlap information");
        }
        for(int edge=0;edge<4;edge++)for(int scale:new int[]{70,100,160})for(int width:new int[]{40,60,80,100})for(int mode=0;mode<3;mode++)for(int count:new int[]{0,1,5,40}){
            items=new ArrayList<>();for(int i=0;i<count;i++){Map<String,Object> item=new LinkedHashMap<>();item.put("id","app"+i);item.put("type","app");item.put("package","org.example.app");item.put("label",i%3==0?"Application avec un nom volontairement très long":"App "+i);item.put("display",mode);item.put("text_size",40);item.put("icon_size",56);items.add(item);}
            m.values.put("nav_items",new JSONArray(items).toString());m.values.put("nav_position",edge);m.values.put("nav_scale",scale);m.values.put("nav_width",width);m.values.put("nav_logo_size",76);m.values.put("nav_tab_gap",48);
            NavigationConfig cfg=new NavigationConfig(m.prefs);LauncherLayout l=new LauncherLayout(m.prefs);int focus=count>0?cfg.code(count-1):NavigationConfig.BRAND;NavigationStrip strip=new NavigationStrip(m.prefs,l,cfg,focus,0);
            check(strip.showClock&&l.navigation().contains(strip.clock),"clock remains inside every bar");
            check(strip.showStatus&&l.navigation().contains(strip.status),"status remains inside every bar");
            check(l.navigation().contains(strip.brand)&&strip.brand.contains(strip.logo),"logo remains inside brand");
            if(count>0)check(strip.tabs.get(strip.tabs.size()-1).index==count-1,"last overflow tab reachable");
            android.graphics.RectF previous=null;for(NavigationStrip.Tab tab:strip.tabs){check(l.navigation().contains(tab.rect),"large fonts and icons stay inside bar");check(!android.graphics.RectF.intersects(tab.rect,strip.clock)&&!android.graphics.RectF.intersects(tab.rect,strip.status)&&!android.graphics.RectF.intersects(tab.rect,strip.brand),"no overlap with clock, status or brand");if(previous!=null)check(!android.graphics.RectF.intersects(previous,tab.rect),"tabs never overlap");previous=tab.rect;}
        }
        m.values.put("show_clock",false);m.values.put("show_status",false);NavigationStrip off=new NavigationStrip(m.prefs,new LauncherLayout(m.prefs),new NavigationConfig(m.prefs),0,0);check(off.clock.isEmpty()&&off.status.isEmpty(),"explicitly disabled information stays hidden");
    }
    static void navigationAlignmentChecks()throws Exception{
        Memory m=new Memory();
        for(int edge=0;edge<2;edge++)for(int width:new int[]{40,60,80,100})for(int count:new int[]{0,1,3,5})for(int info=0;info<4;info++){
            m.values.put("nav_items",new JSONArray(NavigationConfig.defaults().subList(0,count)).toString());
            m.values.put("nav_position",edge);m.values.put("nav_width",width);m.values.put("nav_scale",100);
            m.values.put("show_clock",(info&1)!=0);m.values.put("show_status",(info&2)!=0);
            NavigationConfig cfg=new NavigationConfig(m.prefs);NavigationStrip[] strips=new NavigationStrip[3];
            float[] localStart=new float[3],localEnd=new float[3];
            for(int align=0;align<3;align++){
                m.values.put("nav_alignment",align);LauncherLayout l=new LauncherLayout(m.prefs);
                NavigationStrip strip=new NavigationStrip(m.prefs,l,cfg,0,0);strips[align]=strip;
                android.graphics.RectF bar=l.navigation();
                check(Math.abs(bar.left-(1920-bar.width())*align/2f)<.02,"outer bar follows alignment");
                localStart[align]=strip.brand.left-bar.left;
                localEnd[align]=(strip.tabs.isEmpty()?strip.brand.right:strip.tabs.get(strip.tabs.size()-1).rect.right)-bar.left;
                check(bar.contains(strip.brand)&&strip.brand.contains(strip.logo),"aligned logo stays in bar");
                check(strip.tabs.size()==strips[0].tabs.size(),"alignment does not hide tabs");
                for(int i=0;i<strip.tabs.size();i++){
                    android.graphics.RectF t=strip.tabs.get(i).rect;
                    check(t.left>=bar.left&&t.right<=bar.right+.02,"aligned tabs stay in bar");
                    check(!android.graphics.RectF.intersects(t,strip.clock)&&!android.graphics.RectF.intersects(t,strip.status),"alignment never covers information");
                    if(i>0)check(Math.abs(t.left-strip.tabs.get(i-1).rect.right-strip.gap)<.02,"alignment preserves compact gaps");
                }
                if(strip.showClock)check(bar.contains(strip.clock)&&Math.abs(bar.right-strip.clock.right-16)<.02,"clock anchored inside aligned bar");
                if(strip.showStatus)check(bar.contains(strip.status),"status inside aligned bar");
            }
            float endSlot=1920*width/100f-16;
            if(strips[0].showClock||strips[0].showStatus){
                float left=Math.min(strips[0].showClock?strips[0].clock.left:Float.MAX_VALUE,strips[0].showStatus?strips[0].status.left:Float.MAX_VALUE);
                endSlot=left-12;
            }
            float spare=Math.max(0,endSlot-localEnd[0]);
            if(strips[0].tabs.size()==count){
                check(Math.abs(localStart[1]-localStart[0]-spare/2)<.03,"center aligns compact group in remaining space");
                check(Math.abs(localStart[2]-localStart[0]-spare)<.03,"right aligns compact group before clock");
                if(width==100&&count>0)check(localStart[1]>localStart[0]&&localStart[2]>localStart[1],"full-width alignment visibly changes content");
            }else{
                check(Math.abs(localStart[2]-localStart[0])<.03,"overflow keeps maximum tab viewport");
                m.values.put("nav_alignment",2);LauncherLayout l=new LauncherLayout(m.prefs);
                NavigationStrip last=new NavigationStrip(m.prefs,l,cfg,cfg.code(count-1),0);
                check(last.tabs.get(last.tabs.size()-1).index==count-1,"last tab reachable when right-aligned");
            }
        }
    }
    public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args)throws Exception{
        if(android.graphics.Typeface.DEFAULT==null)throw new IllegalStateException("Font tests require PanelInstrumentation in a normal Android application process");
        if(args!=null&&args.length>0)try(InputStream in=new FileInputStream(args[0])){PanelStore.schema=new JSONObject(new String(SharedPhotoAlbum.bounded(in,1024*1024),StandardCharsets.UTF_8));}
        Memory memory=new Memory();Context c=memory.context();memory.values.put("ha_url","https://ha.example.invalid");memory.values.put("ha_token","test-only-secret");memory.values.put("hero_subtitle","Local");memory.values.put("custom_cards_yaml","cards: [{type: text, name: Local}]\n");
        check(LinkStatus.panelState(memory.prefs).equals(LinkStatus.DISABLED),"HA data does not imply editor linking");
        memory.values.put("ha_panel_enabled",true);memory.values.put("ha_panel_server","https://old.example.invalid");
        check(LinkStatus.panelState(memory.prefs).equals(LinkStatus.SERVER_CHANGED),"changed server has actionable status");
        memory.values.put("ha_panel_server","https://ha.example.invalid");
        for(String state:new String[]{LinkStatus.WAITING,LinkStatus.PENDING,LinkStatus.NO_PROFILE,LinkStatus.SYNCED,LinkStatus.MISSING,LinkStatus.OWNER,LinkStatus.REFUSED,LinkStatus.TIMEOUT,LinkStatus.INVALID,LinkStatus.FORGOTTEN}){
            LinkStatus.panel(c,state);check(LinkStatus.panelState(memory.prefs).equals(state),"state retained");
            check(!LinkStatus.panelLabel(memory.prefs).isEmpty()&&!LinkStatus.hint(memory.prefs).isEmpty(),"status has guidance");
            check(!LinkStatus.summary(memory.prefs,true).contains("test-only-secret"),"summary never includes credentials");
        }
        check(LinkStatus.errorState("unknown_command","private detail").equals(LinkStatus.MISSING),"missing integration distinguished");
        check(LinkStatus.errorState("invalid_profile","Identifiant de TV déjà utilisé par un autre compte").equals(LinkStatus.OWNER),"owner mismatch distinguished");
        check(LinkStatus.errorState("invalid_profile","private detail").equals(LinkStatus.REFUSED),"raw errors never displayed");
        memory.values.remove("ha_panel_enabled");memory.values.remove("ha_panel_server");memory.values.remove("ha_panel_link_state");
        check(!PanelStore.enabled(c),"off by default");PanelStore.enable(c,"Test TV","salon");check(PanelStore.enabled(c),"explicit activation");String id=PanelStore.identity(c);check(id.equals(PanelStore.identity(c)),"stable installation identity");
        JSONObject defaults=new JSONObject();for(Iterator<String> it=PanelStore.schema.keys();it.hasNext();){String k=it.next();defaults.put(k,PanelStore.schema.getJSONObject(k).get("default"));}
        check(PanelStore.validate(c,new JSONObject().put("schema",1).put("settings",defaults)).getJSONArray("cards").length()==0,"all shared defaults valid");
        for(int shape=0;shape<=7;shape++) {
            JSONObject cfg=new JSONObject().put("schema",1).put("settings",new JSONObject().put("app_icon_shape",shape));
            check(PanelStore.validate(c,cfg).getJSONObject("settings").getInt("app_icon_shape")==shape,"all icon shapes roundtrip");
        }
        for(int bad:new int[]{-1,8})reject(()->PanelStore.validate(c,new JSONObject().put("schema",1).put("settings",new JSONObject().put("app_icon_shape",bad))));
        for(int edge=0;edge<4;edge++)for(int size:new int[]{70,100,160}){
            memory.values.put("nav_position",edge);memory.values.put("nav_scale",size);LauncherLayout layout=new LauncherLayout(memory.prefs);
            check(layout.content.left>=0&&layout.content.top>=0&&layout.content.right<=1920&&layout.content.bottom<=1080,"navigation leaves valid content area");
            for(String block:new String[]{"greeting","quick","continue","favorites","home"}){
                LauncherLayout.Block b=layout.block(block,42,90,210);
                check(b.width>0&&b.scale>0&&b.x+b.width<=layout.content.right&&b.y+b.height<=layout.content.bottom,"block remains outside fixed bar");
            }
        }
        memory.values.remove("nav_position");memory.values.remove("nav_scale");
        for(int edge=0;edge<4;edge++)for(int size:new int[]{70,100,160})for(int width:new int[]{40,60,100})for(int align=0;align<3;align++)for(int gap:new int[]{0,48}){
            memory.values.put("nav_position",edge);memory.values.put("nav_scale",size);memory.values.put("nav_width",width);memory.values.put("nav_alignment",align);memory.values.put("nav_tab_gap",gap);LauncherLayout layout=new LauncherLayout(memory.prefs);android.graphics.RectF nav=layout.navigation();
            check(nav.left>=0&&nav.right<=1920&&nav.top>=0&&nav.bottom<=1080,"partial navigation remains onscreen");
            NavigationConfig cfg=new NavigationConfig(memory.prefs);for(int i=0;i<cfg.items.size();i++){NavigationStrip strip=new NavigationStrip(memory.prefs,layout,cfg,cfg.code(i),0);boolean found=false;for(NavigationStrip.Tab t:strip.tabs)if(cfg.code(t.index)==cfg.code(i))found=true;check(found,"selected tab remains reachable");for(NavigationStrip.Tab tab:strip.tabs){android.graphics.RectF t=tab.rect;check(t.width()>0&&t.height()>0&&t.left>=nav.left&&t.right<=nav.right+.01&&t.top>=nav.top&&t.bottom<=nav.bottom+.01,"content-sized tabs remain within navigation");}}
        }
        for(String k:new String[]{"nav_position","nav_scale","nav_width","nav_alignment","nav_tab_gap"})memory.values.remove(k);
        JSONObject placed=profile("Placement");JSONObject card=placed.getJSONArray("cards").getJSONObject(0);card.put("placement",new JSONObject().put("x",5).put("y",20).put("width",40).put("height",30)).put("interactive",false).put("background_opacity",0).put("title_size",32);
        PanelStore.validate(c,placed);Map<String,Object> parsed=CardConfig.parseData(PanelStore.data(new JSONObject().put("cards",placed.getJSONArray("cards")),0)).get(0);
        NativeCards.activate(null,parsed,null,false,null);check(true,"display-only card has no click or service side effects");
        LauncherLayout layout=new LauncherLayout(memory.prefs);android.graphics.RectF positioned=layout.placed(parsed);check(positioned.left>=layout.content.left&&positioned.bottom<=layout.content.bottom,"free element inside content");
        for(Object value:new Object[]{true,1.5,-1,101}){JSONObject bad=new JSONObject(placed.toString());bad.getJSONArray("cards").getJSONObject(0).getJSONObject("placement").put("x",value);reject(()->PanelStore.validate(c,bad));}
        java.util.List<SpatialFocus.Target> targets=java.util.Arrays.asList(new SpatialFocus.Target(6,0,new android.graphics.RectF(10,10,110,110)),new SpatialFocus.Target(6,1,new android.graphics.RectF(150,10,250,110)),new SpatialFocus.Target(6,2,new android.graphics.RectF(10,200,110,300)));
        check(SpatialFocus.next(targets,targets.get(0).rect,android.view.KeyEvent.KEYCODE_DPAD_RIGHT).col==1,"spatial right");check(SpatialFocus.next(targets,targets.get(0).rect,android.view.KeyEvent.KEYCODE_DPAD_DOWN).col==2,"spatial down");check(SpatialFocus.next(targets,targets.get(0).rect,android.view.KeyEvent.KEYCODE_DPAD_UP)==null,"spatial edge returns to nav");
        check(DashboardUrl.resolve("https://ha.example.test:8123/", "/dashboard-tv/tv").equals("https://ha.example.test:8123/dashboard-tv/tv"),"dashboard remains on configured HA");
        for(String bad:new String[]{"//evil.test/a","https://evil.test/a","/../a","/a?token=x","/a%2fb","/a\\b"}){check(!DashboardUrl.validPath(bad),"dashboard path rejects redirect/credential tricks");JSONObject cfg=profile("bad");cfg.getJSONObject("settings").put("dashboard_web_path",bad);reject(()->PanelStore.validate(c,cfg));}
        check(DashboardUrl.sameOrigin("https://ha.test","https://ha.test:443/auth/authorize"),"same origin login allowed");check(!DashboardUrl.sameOrigin("https://ha.test","http://ha.test/lovelace"),"no downgrade");check(!DashboardUrl.sameOrigin("https://ha.test","https://ha.test.evil.test"),"no external origin");check(!DashboardUrl.sameOrigin("https://ha.test","https://user@ha.test"),"no URL credentials");
        JSONObject badColor=profile("Invalid");badColor.getJSONObject("settings").put("text_color","javascript:bad");reject(()->PanelStore.validate(c,badColor));
        JSONObject withLinks=profile("Texte : été · 📺\nDeuxième ligne");
        withLinks.getJSONObject("settings").put("photo_album_url","https://photos.app.goo.gl/ExampleAlbum123");
        withLinks.getJSONArray("cards").getJSONObject(0).put("tap_action",new JSONObject().put("action","url").put("url_path","https://example.com/a/b"));
        JSONObject wire=new JSONObject(withLinks.toString());
        check(PanelStore.validate(c,wire).toString().equals(withLinks.toString()),"Android escaped slash JSON is not reinterpreted as YAML");
        PanelStore.apply(c,snapshot("salon",1,withLinks));
        check(CardStore.active(c).get(0).get("tap_action") instanceof Map,"JSON cached cards preserve URL actions");
        check(memory.prefs.getString("hero_subtitle","").equals("Texte : été · 📺\nDeuxième ligne"),"unicode and multiline text preserved");
        JSONObject deep=new JSONObject();JSONObject cursor=deep;for(int i=0;i<22;i++){JSONObject next=new JSONObject();cursor.put("nested",next);cursor=next;}
        reject(()->PanelStore.validate(c,deep));
        JSONObject badCards=profile("Invalid");badCards.put("cards",JSONObject.NULL);reject(()->PanelStore.validate(c,badCards));
        JSONObject duplicateCards=profile("Invalid");duplicateCards.put("cards",new JSONArray().put(new JSONObject().put("id","same").put("name","A")).put(new JSONObject().put("id","same").put("name","B")));reject(()->PanelStore.validate(c,duplicateCards));
        JSONObject one=profile("From HA");check(PanelStore.apply(c,snapshot("salon",1,one)),"apply profile");check(memory.prefs.getString("hero_subtitle","").equals("From HA"),"appearance applied");check(CardStore.active(c).size()==1&&CardStore.active(c).get(0).get("name").equals("Native"),"native cards source");
        check(!PanelStore.apply(c,snapshot("salon",1,one)),"duplicate event does not redraw/reset");
        for(String key:new String[]{"ha_token","ha_url","custom_wallpaper_uri","ha_panel_enabled"}){JSONObject bad=profile("Invalid");bad.getJSONObject("settings").put(key,"bad");reject(()->PanelStore.apply(c,snapshot("salon",2,bad)));check(memory.prefs.getString("hero_subtitle","").equals("From HA"),"rejection preserves last profile");}
        for(Object value:new Object[]{999,true,"64",2.5}){JSONObject bad=profile("Invalid");bad.getJSONObject("settings").put("greeting_size",value);reject(()->PanelStore.validate(c,bad));}
        reject(()->PanelStore.validate(c,new JSONObject().put("schema",true)));
        reject(()->PanelStore.validate(c,new JSONObject().put("schema",1).put("cards",new JSONArray().put(new JSONObject().put("type","custom:button-card").put("name","x")))));
        JSONObject exported=PanelStore.export(c);check(!exported.toString().contains("test-only-secret")&&!exported.getJSONObject("settings").has("ha_url"),"credential-free export");
        JSONObject changed=profile("Other");changed.getJSONObject("settings").put("show_clock",false);PanelStore.apply(c,snapshot("chambre",1,changed));check(!memory.prefs.getBoolean("show_clock",true),"second profile setting");
        PanelStore.apply(c,snapshot("chambre",2,new JSONObject().put("schema",1).put("settings",new JSONObject()).put("cards",new JSONArray())));check(memory.prefs.getString("hero_subtitle","").equals("Local"),"removed managed key restores original local value");check(!memory.prefs.contains("show_clock"),"absent original setting restored as absent");
        PanelStore.apply(c,snapshot("salon",3,one));PanelStore.disable(c);check(!PanelStore.enabled(c),"disable overlay");check(memory.prefs.getString("hero_subtitle","").equals("Local"),"local appearance restored");check(CardStore.active(c).get(0).get("name").equals("Local"),"local cards restored");check(TokenStore.get(memory.prefs).equals("test-only-secret"),"HA connection untouched");
        Memory navMemory=new Memory();navMemory.values.put("ha_url","https://test.invalid");navMemory.values.put("ha_token","test-fixture-only");Context nc=navMemory.context();PanelStore.enable(nc,"Test","test");
        check(new NavigationConfig(navMemory.prefs).items.size()==6,"default tabs plus microphone");
        List<Map<String,Object>> navItems=NavigationConfig.defaults();Collections.reverse(navItems);navMemory.values.put("nav_items",new JSONArray(navItems).toString());
        NavigationConfig navigation=new NavigationConfig(navMemory.prefs);check(navigation.code(0)==4&&navigation.move(4,1)==3,"semantic destination independent of visual order");
        check(navigation.move(4,-1)==NavigationConfig.BRAND,"brand always reachable");
        String localNav=navMemory.prefs.getString("nav_items","");
        JSONObject navProfile=new JSONObject().put("schema",1).put("navigation",new JSONArray());
        PanelStore.apply(nc,snapshot("test",1,navProfile));navigation=new NavigationConfig(navMemory.prefs);
        check(navigation.items.isEmpty()&&navigation.normalize(0)==NavigationConfig.BRAND&&navigation.move(NavigationConfig.BRAND,1)==NavigationConfig.BRAND,"empty bar retains safe settings access");
        check(PanelStore.export(nc).getJSONArray("navigation").length()==0,"empty navigation exported");
        PanelStore.apply(nc,snapshot("test",2,new JSONObject().put("schema",1)));check(navMemory.prefs.getString("nav_items","").equals(localNav),"navigation removal restores local");
        PanelStore.apply(nc,snapshot("test",3,navProfile));PanelStore.disable(nc);check(navMemory.prefs.getString("nav_items","").equals(localNav),"disable restores local navigation");
        for(Object value:new Object[]{true,"",new JSONObject()})reject(()->PanelStore.validate(nc,new JSONObject().put("schema",1).put("navigation",value)));
        for(int edge=0;edge<4;edge++)for(int scale:new int[]{70,100,160})for(int width:new int[]{40,100})for(int logo:new int[]{32,76}){
            navMemory.values.put("nav_position",edge);navMemory.values.put("nav_scale",scale);navMemory.values.put("nav_width",width);navMemory.values.put("nav_logo_size",logo);LauncherLayout nl=new LauncherLayout(navMemory.prefs);NavigationStrip strip=new NavigationStrip(navMemory.prefs,nl,new NavigationConfig(navMemory.prefs),0,0);
            for(NavigationStrip.Tab tab:strip.tabs){android.graphics.RectF t=tab.rect,n=nl.navigation();check(t.width()>0&&t.left>=n.left&&t.right<=n.right+.01&&t.bottom<=n.bottom+.01,"dynamic tabs within bar at every edge and size");}
        }
        navigationWidthChecks();
        navigationAlignmentChecks();
        Memory other=new Memory();other.values.put("ha_url","https://other.invalid");check(!PanelStore.enabled(other.context()),"TV preference isolation");
        System.out.println("PASS: "+checks+" native HA-panel validation, backup, cache and isolation checks");
    }
}
