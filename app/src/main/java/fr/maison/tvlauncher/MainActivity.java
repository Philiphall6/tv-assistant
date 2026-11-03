package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

public class MainActivity extends Activity {
    private LauncherView launcherView;
    boolean offerSetup;
    android.widget.FrameLayout root;
    HaCardSurface haCards;
    @Override public void onCreate(Bundle b){super.onCreate(b);offerSetup=LauncherSetupActivity.offerOnFirstLaunch(CardStore.prefs(this));requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);launcherView=new LauncherView(this);root=new android.widget.FrameLayout(this);root.addView(launcherView,new android.widget.FrameLayout.LayoutParams(-1,-1));haCards=new HaCardSurface(this,launcherView);root.addView(haCards,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(root);}
    @Override public void onResume(){super.onResume();if(offerSetup){offerSetup=false;startActivity(new Intent(this,LauncherSetupActivity.class));return;}if(haCards!=null)haCards.resume();if(launcherView!=null){launcherView.reload();String page=getIntent().getStringExtra("page");if(page!=null){launcherView.navigate(page);getIntent().removeExtra("page");}}}
    @Override public void onPause(){if(haCards!=null)haCards.pause();if(launcherView!=null)launcherView.pause();super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return haCards!=null&&haCards.key(e)||super.dispatchKeyEvent(e);}
    @Override public void onDestroy(){if(haCards!=null)haCards.destroy();super.onDestroy();}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    @Override public void onBackPressed(){if(launcherView!=null)launcherView.goBack();}

    static class AppEntry { String label,pkg,activity; AppIconRenderer icon; }
    static class EntityItem { String id,name,state,domain; Map<String,Object> custom; JSONObject haState; }
    static class LauncherView extends View {
        final MainActivity activity; final Paint p=new Paint(3); final Paint stroke=new Paint(3); final List<AppEntry> apps=new ArrayList<>(); final List<AppEntry> favoriteApps=new ArrayList<>(); final List<AppEntry> resumeApps=new ArrayList<>(); final List<EntityItem> homeEntities=new ArrayList<>();
        Bitmap hero,customWallpaper; Drawable haMark; BrandLogo brandLogo; String loadedWallpaperUri=""; int row=2,col=0,appOffset=0,homeOffset=0,appGridOffset=0,favoriteOffset=0; long lastClockUpdate,lastHomeRefresh; String time=""; boolean haOnline=false,appsPage=false,centerLongHandled=false,initialPageApplied=false; AppEntry pendingApp; int pendingAppRow=-1,pendingAppColumn=-1; AppActionsDialog appMenu; SharedPreferences prefs;
        HaLiveClient live;Map<String,JSONObject> states=new LinkedHashMap<>();List<Map<String,Object>> customCards=new ArrayList<>();
        boolean dashboardPage=false,active=false,remoteFetching=false;int dashboardOffset=0;String pendingCardId=null;
        final Runnable clockTick=this::tick;
        WallpaperSlideshow slideshow;

        LauncherView(MainActivity a){super(a);activity=a;setFocusable(true);setFocusableInTouchMode(true);hero=BitmapFactory.decodeResource(getResources(),R.drawable.living_room_hero);haMark=getResources().getDrawable(R.drawable.app_icon,a.getTheme());brandLogo=new BrandLogo(a);stroke.setStyle(Paint.Style.STROKE);prefs=a.getSharedPreferences(HaClient.PREFS,MODE_PRIVATE);if(!prefs.getBoolean("home_redesign_v2",false))prefs.edit().putBoolean("home_redesign_v2",true).putInt("wallpaper_mode",0).putInt("background_dim",8).apply();NativeCards.init(a);slideshow=new WallpaperSlideshow(this);}
        void tick(){if(!active)return;time=new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date());invalidate();postDelayed(clockTick,30000);}
        void pause(){active=false;if(slideshow!=null)slideshow.stop();removeCallbacks(clockTick);if(live!=null){live.stop();live=null;}pendingCardId=null;clearAppPress();dismissAppMenu();}
        void reload(){pause();active=true;prefs=activity.getSharedPreferences(HaClient.PREFS,MODE_PRIVATE);if(prefs.getInt("wallpaper_mode",0)==4)slideshow.start();loadCustomWallpaper();loadApps();customCards=CardStore.active(activity);loadEntities();haOnline=false;
            live=new HaLiveClient(activity,new HaLiveClient.Listener(){public void states(Map<String,JSONObject> next){states=next;loadEntities();}public void connection(boolean online){haOnline=online;invalidate();}public void configuration(JSONObject snapshot)throws Exception{applyPanel(snapshot);}public Bitmap preview(){return capturePreview();}});live.start();refreshRemote();tick();
            if(!initialPageApplied){initialPageApplied=true;if(prefs.getBoolean("start_dashboard",false)){dashboardPage=true;row=4;col=2;}else{appsPage=prefs.getBoolean("start_apps",false);if(appsPage){row=4;col=1;}}}normalizeSelection();requestFocus();invalidate();}
        void applyPanel(JSONObject snapshot)throws Exception{if(!active||!PanelStore.apply(activity,snapshot))return;pendingCardId=null;clearAppPress();dismissAppMenu();pendingPlacedIndex=-1;customCards=CardStore.active(activity);loadApps();loadEntities();loadCustomWallpaper();if(prefs.getInt("wallpaper_mode",0)==4)slideshow.start();else slideshow.stop();normalizeSelection();invalidate();}
        Bitmap capturePreview(){
            if(!active||!isShown()||!activity.hasWindowFocus()||getWidth()<1||getHeight()<1)throw new IllegalStateException("Launcher non visible");
            // Draw only our own visible launcher, never Android settings, video or another app.
            float scale=Math.min(1f,Math.min(1280f/getWidth(),720f/getHeight()));
            Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(getWidth()*scale)),Math.max(1,Math.round(getHeight()*scale)),Bitmap.Config.ARGB_8888);
            try{Canvas canvas=new Canvas(bitmap);canvas.scale(scale,scale);activity.root.draw(canvas);return bitmap;}catch(RuntimeException e){bitmap.recycle();throw e;}
        }
        void refreshRemote(){if(remoteFetching||PanelStore.enabled(activity)||!prefs.getBoolean("cards_remote_enabled",false))return;String url=prefs.getString("cards_remote_url","");remoteFetching=true;CardStore.fetch(activity,url,(ok,msg,raw)->activity.runOnUiThread(()->{remoteFetching=false;if(!active||PanelStore.enabled(activity)||!prefs.getBoolean("cards_remote_enabled",false)||!url.equals(prefs.getString("cards_remote_url","")))return;if(ok&&!raw.equals(prefs.getString("cards_remote_cache",""))){prefs.edit().putString("cards_remote_cache",raw).apply();customCards=CardStore.active(activity);loadEntities();}}));}
        void loadCustomWallpaper(){String uri=prefs.getString("custom_wallpaper_uri","");if(uri.equals(loadedWallpaperUri))return;loadedWallpaperUri=uri;customWallpaper=null;if(uri.isEmpty())return;try(InputStream in=activity.getContentResolver().openInputStream(android.net.Uri.parse(uri))){customWallpaper=BitmapFactory.decodeStream(in);}catch(Exception ignored){}}
        void loadApps(){
            stripCache=null;apps.clear();PackageManager pm=activity.getPackageManager();Set<String> seen=new HashSet<>();
            loadAppsForCategory(pm,Intent.CATEGORY_LEANBACK_LAUNCHER,seen);
            if(prefs.getBoolean("show_mobile_apps",true))loadAppsForCategory(pm,Intent.CATEGORY_LAUNCHER,seen);
            apps.sort((a,b)->a.label.compareToIgnoreCase(b.label));
            loadFavoriteApps();
            resumeApps.clear();
            if(row==0&&col>=apps.size())col=Math.max(0,apps.size()-1);appOffset=Math.min(appOffset,Math.max(0,apps.size()-1));
        }
        void loadFavoriteApps(){favoriteApps.clear();String saved=prefs.getString("favorite_apps_order","");if(!saved.isEmpty())for(String pkg:saved.split("\\n"))for(AppEntry a:apps)if(a.pkg.equals(pkg)){favoriteApps.add(a);break;}favoriteOffset=Math.min(favoriteOffset,Math.max(0,favoriteApps.size()));}
        boolean isFavorite(AppEntry a){for(AppEntry f:favoriteApps)if(f.pkg.equals(a.pkg))return true;return false;}
        void toggleFavorite(AppEntry a){boolean added=AppFavorites.toggle(prefs,a.pkg);loadFavoriteApps();normalizeSelection();Toast.makeText(activity,a.label+(added?" ajoutée aux favoris":" retirée des favoris"),Toast.LENGTH_SHORT).show();invalidate();}
        AppEntry selectedApp(){
            if(col<0)return null;
            if(appsPage&&row==0)return col<apps.size()?apps.get(col):null;
            if(row==5)return col<favoriteApps.size()?favoriteApps.get(col):null;
            if(row==3)return col<resumeApps.size()?resumeApps.get(col):null;
            if(row==6&&col<customCards.size()&&placedFocusable(customCards.get(col))&&"app".equals(CardConfig.str(customCards.get(col),"type","")))return placedApp(customCards.get(col));
            if(row==4&&col>=5){Map<String,Object> item=navigation().selected(col);if(item!=null&&"app".equals(item.get("type")))return placedApp(item);}
            return null;
        }
        void clearAppPress(){pendingApp=null;pendingAppRow=-1;pendingAppColumn=-1;}
        void dismissAppMenu(){if(appMenu!=null){appMenu.dismiss();appMenu=null;}}
        void showAppMenu(AppEntry app){
            if(app==null||activity.isFinishing()||appMenu!=null&&appMenu.isShowing())return;
            clearAppPress();pendingPlacedIndex=-1;centerLongHandled=true;
            appMenu=new AppActionsDialog(activity,app.label,isFavorite(app),AppActionsDialog.systemApp(activity,app.pkg),true,new AppActionsDialog.Actions(){
                public void favorite(){toggleFavorite(app);}
                public void information(){AppActionsDialog.open(activity,AppActionsDialog.informationIntent(app.pkg));}
                public void uninstall(){if(!AppActionsDialog.systemApp(activity,app.pkg))AppActionsDialog.open(activity,AppActionsDialog.uninstallIntent(app.pkg));}
            });
            appMenu.setOnDismissListener(d->{if(appMenu==d){appMenu=null;clearAppPress();requestFocus();invalidate();}});
            appMenu.show();
        }
        boolean appKeyDown(int key,KeyEvent event){
            if(!AppActionsDialog.isConfirm(key)){clearAppPress();return false;}
            AppEntry selected=selectedApp();
            if(selected==null)return false;
            if(event.getRepeatCount()==0){pendingApp=selected;pendingAppRow=row;pendingAppColumn=col;centerLongHandled=false;event.startTracking();}
            else if(pendingApp!=null&&!centerLongHandled&&(event.isLongPress()||event.getEventTime()-event.getDownTime()>=android.view.ViewConfiguration.getLongPressTimeout()))showAppMenu(pendingApp);
            return true;
        }
        boolean appKeyUp(int key,KeyEvent event){
            if(!AppActionsDialog.isConfirm(key)||pendingApp==null)return false;
            AppEntry app=pendingApp,selected=selectedApp();boolean same=row==pendingAppRow&&col==pendingAppColumn&&selected!=null&&app.pkg.equals(selected.pkg);
            clearAppPress();
            if(!event.isCanceled()&&!centerLongHandled&&same)launchApp(app);
            centerLongHandled=false;return true;
        }
        void loadAppsForCategory(PackageManager pm,String category,Set<String> seen){
            Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(category);
            for(ResolveInfo r:pm.queryIntentActivities(q,0)){
                if(r.activityInfo.packageName.equals(activity.getPackageName())||prefs.getStringSet("hidden_apps",java.util.Collections.emptySet()).contains(r.activityInfo.packageName))continue;
                String key=r.activityInfo.packageName;if(!seen.add(key))continue;
                AppEntry e=new AppEntry();e.label=r.loadLabel(pm).toString();e.pkg=r.activityInfo.packageName;e.activity=r.activityInfo.name;e.icon=new AppIconRenderer(r.loadIcon(pm));apps.add(e);
            }
        }
        void loadEntities(){
            String selected=row==1&&col<homeEntities.size()?homeEntities.get(col).id:null;
            homeEntities.clear();List<String> configured=new ArrayList<>();String ordered=prefs.getString("dashboard_entities_order","");
            if(prefs.contains("dashboard_entities_order")){for(String id:ordered.split("\\n"))if(!id.trim().isEmpty())configured.add(id.trim());}
            else{Set<String> saved=prefs.getStringSet("dashboard_entities",null);if(saved!=null)configured.addAll(saved);else{configured.add(prefs.getString("light_entity","light.salon"));configured.add(prefs.getString("scene_movie","scene.soiree_cinema"));configured.add(prefs.getString("scene_off","scene.tout_eteindre"));}}
            if(prefs.getBoolean("home_source_ha",false)){JSONObject group=states.get(prefs.getString("home_group_entity","group.tv_launcher").trim());JSONObject attrs=group==null?null:group.optJSONObject("attributes");JSONArray members=attrs==null?null:attrs.optJSONArray("entity_id");if(members!=null){configured.clear();for(int i=0;i<members.length();i++){String id=members.optString(i);if(!id.isEmpty())configured.add(id);}}}
            if(prefs.getBoolean("dashboard_include_entities",true))for(String id:configured){EntityItem e=new EntityItem();e.id=id;e.name=prettyEntityName(id);e.haState=states.get(id);e.state=e.haState==null?"Indisponible":e.haState.optString("state");JSONObject attrs=e.haState==null?null:e.haState.optJSONObject("attributes");if(attrs!=null)e.name=attrs.optString("friendly_name",e.name);e.domain=id.contains(".")?id.substring(0,id.indexOf('.')):"homeassistant";homeEntities.add(e);}
            int index=0;for(Map<String,Object> card:customCards){int slot=index++;if(!CardConfig.bool(card,"enabled",true)||card.containsKey("placement"))continue;EntityItem e=new EntityItem();e.custom=card;e.id="card:"+CardConfig.str(card,"id",String.valueOf(slot));String entity=CardConfig.str(card,"entity","");e.haState=HeatPumpState.isCard(card)?HeatPumpCard.displayState(card,states):states.get(entity);e.domain="custom";e.name=NativeCards.title(card,e.haState);e.state=NativeCards.stateText(e.haState);homeEntities.add(e);}
            if(selected!=null)for(int i=0;i<homeEntities.size();i++)if(selected.equals(homeEntities.get(i).id)){col=i;break;}
            homeOffset=Math.min(homeOffset,Math.max(0,homeEntities.size()-1));normalizeSelection();invalidate();
        }
        void testHa(){if(!HaClient.isConfigured(activity)){haOnline=false;invalidate();return;}HaClient.test(activity,(ok,msg)->activity.runOnUiThread(()->{haOnline=ok;invalidate();}));}
        int tint(String key,int fallback){return LauncherLayout.color(prefs,key,fallback);}
        void txt(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setShader(null);if(color==Color.WHITE)color=tint("text_color",color);else if(color==0xffb4b9bf||color==0xffb4c0ca||color==0xffa7abb3||color==0xffbac7d0||color==0xffc2cbd4||color==0xffaeb5bc)color=tint("secondary_text_color",color);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans",Typeface.BOLD):Typeface.create("sans",Typeface.NORMAL));c.drawText(s,x,y,p);}
        void rounded(Canvas c,float l,float t,float r,float b,float rad,int color){p.setShader(null);p.setColor(color);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
        void glass(Canvas c,float l,float t,float r,float b,float rad){rounded(c,l,t,r,b,rad,0xaa26303a);stroke.setColor(0x557e8994);stroke.setStrokeWidth(1.5f);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,stroke);}
        void centered(Canvas c,String s,float cx,float y,float size,int color,boolean bold){p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans",Typeface.BOLD):Typeface.create("sans",Typeface.NORMAL));txt(c,s,cx-p.measureText(s)/2,y,size,color,bold);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/1920f,getHeight()/1080f);float w=1920,h=1080;int wallpaper=prefs.getInt("wallpaper_mode",0);drawWallpaper(c,w,h,wallpaper);WallpaperBackdrop.overlays(c,w,h,wallpaper,prefs,p);
            LauncherLayout layout=new LauncherLayout(prefs);c.save();c.clipRect(layout.content);drawBody(c,layout);c.restore();drawFixedNavigation(c,layout);c.restore();removeCallbacks(syncHaSurface);post(syncHaSurface);}
        LauncherLayout.Block block(String key){LauncherLayout l=new LauncherLayout(prefs);switch(key){case "greeting":return l.block(key,9,90,130);case "quick":return l.block(key,26,50,66);case "continue":return l.block(key,35,90,rowStart()+110);case "favorites":return l.block(key,42,90,125+rowStart()+prefs.getInt("favorite_icon_size",59)*1.04f+prefs.getInt("app_label_size",15));default:return l.block("home",67,90,rowStart()+prefs.getInt("home_card_height",108));}}
        void beginBlock(Canvas c,LauncherLayout.Block b){c.save();c.translate(b.x,b.y);c.scale(b.scale,b.scale);c.clipRect(-5,-5,b.logicalWidth()+5,b.height/b.scale+5);}
        int rowStart(){return prefs.getInt("section_title_size",25)+25;}
        void sectionTitle(Canvas c,String title,float width){NativeCards.text(c,p,title,0,prefs.getInt("section_title_size",25),width,prefs.getInt("section_title_size",25),tint("section_color",Color.WHITE),true);}
        void drawBody(Canvas c,LauncherLayout l){
            if(appsPage||dashboardPage){c.save();c.translate(l.content.left,l.content.top-118);float w=l.content.width(),h=l.content.height()+118;if(appsPage)drawApplicationsPage(c,w*.045f,w,h);else drawDashboard(c,w*.045f,w,h);c.restore();return;}
            LauncherLayout.Block b=block("greeting");beginBlock(c,b);if(prefs.getBoolean("show_greeting",true))NativeCards.text(c,p,greeting(),0,prefs.getInt("greeting_size",64),b.logicalWidth(),prefs.getInt("greeting_size",64),tint("greeting_color",Color.WHITE),true);if(prefs.getBoolean("show_subtitle",true))NativeCards.text(c,p,prefs.getString("hero_subtitle","Votre salon, simplement."),0,prefs.getInt("greeting_size",64)+20+prefs.getInt("subtitle_size",22),b.logicalWidth(),prefs.getInt("subtitle_size",22),tint("subtitle_color",0xffd7d9dc),false);c.restore();
            if(prefs.getBoolean("show_quick",true)){b=block("quick");beginBlock(c,b);drawQuick(c,0,0,b.logicalWidth());c.restore();}
            if(!resumeApps.isEmpty()&&prefs.getBoolean("show_continue",true)){b=block("continue");beginBlock(c,b);sectionTitle(c,"Reprendre la lecture",b.logicalWidth());drawContinue(c,0,rowStart(),b.logicalWidth());c.restore();}
            if(prefs.getBoolean("show_favorites",true)){b=block("favorites");beginBlock(c,b);sectionTitle(c,"Vos applications",b.logicalWidth());drawFavorites(c,0,rowStart(),b.logicalWidth());c.restore();}
            if(prefs.getBoolean("show_home",true)){b=block("home");beginBlock(c,b);sectionTitle(c,prefs.getString("home_row_title","Votre maison"),b.logicalWidth());drawHome(c,0,rowStart(),b.logicalWidth());c.restore();}
            drawPlaced(c,l);
        }
        final Runnable syncHaSurface=this::syncHaSurface;
        void syncHaSurface(){
            if(activity.haCards==null||!active)return;
            JSONArray positions=new JSONArray();String selected="";
            LauncherLayout l=new LauncherLayout(prefs);
            if(!appsPage&&!dashboardPage){
                for(int i=0;i<customCards.size();i++){Map<String,Object> card=customCards.get(i);if(!placedVisible(card)||!HaCardSurface.isCard(card))continue;addHaPosition(positions,card,l.placed(card),l.content);if(row==6&&col==i)selected=CardConfig.str(card,"id","");}
            }
            if(!appsPage&&(dashboardPage||prefs.getBoolean("show_home",true))){
                int offset=dashboardPage?dashboardOffset:homeOffset,count=dashboardPage?dashboardColumns()*dashboardRows():homeVisibleCount();
                LauncherLayout.Block b=block("home");float pad=l.content.width()*.045f,gap=dashboardPage?20:14;
                int columns=dashboardPage?dashboardColumns():homeVisibleCount();
                float cw=dashboardPage?(l.content.width()-pad*2-gap*(columns-1))/columns:(b.logicalWidth()-gap*(columns-1))/columns;
                float ch=dashboardPage?Math.min(320,(l.content.height()+118-300)/dashboardRows()-16):prefs.getInt("home_card_height",108);
                for(int i=offset;i<Math.min(homeEntities.size(),offset+count);i++){
                    Map<String,Object> card=homeEntities.get(i).custom;if(!HaCardSurface.isCard(card))continue;int slot=i-offset;
                    float x=dashboardPage?l.content.left+pad+(slot%columns)*(cw+gap):b.x+slot*(cw+gap)*b.scale;
                    float y=dashboardPage?l.content.top-118+264+(slot/columns)*(ch+gap):b.y+rowStart()*b.scale;
                    float scale=dashboardPage?1:b.scale;addHaPosition(positions,card,new RectF(x,y,x+cw*scale,y+ch*scale),l.content);
                    if(row==1&&col==i)selected=CardConfig.str(card,"id","");
                }
            }
            activity.haCards.update(positions,selected);
        };
        void addHaPosition(JSONArray array,Map<String,Object> card,RectF bounds,RectF clip){
            RectF b=new RectF(bounds);if(!b.intersect(clip))return;
            try{array.put(new JSONObject().put("id",CardConfig.str(card,"id","")).put("x",b.left/19.2f).put("y",b.top/10.8f).put("width",b.width()/19.2f).put("height",b.height()/10.8f));}catch(Exception ignored){}
        }
        void drawHaPlaceholder(Canvas c,Map<String,Object> card,float x,float y,float w,float h,boolean selected){
            if(activity.haCards!=null&&activity.haCards.hasReadyCard())return;
            drawCard(c,x,y,w,h,selected,0xdd19232c);
            NativeCards.text(c,p,CardConfig.str(card,"name","Carte Home Assistant"),x+16,y+30,w-32,20,Color.WHITE,true);
            NativeCards.text(c,p,activity.haCards==null?"Chargement HA…":activity.haCards.message,x+16,y+57,w-32,15,0xffbccbd6,false);
        }
        int pendingPlacedIndex=-1;
        boolean placedVisible(Map<String,Object> card){return card.containsKey("placement")&&CardConfig.bool(card,"enabled",true);}
        AppEntry placedApp(Map<String,Object> card){String pkg=CardConfig.str(card,"package","");for(AppEntry a:apps)if(a.pkg.equals(pkg))return a;return null;}
        boolean placedFocusable(Map<String,Object> card){return placedVisible(card)&&CardConfig.bool(card,"interactive",true)&&(!"app".equals(CardConfig.str(card,"type",""))||placedApp(card)!=null);}
        JSONObject placedState(Map<String,Object> card){return HeatPumpState.isCard(card)?HeatPumpCard.displayState(card,states):states.get(CardConfig.str(card,"entity",""));}
        boolean hasPlaced(){for(Map<String,Object> card:customCards)if(placedVisible(card))return true;return false;}
        void drawPlaced(Canvas c,LauncherLayout l){
            for(int i=0;i<customCards.size();i++){Map<String,Object> card=customCards.get(i);if(!placedVisible(card))continue;RectF b=l.placed(card);boolean selected=row==6&&col==i&&placedFocusable(card);c.save();c.clipRect(b);
                if("app".equals(CardConfig.str(card,"type",""))){AppEntry a=placedApp(card);if(selected)drawCard(c,b.left+5,b.top+5,b.width()-10,b.height()-10,true,0);float radius=Math.min(b.width()*.38f,b.height()*.34f);if(a!=null)drawAppIcon(c,a,b.centerX(),b.top+radius+8,radius);NativeCards.text(c,p,a==null?"Application absente":CardConfig.str(card,"name",a.label),b.left+8,b.bottom-12,b.width()-16,prefs.getInt("app_label_size",15),tint("text_color",Color.WHITE),true);
                }else if(HaCardSurface.isCard(card)){drawHaPlaceholder(c,card,b.left,b.top,b.width(),b.height(),selected);}else{float fit=Math.min(1,Math.min(b.width()/200f,b.height()/100f));c.translate(b.left,b.top);c.scale(fit,fit);JSONObject state=placedState(card);NativeCards.draw(c,p,card,state,0,0,b.width()/fit,b.height()/fit,selected,haOnline);animateCard(card,state,0,0,b.width(),b.height());}
                c.restore();
            }
        }
        void activatePlaced(boolean hold){if(row!=6||col>=customCards.size())return;Map<String,Object> card=customCards.get(col);if(!placedFocusable(card))return;if(HaCardSurface.isCard(card)){activity.haCards.activate(card);return;}if("app".equals(CardConfig.str(card,"type",""))){if(!hold)launchApp(placedApp(card));return;}NativeCards.activate(activity,card,placedState(card),hold,this::navigate);}
        List<SpatialFocus.Target> homeTargets(){
            List<SpatialFocus.Target> targets=new ArrayList<>();LauncherLayout l=new LauncherLayout(prefs);
            for(int r:visibleRows()){if(r==4)continue;LauncherLayout.Block b=block(rowBlock(r));int offset=r==5?favoriteOffset:r==1?homeOffset:0,total=r==5?favoriteApps.size():r==1?homeEntities.size():r==3?resumeApps.size():2,count=r==5?favoriteVisibleCount():r==1?homeVisibleCount():r==3?4:2;
                float gap=14,cell=(b.logicalWidth()-gap*(count-1))/count,top=rowStart(),height=r==1?prefs.getInt("home_card_height",108):r==5?b.height/b.scale-top:106;
                if(r==5){cell=favoriteCellWidth();gap=0;}if(r==2){cell=205;gap=14;top=0;height=66;}
                float scale=b.scale*(r==2?Math.min(1,b.logicalWidth()/409f):1);
                for(int slot=0;slot<count&&offset+slot<total;slot++){int index=offset+slot;if(r==1&&homeEntities.get(index).custom!=null&&!CardConfig.bool(homeEntities.get(index).custom,"interactive",true))continue;targets.add(new SpatialFocus.Target(r,index,new RectF(b.x+slot*(cell+gap)*scale,b.y+top*scale,b.x+(slot*(cell+gap)+cell)*scale,b.y+(top+height)*scale)));}
            }
            for(int i=0;i<customCards.size();i++)if(placedFocusable(customCards.get(i)))targets.add(new SpatialFocus.Target(6,i,l.placed(customCards.get(i))));
            return targets;
        }
        void enterHome(){List<SpatialFocus.Target> targets=homeTargets();if(targets.isEmpty())return;LauncherLayout l=new LauncherLayout(prefs);int key=l.edge==0?KeyEvent.KEYCODE_DPAD_DOWN:l.edge==1?KeyEvent.KEYCODE_DPAD_UP:l.edge==2?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT;SpatialFocus.Target t=SpatialFocus.next(targets,navigationStrip(l).selectedRect(navigation(),col),key);if(t==null)t=targets.get(0);row=t.row;col=t.col;}
        boolean handleFreeNavigation(int key){
            if(appsPage||dashboardPage||!hasPlaced())return false;boolean arrow=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;if(!arrow)return false;
            LauncherLayout l=new LauncherLayout(prefs);int inward=l.edge==0?KeyEvent.KEYCODE_DPAD_DOWN:l.edge==1?KeyEvent.KEYCODE_DPAD_UP:l.edge==2?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT;
            if(row==4){if(key==inward){enterHome();invalidate();return true;}return false;}
            List<SpatialFocus.Target> targets=homeTargets();SpatialFocus.Target from=null;for(SpatialFocus.Target t:targets)if(t.row==row&&t.col==col)from=t;
            if(from==null){row=4;col=0;invalidate();return true;}
            SpatialFocus.Target next=SpatialFocus.next(targets,from.rect,key);
            if(next!=null){row=next.row;col=next.col;clampColumn();invalidate();return true;}
            int outward=l.edge==0?KeyEvent.KEYCODE_DPAD_UP:l.edge==1?KeyEvent.KEYCODE_DPAD_DOWN:l.edge==2?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
            if(key==outward){row=4;col=0;invalidate();return true;}
            // Preserve horizontal paging for legacy rows when they contain additional items.
            if(row!=6&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT))return false;
            return true;
        }
        int navOffset,stripSelection;String navSource;NavigationConfig navCache;NavigationStrip stripCache;
        NavigationStrip navigationStrip(LauncherLayout l){NavigationConfig nav=navigation();int selection=row==4?col:-1;if(stripCache==null||stripSelection!=selection){stripSelection=selection;stripCache=new NavigationStrip(prefs,l,nav,selection,navOffset);}return stripCache;}
        NavigationConfig navigation(){String source=prefs.getString("nav_items","");if(navCache==null||!source.equals(navSource)){navSource=source;navCache=new NavigationConfig(prefs);stripCache=null;}return navCache;}
        void drawBrandLogo(Canvas c,float x,float y,float size,int accent){
            brandLogo.draw(c,x,y,size,accent);
        }
        void drawFixedNavigation(Canvas c,LauncherLayout l){
            NavigationConfig nav=navigation();if(row==4)col=nav.normalize(col);NavigationStrip strip=navigationStrip(l);navOffset=strip.offset;float scale=l.navScale;int color=tint("nav_text_color",0xffe0e2e4);RectF n=l.navigation();
            if(prefs.getBoolean("nav_background",true)){int bg=tint("nav_background_color",0xff10171e);bg=Color.argb(prefs.getInt("nav_background_opacity",53)*255/100,Color.red(bg),Color.green(bg),Color.blue(bg));rounded(c,n.left,n.top,n.right,n.bottom,prefs.getInt("nav_corner_radius",0)*scale,bg);}
            RectF brand=strip.brand;if(row==4&&col==NavigationConfig.BRAND){rounded(c,brand.left,brand.top,brand.right,brand.bottom,20*scale,0xff263744);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*scale);p.setColor(0xff77dcff);c.drawRoundRect(brand,20*scale,20*scale,p);p.setStyle(Paint.Style.FILL);}
            drawBrandLogo(c,strip.logo.left,strip.logo.top,strip.logo.width(),tint("nav_logo_color",BrandLogo.BLUE));
            if(strip.brandText.width()>20*scale)NativeCards.text(c,p,NavigationStrip.BRAND_TEXT,strip.brandText.left,strip.logo.centerY()+7*scale,strip.brandText.width(),20*scale,Color.WHITE,true);
            for(NavigationStrip.Tab tab:strip.tabs){int i=tab.index;Map<String,Object> item=nav.items.get(i);RectF t=tab.rect;boolean selected=row==4&&col==nav.code(i);if(selected)rounded(c,t.left,t.top,t.right,t.bottom,22*scale,TvFocus.PAPER);
                int mode=NavigationStrip.mode(prefs,item);float fs=NavigationStrip.textSize(prefs,item)*scale,is=NavigationStrip.iconSize(prefs,item)*scale;
                int textColor=item.containsKey("color")?Color.parseColor((String)item.get("color")):color,iconColor=item.containsKey("icon_color")?Color.parseColor((String)item.get("icon_color")):tint("nav_icon_color",0xffe0e2e4);
                c.save();c.clipRect(t);float x=t.left+strip.padding;if(mode!=0){String glyph=NativeCards.glyphs==null?null:NativeCards.glyphs.get(CardConfig.str(item,"icon","mdi:home"));p.setTypeface(glyph!=null&&NativeCards.iconFont!=null?NativeCards.iconFont:Typeface.DEFAULT);p.setTextSize(is);p.setColor(selected?TvFocus.INK:iconColor);String draw=glyph==null?"◆":glyph;float ix=mode==1?t.centerX()-p.measureText(draw)/2:x;c.drawText(draw,ix,t.centerY()-(p.ascent()+p.descent())/2,p);x+=is+8*scale;}
                if(mode!=1)NativeCards.text(c,p,CardConfig.str(item,"label",""),x,t.centerY()+fs*.35f,t.right-x-strip.padding,fs,selected?TvFocus.INK:textColor,selected);c.restore();
            }
            if(nav.items.size()>strip.tabs.size()&&!strip.tabs.isEmpty())NativeCards.text(c,p,(navOffset>0?"‹ ":"")+(navOffset+1)+"–"+(navOffset+strip.tabs.size())+" / "+nav.items.size()+(navOffset+strip.tabs.size()<nav.items.size()?" ›":""),strip.area.left,strip.counterY,strip.area.width(),11*scale,color,false);
            if(strip.showStatus){int statusColor=haOnline?0xff69d39e:0xffd09b4c;if(strip.fullStatus)NativeCards.text(c,p,haOnline?"● Maison connectée":"● Mode local",strip.status.left,textBaseline(strip.status,strip.statusSize),strip.status.width(),strip.statusSize,statusColor,false);else{p.setColor(statusColor);c.drawCircle(strip.status.centerX(),strip.status.centerY(),4*scale,p);}}
            if(strip.showClock)NativeCards.text(c,p,time,strip.clock.left,textBaseline(strip.clock,strip.clockSize),strip.clock.width(),strip.clockSize,color,true);
        }
        void drawWallpaper(Canvas c,float w,float h,int mode){if(WallpaperBackdrop.drawSolid(c,w,h,mode,prefs,p))return;if(mode==4&&slideshow.draw(c,w,h))return;Bitmap image=mode==3?customWallpaper:(mode==0||mode==4)?hero:null;if(image!=null){int fit=prefs.getInt("wallpaper_fit",0);if(fit==2){c.drawBitmap(image,null,new RectF(0,0,w,h),p);return;}float iw=image.getWidth(),ih=image.getHeight(),scale=fit==1?Math.min(w/iw,h/ih):Math.max(w/iw,h/ih);float dw=iw*scale,dh=ih*scale;if(fit==1){p.setColor(0xff080b0f);c.drawRect(0,0,w,h,p);float l=(w-dw)/2,t=(h-dh)/2;c.drawBitmap(image,null,new RectF(l,t,l+dw,t+dh),p);return;}float srcW=w/scale,srcH=h/scale,fx=prefs.getInt("wallpaper_focus_x",50)/100f,fy=prefs.getInt("wallpaper_focus_y",50)/100f,left=(iw-srcW)*fx,top=(ih-srcH)*fy;left=Math.max(0,Math.min(iw-srcW,left));top=Math.max(0,Math.min(ih-srcH,top));c.drawBitmap(image,new Rect((int)left,(int)top,(int)(left+srcW),(int)(top+srcH)),new RectF(0,0,w,h),p);return;}p.setShader(new LinearGradient(0,0,w,h,mode==1?new int[]{0xff203541,0xff101a21,0xff080d11}:new int[]{0xff090b0e,0xff090b0e},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}
        float textBaseline(RectF bounds,float size){p.setTextSize(size);p.setTypeface(Typeface.DEFAULT);return bounds.centerY()-(p.ascent()+p.descent())/2;}
        String greeting(){return NativeCards.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));}
        String formattedDate(){String s=new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new Date());return s.substring(0,1).toUpperCase(Locale.FRANCE)+s.substring(1);}
        void drawHeader(Canvas c,float pad,float w){
            if(haMark!=null){haMark.setBounds((int)pad,31,(int)pad+64,95);haMark.draw(c);}
            txt(c,"TV Assistant",pad+78,75,24,Color.WHITE,true);
            String[] tabs={"Pour vous","Applications","Maison","⚙  Paramètres TV","⌂  Home Assistant"};float[] xs={pad+330,pad+465,pad+625,pad+760,pad+970};float[] widths={102,128,90,176,182};
            for(int i=0;i<tabs.length;i++){if(row==4&&col==i)rounded(c,xs[i]-15,35,xs[i]+widths[i]+6,94,29,0xfff1f2f4);txt(c,tabs[i],xs[i],73,19,row==4&&col==i?0xff17191d:(i==0?Color.WHITE:0xffc0c3c7),row==4&&col==i||i==0);}
            if(prefs.getBoolean("show_status",true)){int dot=haOnline?0xff69d39e:0xffd09b4c;rounded(c,w-315,35,w-126,94,29,0xaa171c22);rounded(c,w-290,58,w-278,70,6,dot);txt(c,haOnline?"Maison connectée":"Mode local",w-265,74,14,0xffe0e2e4,false);}if(prefs.getBoolean("show_clock",true))txt(c,time,w-101,76,20,Color.WHITE,true);
        }
        void drawQuick(Canvas c,float x,float y,float w){
            c.save();float fit=Math.min(1,w/409f);c.scale(fit,fit);
            boolean first=row==2&&col==0,second=row==2&&col==1;
            drawCard(c,x,y,205,66,first,first?TvFocus.PAPER:0xee202832);
            txt(c,"▶  Soirée cinéma",x+24,y+42,16,first?TvFocus.INK:Color.WHITE,true);
            drawCard(c,x+219,y,190,66,second,second?TvFocus.PAPER:0xee202832);
            txt(c,"○  Tout éteindre",x+242,y+42,15,second?TvFocus.INK:Color.WHITE,true);
            c.restore();
        }
        void drawHouseSummary(Canvas c,float l,float t,float r,float b){glass(c,l,t,r,b,9);txt(c,"MAISON",l+28,t+43,14,0xffffc75d,true);txt(c,haOnline?"Connectée":"Hors connexion",l+28,t+78,21,Color.WHITE,true);int count=Math.min(3,homeEntities.size());for(int i=0;i<count;i++){EntityItem e=homeEntities.get(i);float y=t+116+i*76;rounded(c,l+24,y-25,l+63,y+14,10,domainColor(e.domain)&0x77ffffff|0x22000000);txt(c,domainIcon(e.domain),l+37,y+2,17,domainColor(e.domain),true);txt(c,shorten(e.name,29),l+78,y-4,16,Color.WHITE,true);txt(c,friendlyState(e),l+78,y+19,13,0xffaeb5bc,false);if(i<count-1){p.setColor(0x334f5b64);c.drawRect(l+24,y+38,r-24,y+39,p);}}if(count==0)txt(c,"Ajoutez des appareils depuis Home Assistant",l+28,t+135,15,0xffaeb5bc,false);}
        void drawContinue(Canvas c,float pad,float y,float w){
            float gap=14,cw=(w-pad*2-gap*3)/4f,ch=106;
            for(int i=0;i<resumeApps.size();i++){
                AppEntry a=resumeApps.get(i);float x=pad+i*(cw+gap);drawCard(c,x,y,cw,ch,row==3&&col==i,0xee171a20);
                drawAppIcon(c,a,x+52,y+51,36);
                txt(c,shorten(a.label,22),x+105,y+41,16,Color.WHITE,true);txt(c,"Reprendre",x+105,y+63,12,0xffa8abb2,false);
                rounded(c,x+105,y+76,x+cw-18,y+81,3,0xff3a3d44);float progress=(.28f+i*.16f);rounded(c,x+105,y+76,x+105+(cw-123)*progress,y+81,3,0xfff3b75f);
            }
        }
        int appGridColumns(){return Math.max(3,Math.min(12,prefs.getInt("app_grid_columns",10)));}
        int appGridRows(){return Math.max(2,Math.min(6,prefs.getInt("app_grid_rows",5)));}
        void drawApplicationsPage(Canvas c,float pad,float w,float h){
            p.setShader(null);p.setColor(0xf20b1015);c.drawRect(0,118,w,h,p);txt(c,"Vos applications",pad,184,38,Color.WHITE,true);txt(c,apps.size()+" applications installées  ·  Maintenez OK pour les options",pad,222,18,0xffb4b9bf,false);
            int cols=appGridColumns();float gap=12,cellW=(w-pad*2-gap*(cols-1))/cols,cellH=Math.min(180,(h-270)/appGridRows()),top=248,r=Math.min(Math.min(53,prefs.getInt("icon_radius",62)),Math.min(cellW*.4f,cellH*.32f));int max=Math.min(apps.size(),appGridOffset+cols*appGridRows());
            for(int i=appGridOffset;i<max;i++){int slot=i-appGridOffset,gridRow=slot/cols,gridCol=slot%cols;float x=pad+gridCol*(cellW+gap),y=top+gridRow*cellH,cx=x+cellW/2,cy=y+58;boolean sel=row==0&&col==i;
                if(sel)rounded(c,x,y,x+cellW,y+cellH-8,prefs.getInt("card_roundness",18),0xfff1f2f4);AppEntry a=apps.get(i);cy=y+cellH*.4f;drawAppIcon(c,a,cx,cy,r);if(isFavorite(a)){rounded(c,cx+r-20,cy-r,cx+r+6,cy-r+26,13,0xffffc75d);txt(c,"★",cx+r-16,cy-r+20,17,0xff15171b,true);}if(prefs.getBoolean("show_app_labels",true))NativeCards.text(c,p,a.label,x+5,y+cellH-20,cellW-10,prefs.getInt("app_label_size",15),sel?TvFocus.INK:tint("text_color",Color.WHITE),true);
            }
            if(appGridOffset>0)txt(c,"▲  Applications précédentes",w-310,178,14,0xffa7abb3,false);if(max<apps.size())txt(c,"Applications suivantes  ▼",w-290,h-24,14,0xffa7abb3,false);
        }
        int favoriteVisibleCount(){return Math.max(1,(int)(block("favorites").logicalWidth()/favoriteCellWidth()));}
        float favoriteCellWidth(){return Math.max(prefs.getInt("favorite_icon_size",59)*2+24,prefs.getInt("favorite_cell_width",184));}
        void drawAppIcon(Canvas c,AppEntry app,float cx,float cy,float radius){
            app.icon.draw(c,cx,cy,radius,prefs.getInt("app_icon_shape",AppIconRenderer.ROUND));
        }
        void drawAppLabel(Canvas c,AppEntry app,float cx,float baseline,boolean selected){
            if(prefs.getBoolean("show_app_labels",true)){
                String label=shorten(app.label,18);int font=prefs.getInt("app_label_size",15);p.setTextSize(font);p.setTypeface(Typeface.create("sans",Typeface.BOLD));int count=p.breakText(label,true,favoriteCellWidth()-32,null);if(count<label.length())label=label.substring(0,Math.max(0,count-1))+"…";float tw=p.measureText(label);
                if(selected)rounded(c,cx-tw/2-14,baseline-font-6,cx+tw/2+14,baseline+9,16,TvFocus.PAPER);
                txt(c,label,cx-tw/2,baseline,font,selected?TvFocus.INK:Color.WHITE,true);
            }else if(selected){
                rounded(c,cx-20,baseline-7,cx+20,baseline-1,3,TvFocus.PAPER);
            }
        }
        void drawFavorites(Canvas c,float pad,float y,float w){
            float r=prefs.getInt("favorite_icon_size",59),cell=favoriteCellWidth();
            int total=favoriteApps.size(),visible=Math.min(favoriteVisibleCount(),Math.max(0,total-favoriteOffset));
            for(int slot=0;slot<visible;slot++){
                int i=favoriteOffset+slot;float cx=pad+slot*cell+r+8,cy=y+67;boolean sel=row==5&&col==i;
                AppEntry app=favoriteApps.get(i);
                drawAppIcon(c,app,cx,cy,sel?r*1.04f:r);
                drawAppLabel(c,app,cx,Math.max(y+151,cy+r*1.04f+33),sel);
            }
            if(favoriteOffset>0)txt(c,"‹",pad-20,y+78,34,Color.WHITE,true);
            if(favoriteOffset+visible<total)txt(c,"›",pad+visible*cell-4,y+78,34,Color.WHITE,true);
        }
        int visibleAppCount(){float pad=getWidth()*.045f,gap=prefs.getInt("app_gap",10),r=prefs.getInt("icon_radius",62),cw=r*2+36;return Math.max(1,(int)((getWidth()-pad*2+gap)/(cw+gap)));}
        void drawApps(Canvas c,float pad,float y,float w){
            float gap=prefs.getInt("app_gap",10),r=prefs.getInt("icon_radius",62),cw=r*2+36;int visible=Math.min(visibleAppCount(),Math.max(0,apps.size()-appOffset));
            for(int slot=0;slot<visible;slot++){
                int i=appOffset+slot;float x=pad+slot*(cw+gap),cx=x+cw/2,cy=y+68;boolean sel=row==0&&col==i;
                AppEntry app=apps.get(i);
                drawAppIcon(c,app,cx,cy,sel?r*1.04f:r);
                drawAppLabel(c,app,cx,y+r*2+36,sel);
            }
            if(appOffset>0)txt(c,"‹",pad-25,y+78,34,Color.WHITE,true);
            if(appOffset+visible<apps.size())txt(c,"›",pad+visible*(cw+gap)-5,y+78,34,Color.WHITE,true);
        }
        int homeVisibleCount(){return Math.max(3,Math.min(5,prefs.getInt("home_columns",4)));}
        int dashboardColumns(){return Math.max(2,Math.min(5,prefs.getInt("dashboard_columns",3)));}
        int dashboardRows(){return Math.max(1,Math.min(5,prefs.getInt("dashboard_rows",3)));}
        boolean webDashboard(){return prefs.getBoolean("dashboard_web_enabled",false);}
        void openWebDashboard(){activity.startActivity(new Intent(activity,HaDashboardActivity.class));}
        void drawDashboard(Canvas c,float pad,float w,float h){
            p.setColor(0xd90b1219);c.drawRect(0,118,w,h,p);NativeCards.text(c,p,prefs.getString("home_row_title","Votre maison"),pad,190,webDashboard()?w-pad*2-480:w-pad*2,38,tint("text_color",Color.WHITE),true);if(webDashboard()){float bw=Math.min(440,w*.48f),bx=w-pad-bw;drawCard(c,bx,145,bw,62,row==7,0xee19232c);NativeCards.text(c,p,"↗ "+prefs.getString("dashboard_web_title","Tableau de bord TV"),bx+18,185,bw-36,20,tint("text_color",Color.WHITE),true);}
            txt(c,haOnline?"En direct · OK pour ouvrir · Appui long pour les actions":"Hors ligne · dernières valeurs conservées",pad,228,18,0xffb4c0ca,false);
            int columns=dashboardColumns(),visible=columns*dashboardRows();float gap=20,cw=(w-pad*2-gap*(columns-1))/columns,ch=Math.min(320,(h-300)/dashboardRows()-16);
            if(homeEntities.isEmpty())txt(c,"Créez vos cartes dans Home Assistant → Cartes personnalisées et dashboard natif",pad,330,22,0xffc2cbd4,false);
            for(int i=dashboardOffset;i<Math.min(homeEntities.size(),dashboardOffset+visible);i++){int slot=i-dashboardOffset;EntityItem e=homeEntities.get(i);Map<String,Object> card=e.custom;if(card==null){card=new LinkedHashMap<>();card.put("name",e.name);card.put("entity",e.id);String icon=e.domain.equals("light")?"mdi:lightbulb":e.domain.equals("climate")?"mdi:thermostat":e.domain.equals("cover")?"mdi:window-shutter":e.domain.equals("sensor")?"mdi:gauge":e.domain.equals("scene")||e.domain.equals("script")?"mdi:play-circle":"mdi:home";card.put("icon",icon);}
                if(HaCardSurface.isCard(card))drawHaPlaceholder(c,card,pad+(slot%columns)*(cw+gap),264+(slot/columns)*(ch+gap),cw,ch,row==1&&col==i);else NativeCards.draw(c,p,card,e.haState,pad+(slot%columns)*(cw+gap),264+(slot/columns)*(ch+gap),cw,ch,row==1&&col==i,haOnline);
                animateCard(card,e.haState,pad+(slot%columns)*(cw+gap),264+(slot/columns)*(ch+gap),cw,ch);
            }
            if(homeEntities.size()>visible)txt(c,(Math.min(homeEntities.size(),dashboardOffset+1))+"–"+Math.min(homeEntities.size(),dashboardOffset+visible)+" / "+homeEntities.size(),w-pad-170,h-20,16,0xffbac7d0,false);
        }
        void drawHome(Canvas c,float pad,float y,float w){
            int count=homeVisibleCount();float gap=14,cw=(w-pad*2-gap*(count-1))/count;int total=homeEntities.size(),visible=Math.min(count,Math.max(0,total-homeOffset));
            int cardHeight=prefs.getInt("home_card_height",108),cardAlpha=prefs.getInt("home_card_opacity",90)*255/100,cardColor=(cardAlpha<<24)|0x001a2028;for(int slot=0;slot<visible;slot++){int i=homeOffset+slot;float x=pad+slot*(cw+gap);
                EntityItem item=homeEntities.get(i);if(item.custom!=null){if(HaCardSurface.isCard(item.custom))drawHaPlaceholder(c,item.custom,x,y,cw,cardHeight,row==1&&col==i);else NativeCards.draw(c,p,item.custom,item.haState,x,y,cw,cardHeight,row==1&&col==i,haOnline);animateCard(item.custom,item.haState,x,y,cw,cardHeight);continue;}
                drawCard(c,x,y,cw,cardHeight,row==1&&col==i,cardColor);
                String domain=homeEntities.get(i).domain;int accent=domainColor(domain);rounded(c,x+18,y+23,x+66,y+71,14,accent&0x55ffffff|0x22000000);txt(c,domainIcon(domain),x+33,y+55,21,accent,true);
                String title=homeEntities.get(i).name;String sub=friendlyState(homeEntities.get(i));NativeCards.text(c,p,title,x+82,y+(prefs.getBoolean("home_show_states",true)?45:59),cw-94,prefs.getInt("card_title_size",19),tint("card_text_color",Color.WHITE),true);if(prefs.getBoolean("home_show_states",true))NativeCards.text(c,p,sub,x+82,y+76,cw-94,prefs.getInt("card_state_size",14),tint("card_secondary_color",0xffa9afb7),false);
            }
            if(homeOffset>0)txt(c,"‹",pad-25,y+57,30,Color.WHITE,true);if(homeOffset+visible<total)txt(c,"›",pad+visible*(cw+gap)-5,y+57,30,Color.WHITE,true);
        }
        void animateCard(Map<String,Object> card,JSONObject state,float x,float y,float w,float h){if(active&&isShown()&&HeatPumpCard.animate(card,state,haOnline))postInvalidateDelayed(50);}
        int domainColor(String d){if(d.equals("light"))return 0xffffc35f;if(d.equals("switch")||d.equals("input_boolean"))return 0xff73d6a4;if(d.equals("cover"))return 0xffa48cff;if(d.equals("climate"))return 0xff6db9ff;if(d.equals("sensor")||d.equals("binary_sensor"))return 0xff6dcfe5;if(d.equals("scene")||d.equals("script"))return 0xfff3b75f;if(d.equals("media_player"))return 0xffff8175;if(d.equals("settings"))return 0xffaa96ff;return 0xffaeb2ba;}
        String domainIcon(String d){if(d.equals("light"))return "●";if(d.equals("switch")||d.equals("input_boolean"))return "◉";if(d.equals("cover"))return "▤";if(d.equals("climate"))return "⌁";if(d.equals("scene")||d.equals("script"))return "▶";if(d.equals("media_player"))return "♪";if(d.equals("settings"))return "⚙";return "•";}
        String friendlyState(EntityItem e){String s=e.state;if(s==null)return "Indisponible";if(s.equals("on"))return "Allumé";if(s.equals("off"))return "Éteint";if(s.equals("open"))return "Ouvert";if(s.equals("closed"))return "Fermé";if(s.equals("unavailable"))return "Indisponible";return s;}
        void drawCard(Canvas c,float x,float y,float w,float h,boolean selected,int color){
            int rad=prefs.getInt("card_roundness",20);float grow=selected?3:0;
            if(selected&&row!=2)color=TvFocus.ACTIVE_SURFACE;
            rounded(c,x-grow,y-grow,x+w+grow,y+h+grow,rad,color);
            if(selected){RectF bounds=new RectF(x-grow,y-grow,x+w+grow,y+h+grow);
                stroke.setColor(TvFocus.INK);stroke.setStrokeWidth(10);c.drawRoundRect(bounds,rad,rad,stroke);
                stroke.setColor(TvFocus.ACCENT);stroke.setStrokeWidth(Math.max(3,prefs.getInt("focus_width",4)));
                c.drawRoundRect(bounds,rad,rad,stroke);
            }
        }
        String shorten(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
        String prettyEntityName(String id){String raw=id.contains(".")?id.substring(id.indexOf('.')+1):id;raw=raw.replace('_',' ');if(raw.isEmpty())return "Maison";return raw.substring(0,1).toUpperCase(Locale.FRANCE)+raw.substring(1);}
        @Override public boolean onKeyDown(int key,KeyEvent e){
            if(!AppActionsDialog.isConfirm(key))clearAppPress();
            if(key==KeyEvent.KEYCODE_MENU){openSettings();return true;}
            if(key==KeyEvent.KEYCODE_SETTINGS){openTvSettings();return true;}
            if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_PAGE_UP){pageSelection(-1);return true;}
            if(key==KeyEvent.KEYCODE_CHANNEL_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN){pageSelection(1);return true;}
            if(key==KeyEvent.KEYCODE_INFO){showSelectionInfo();return true;}
            if(key==KeyEvent.KEYCODE_TV||key==KeyEvent.KEYCODE_TV_INPUT){launchNamedApp("TV","Télévision","Live TV");return true;}
            if(key==KeyEvent.KEYCODE_GUIDE||key==KeyEvent.KEYCODE_DVR||key==KeyEvent.KEYCODE_APP_SWITCH||key==256){showAllApps();return true;}
            if(handleFreeNavigation(key))return true;
            if(handleFixedNavigation(key))return true;
            if(appKeyDown(key,e))return true;
            if(row==6&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(e.getRepeatCount()==0){pendingPlacedIndex=col;centerLongHandled=false;e.startTracking();}return true;}
            if(row==1&&col<homeEntities.size()&&homeEntities.get(col).custom!=null&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(e.getRepeatCount()==0){pendingCardId=homeEntities.get(col).id;centerLongHandled=false;e.startTracking();}if(e.isLongPress()&&!centerLongHandled){centerLongHandled=true;customAction(homeEntities.get(col),true);}return true;}
            if(dashboardPage&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){handleDashboardKey(key);return true;}
            if(appsPage&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){handleAppsPageKey(key);return true;}
            if(appsPage)return super.onKeyDown(key,e);
            if(key==KeyEvent.KEYCODE_DPAD_UP){moveVertical(-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){moveVertical(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){col=Math.max(0,col-1);updatePageFromHeader();ensureHomeVisible();ensureFavoriteVisible();invalidate();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){col=Math.min(rowMax(),col+1);updatePageFromHeader();ensureHomeVisible();ensureFavoriteVisible();invalidate();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){activate();return true;}return super.onKeyDown(key,e);
        }
        @Override public boolean onKeyLongPress(int key,KeyEvent e){if(AppActionsDialog.isConfirm(key)&&pendingApp!=null){if(!centerLongHandled)showAppMenu(pendingApp);return true;}if(pendingPlacedIndex>=0&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(!centerLongHandled){centerLongHandled=true;activatePlaced(true);}return true;}if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&pendingCardId!=null&&row==1&&col<homeEntities.size()&&pendingCardId.equals(homeEntities.get(col).id)){if(!centerLongHandled){centerLongHandled=true;customAction(homeEntities.get(col),true);}return true;}return super.onKeyLongPress(key,e);}
        @Override public boolean onKeyUp(int key,KeyEvent e){if(appKeyUp(key,e))return true;if(pendingPlacedIndex>=0&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){int pending=pendingPlacedIndex;pendingPlacedIndex=-1;if(!e.isCanceled()&&!centerLongHandled&&row==6&&col==pending)activatePlaced(false);centerLongHandled=false;return true;}if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&pendingCardId!=null){String id=pendingCardId;pendingCardId=null;if(!e.isCanceled()&&!centerLongHandled&&row==1&&col<homeEntities.size()&&id.equals(homeEntities.get(col).id))customAction(homeEntities.get(col),false);centerLongHandled=false;return true;}return super.onKeyUp(key,e);}
        void handleAppsPageKey(int key){int cols=appGridColumns();if(key==KeyEvent.KEYCODE_DPAD_UP){if(row==4)return;if(col-cols>=0){col-=cols;ensureAppGridVisible();}else{row=4;col=1;}}else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(row==4){row=0;col=Math.min(appGridOffset,Math.max(0,apps.size()-1));}else if(col+cols<apps.size()){col+=cols;ensureAppGridVisible();}}else if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(row==4){col=Math.max(0,col-1);updatePageFromHeader();}else if(col>0){col--;ensureAppGridVisible();}}else if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(row==4){col=Math.min(4,col+1);updatePageFromHeader();}else if(col+1<apps.size()){col++;ensureAppGridVisible();}}else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)activate();invalidate();}
        void handleDashboardKey(int key){int columns=dashboardColumns();if(row==7){if(key==KeyEvent.KEYCODE_DPAD_UP){row=4;col=2;}else if(key==KeyEvent.KEYCODE_DPAD_DOWN&&!homeEntities.isEmpty()){row=1;col=dashboardOffset;}else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)openWebDashboard();invalidate();return;}if(row==4){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(!homeEntities.isEmpty()){row=1;col=Math.min(dashboardOffset,homeEntities.size()-1);}}else if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){col=Math.max(0,Math.min(4,col+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));updatePageFromHeader();}else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)activate();}else{if(key==KeyEvent.KEYCODE_DPAD_UP){if(col<columns){row=webDashboard()?7:4;col=webDashboard()?0:2;}else col-=columns;}else if(key==KeyEvent.KEYCODE_DPAD_DOWN)col=Math.min(rowMax(),col+columns);else if(key==KeyEvent.KEYCODE_DPAD_LEFT)col=Math.max(0,col-1);else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)col=Math.min(rowMax(),col+1);else activate();}ensureDashboardVisible();invalidate();}
        void ensureDashboardVisible(){if(!dashboardPage||row!=1)return;int columns=dashboardColumns();dashboardOffset=Math.max(0,(dashboardOffset/columns)*columns);if(col<dashboardOffset)dashboardOffset=(col/columns)*columns;else if(col>=dashboardOffset+columns*dashboardRows())dashboardOffset=(col/columns-dashboardRows()+1)*columns;}
        void updatePageFromHeader(){if(row==4){col=navigation().normalize(col);boolean entering=col==2&&!dashboardPage;appsPage=col==1;dashboardPage=col==2;if(entering)refreshRemote();}}
        void navigate(String page){appsPage=page.equals("applications");dashboardPage=page.equals("dashboard");row=4;col=appsPage?1:dashboardPage?2:0;if(dashboardPage)refreshRemote();invalidate();}
        void customAction(EntityItem e,boolean hold){if(HaCardSurface.isCard(e.custom)){activity.haCards.activate(e.custom);return;}NativeCards.activate(activity,e.custom,e.haState,hold,this::navigate);}
        void ensureAppGridVisible(){int page=appGridColumns()*appGridRows();if(col<appGridOffset)appGridOffset=Math.max(0,(col/appGridColumns()-appGridRows()+1)*appGridColumns());else if(col>=appGridOffset+page)appGridOffset=(col/appGridColumns()-appGridRows()+1)*appGridColumns();}
        int rowMax(){return row==7?0:row==6?Math.max(0,customCards.size()-1):row==0?Math.max(0,apps.size()-1):row==1?Math.max(0,homeEntities.size()-1):row==3?Math.max(0,resumeApps.size()-1):row==4?4:row==5?Math.max(0,favoriteApps.size()-1):1;}
        String rowBlock(int r){return r==2?"quick":r==3?"continue":r==5?"favorites":"home";}
        List<Integer> visibleRows(){List<Integer> rows=new ArrayList<>();if(prefs.getBoolean("show_quick",true))rows.add(2);if(prefs.getBoolean("show_continue",true)&&!resumeApps.isEmpty())rows.add(3);if(prefs.getBoolean("show_favorites",true)&&!favoriteApps.isEmpty())rows.add(5);if(prefs.getBoolean("show_home",true)&&!homeEntities.isEmpty())rows.add(1);rows.sort((a,b)->Float.compare(block(rowBlock(a)).y,block(rowBlock(b)).y));if(prefs.getInt("nav_position",0)==1)rows.add(4);else rows.add(0,4);return rows;}
        void normalizeSelection(){if(!appsPage&&!dashboardPage&&row==6&&col<customCards.size()&&placedFocusable(customCards.get(col))){invalidate();return;}if(dashboardPage){if(row!=1&&row!=4&&!(row==7&&webDashboard())||homeEntities.isEmpty()&&row==1){row=4;col=2;}}else if(!appsPage&&!visibleRows().contains(row)){row=4;col=0;}clampColumn();}
        boolean handleFixedNavigation(int key){
            int edge=prefs.getInt("nav_position",0);boolean vertical=edge>=2;boolean arrow=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if(!arrow)return false;
            if(row==4){
                if(vertical&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)||!vertical&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){col=navigation().move(navigation().normalize(col),(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT)?-1:1);updatePageFromHeader();invalidate();return true;}
                boolean enter=edge==0&&key==KeyEvent.KEYCODE_DPAD_DOWN||edge==1&&key==KeyEvent.KEYCODE_DPAD_UP||edge==2&&key==KeyEvent.KEYCODE_DPAD_RIGHT||edge==3&&key==KeyEvent.KEYCODE_DPAD_LEFT;
                if(enter){if(appsPage&&!apps.isEmpty()){row=0;col=appGridOffset;}else if(dashboardPage&&webDashboard()){row=7;col=0;}else if(dashboardPage&&!homeEntities.isEmpty()){row=1;col=dashboardOffset;}else if(!appsPage&&!dashboardPage){List<Integer> rows=visibleRows();rows.remove(Integer.valueOf(4));if(!rows.isEmpty()){row=rows.get(edge==1?rows.size()-1:0);col=0;}}clampColumn();invalidate();}return true;
            }
            boolean toSide=edge==2&&key==KeyEvent.KEYCODE_DPAD_LEFT&&(col==0||appsPage&&col%appGridColumns()==0||dashboardPage&&col%dashboardColumns()==0)||edge==3&&key==KeyEvent.KEYCODE_DPAD_RIGHT&&(col==rowMax()||appsPage&&(col+1)%appGridColumns()==0||dashboardPage&&(col+1)%dashboardColumns()==0);
            boolean toBottom=edge==1&&key==KeyEvent.KEYCODE_DPAD_DOWN&&(appsPage&&col+appGridColumns()>=apps.size()||dashboardPage&&col+dashboardColumns()>=homeEntities.size());
            if(toSide||toBottom){row=4;col=appsPage?1:dashboardPage?2:0;invalidate();return true;}
            return false;
        }
        void moveVertical(int direction){List<Integer> rows=visibleRows();int index=rows.indexOf(row);if(index<0)index=1;row=rows.get(Math.max(0,Math.min(rows.size()-1,index+direction)));clampColumn();invalidate();}
        void clampColumn(){col=row==4?navigation().normalize(col):Math.max(0,Math.min(col,rowMax()));ensureHomeVisible();ensureFavoriteVisible();ensureDashboardVisible();if(appsPage&&row==0)ensureAppGridVisible();}
        void ensureAppVisible(){if(row!=0)return;int visible=visibleAppCount();if(col<appOffset)appOffset=col;else if(col>=appOffset+visible)appOffset=col-visible+1;}
        void ensureHomeVisible(){if(row!=1)return;int visible=homeVisibleCount();if(col<homeOffset)homeOffset=col;else if(col>=homeOffset+visible)homeOffset=col-visible+1;}
        void ensureFavoriteVisible(){if(row!=5)return;int visible=favoriteVisibleCount();if(col<favoriteOffset)favoriteOffset=col;else if(col>=favoriteOffset+visible)favoriteOffset=col-visible+1;}
        void goBack(){appsPage=false;dashboardPage=false;row=4;col=0;invalidate();}
        void showAllApps(){appsPage=true;dashboardPage=false;row=0;col=Math.min(appGridOffset,Math.max(0,apps.size()-1));invalidate();}
        void openTvSettings(){try{activity.startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){Toast.makeText(activity,"Paramètres TV indisponibles",Toast.LENGTH_SHORT).show();}}
        void pageSelection(int direction){
            if(appsPage){int page=appGridColumns()*appGridRows();col=Math.max(0,Math.min(Math.max(0,apps.size()-1),col+direction*page));appGridOffset=(col/appGridColumns()/appGridRows())*page;row=0;}
            else if(dashboardPage){row=1;col=Math.max(0,Math.min(rowMax(),col+direction*dashboardColumns()*3));ensureDashboardVisible();}
            else if(row==5){col=Math.max(0,Math.min(rowMax(),col+direction*favoriteVisibleCount()));ensureFavoriteVisible();}
            else if(row==1){col=Math.max(0,Math.min(rowMax(),col+direction*homeVisibleCount()));ensureHomeVisible();}
            else if(direction>0){showAllApps();return;}else{row=4;col=0;}
            invalidate();
        }
        void showSelectionInfo(){
            String message="TV Assistant";
            if(appsPage&&row==0&&col<apps.size()){AppEntry a=apps.get(col);message=a.label+(isFavorite(a)?" · Favori":" · Maintenez OK pour les options");}
            else if(row==5&&col<favoriteApps.size())message=favoriteApps.get(col).label+" · Favori";
            else if(row==1&&col<homeEntities.size()){EntityItem e=homeEntities.get(col);message=e.name+" : "+friendlyState(e);}
            else if(row==4)message=col==0?"Accueil":col==1?apps.size()+" applications installées":col==2?"Dashboard Maison":col==3?"Paramètres de la télévision":"Paramètres TV Assistant";
            Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
        }
        void launchNamedApp(String... names){
            for(String name:names)for(AppEntry a:apps)if(a.label.equalsIgnoreCase(name)){launchApp(a);return;}
            for(String name:names)for(AppEntry a:apps)if(a.label.toLowerCase(Locale.FRANCE).contains(name.toLowerCase(Locale.FRANCE))){launchApp(a);return;}
            Toast.makeText(activity,"Application TV introuvable",Toast.LENGTH_SHORT).show();
        }
        void activate(){if(row==7){openWebDashboard();return;}if(row==6){activatePlaced(false);return;}if(row==4){col=navigation().normalize(col);if(col==NavigationConfig.BRAND){openSettings();return;}if(col==NavigationConfig.VOICE){activity.startActivity(new Intent(activity,VoiceSearchActivity.class));return;}if(col>=5){Map<String,Object> item=navigation().selected(col);if(item!=null){if("app".equals(item.get("type"))){String pkg=CardConfig.str(item,"package","");Intent launch=activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=activity.getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null){try{activity.startActivity(launch);}catch(Exception e){Toast.makeText(activity,"Application indisponible",Toast.LENGTH_SHORT).show();}}else Toast.makeText(activity,"Application absente de cette TV",Toast.LENGTH_SHORT).show();}else if("ha_dashboard".equals(item.get("type")))activity.startActivity(new Intent(activity,HaDashboardActivity.class).putExtra("dashboard_path",CardConfig.str(item,"path","")));}return;}if(col==0){appsPage=false;dashboardPage=false;row=4;col=0;enterHome();invalidate();}else if(col==1){appsPage=true;dashboardPage=false;row=0;col=0;appGridOffset=0;invalidate();}else if(col==2){dashboardPage=true;appsPage=false;if(webDashboard()){row=7;col=0;}else if(!homeEntities.isEmpty()){row=1;col=Math.min(dashboardOffset,homeEntities.size()-1);}refreshRemote();invalidate();}else if(col==3)openTvSettings();else if(col==4)openSettings();return;}if(row==5){if(col<favoriteApps.size())launchApp(favoriteApps.get(col));return;}if(row==0&&appsPage){if(col<apps.size())launchApp(apps.get(col));return;}if(row==3){if(col<resumeApps.size())launchApp(resumeApps.get(col));return;}if(row==2){if(col==0)scene("scene_movie","Scène cinéma lancée");else scene("scene_off","Maison éteinte");return;}if(row==1){if(col<homeEntities.size()){EntityItem e=homeEntities.get(col);if(e.custom!=null)customAction(e,false);else activateEntity(e);}return;}}
        void activateEntity(EntityItem e){String action;if(e.domain.equals("scene")||e.domain.equals("script"))action="turn_on";else if(e.domain.equals("button")||e.domain.equals("input_button"))action="press";else if(e.domain.equals("media_player"))action="media_play_pause";else if(e.domain.equals("sensor")||e.domain.equals("binary_sensor")){Toast.makeText(activity,e.name+" : "+friendlyState(e),Toast.LENGTH_SHORT).show();return;}else action="toggle";HaClient.callService(activity,e.domain,action,e.id,(ok,msg)->activity.runOnUiThread(()->{Toast.makeText(activity,ok?e.name+" commandé":msg,Toast.LENGTH_LONG).show();if(ok)loadEntities();}));}
        void launchApp(AppEntry a){Intent i=new Intent();i.setClassName(a.pkg,a.activity);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{activity.startActivity(i);}catch(Exception ex){Toast.makeText(activity,"Impossible d’ouvrir "+a.label,Toast.LENGTH_SHORT).show();}}
        void scene(String key,String ok){service("scene","turn_on",prefs.getString(key,key.equals("scene_movie")?"scene.soiree_cinema":"scene.tout_eteindre"),ok);}
        void service(String domain,String action,String entity,String ok){HaClient.callService(activity,domain,action,entity,(success,msg)->activity.runOnUiThread(()->Toast.makeText(activity,success?ok:msg,Toast.LENGTH_LONG).show()));}
        void openSettings(){activity.startActivity(new Intent(activity,LauncherSettingsActivity.class));}
    }
}
