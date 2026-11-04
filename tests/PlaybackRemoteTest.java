package fr.maison.tvlauncher;

import android.content.Intent;
import android.database.MatrixCursor;
import android.view.KeyEvent;
import java.util.List;

/** Synthetic provider data only: no playback, TV command or personal history. */
final class PlaybackRemoteTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void row(MatrixCursor c,long id,String pkg,String title,String uri,int type,long engaged,long position,long duration,int browsable){
        c.addRow(new Object[]{id,pkg,title,uri,type,engaged,position,duration,browsable,"",""});
    }
    static void run() throws Exception {
        check(ContinueArtwork.checked("https://images.example.invalid/poster.jpg","org.example.player")!=null,"HTTPS poster accepted");
        check(ContinueArtwork.checked("content://org.example.player/poster/1","org.example.player")!=null,"provider poster accepted");
        for(String uri:new String[]{"file:///secret","http://images.example.invalid/a","https://u:p@example.invalid/a","javascript:1","android.resource://org.other.player/drawable/secret"}){
            boolean rejected=false;try{ContinueArtwork.checked(uri,"org.example.player");}catch(java.io.IOException expected){rejected=true;}check(rejected,"unsafe artwork URI rejected");
        }
        android.graphics.Bitmap original=android.graphics.Bitmap.createBitmap(1600,900,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();original.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png);original.recycle();
        android.graphics.Bitmap small=ContinueArtwork.decode(png.toByteArray());
        check(small!=null&&small.getWidth()<=640&&small.getHeight()<=360,"bounded thumbnail decoding");small.recycle();
        check(ContinueArtwork.decode(new byte[]{1,2,3})==null,"invalid image falls back to app icon");
        android.graphics.Rect crop=ContinueArtwork.crop(800,1200,2.5f);
        check(crop.width()==800&&crop.height()==320&&crop.top==440,"portrait artwork centered without stretching");
        PanelRuntimeTest.Memory permission=new PanelRuntimeTest.Memory();
        check(PlaybackPermission.offer(permission.prefs,false,true),"permission offered when needed");
        check(!PlaybackPermission.offer(permission.prefs,true,true),"no prompt after grant");
        check(!PlaybackPermission.offer(permission.prefs,false,false),"no prompt without TV provider");
        permission.values.put(PlaybackPermission.ASKED,true);
        check(!PlaybackPermission.offer(permission.prefs,false,true),"respect refusal without repeated prompts");
        permission.values.clear();permission.values.put("show_continue",false);
        check(!PlaybackPermission.offer(permission.prefs,false,true),"hidden row never requests playback permission");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_NETFLIX").equals("netflix"),"TCL Netflix");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_FORCE_NETFLIX").equals("netflix"),"TCL forced Netflix");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_FORCE_AMAZON").equals("prime"),"TCL Amazon");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_PRIMEVIDEO").equals("prime"),"TCL Prime");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_YOUTUBE").equals("youtube"),"TCL YouTube");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_CHANNELPLUS").equals("tcl"),"TCL Channel");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_LIST").equals("tv"),"List returns to television");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_QUICK_SETTINGS").equals("settings"),"TCL settings");
        check(RemoteShortcuts.vendorAction("KEYCODE_TCL_NETFLIX_PLAYPAUSE").isEmpty(),"do not repurpose media controls");
        check(RemoteShortcuts.vendorAction("KEYCODE_VIDEO_APP_1").isEmpty(),"standard vendor-neutral button has no guessed brand");
        for(int key:new int[]{KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_POWER,KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_UNKNOWN})check(!RemoteShortcuts.assignable(key),"essential controls protected");
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();
        m.values.put(RemoteShortcuts.PREFIX+KeyEvent.KEYCODE_F1,"org.example.player");
        check(RemoteShortcuts.action(m.prefs,KeyEvent.KEYCODE_F1).equals("app:org.example.player"),"learned app selected");
        m.values.put(RemoteShortcuts.PREFIX+KeyEvent.KEYCODE_HOME,"org.example.player");
        check(RemoteShortcuts.action(m.prefs,KeyEvent.KEYCODE_HOME).isEmpty(),"even injected Home mapping ignored");
        check(RemoteShortcuts.packages("netflix")[0].equals("com.netflix.ninja"),"exact Netflix package");
        check(RemoteShortcuts.packages("prime")[0].equals("com.amazon.amazonvideo.livingroom"),"exact Prime package");
        check(RemoteShortcuts.shouldLaunch(AppActionsTest.event(0,KeyEvent.KEYCODE_F1,0,0)),"initial press launches");
        check(!RemoteShortcuts.shouldLaunch(AppActionsTest.event(0,KeyEvent.KEYCODE_F1,1,0)),"holding button never launches twice");
        check(!RemoteShortcuts.shouldLaunch(AppActionsTest.event(1,KeyEvent.KEYCODE_F1,0,0)),"release never launches twice");
        check(!RemoteShortcuts.shouldLaunch(AppActionsTest.event(0,KeyEvent.KEYCODE_F1,0,KeyEvent.FLAG_CANCELED)),"cancelled event never launches");
        Intent deep=WatchNextStore.safeIntent("org.example.player","https://example.invalid/watch/1");
        Intent jellyfin=new Intent().setComponent(new android.content.ComponentName("org.jellyfin.androidtv","org.jellyfin.androidtv.StartupActivity")).putExtra("item_id","synthetic-item");
        Intent resume=WatchNextStore.safeIntent("org.jellyfin.androidtv",jellyfin.toUri(Intent.URI_INTENT_SCHEME));
        check(resume!=null&&"synthetic-item".equals(resume.getStringExtra("item_id"))&&resume.getData()==null,"Jellyfin explicit no-URI resume preserved");
        check(WatchNextStore.safeIntent("org.other.app",jellyfin.toUri(Intent.URI_INTENT_SCHEME))==null,"foreign explicit component rejected");
        check(WatchNextStore.safeIntent("org.jellyfin.androidtv",new Intent().setPackage("org.jellyfin.androidtv").toUri(Intent.URI_INTENT_SCHEME))==null,"implicit no-URI launch rejected");
        jellyfin.setAction(Intent.ACTION_DELETE);
        check(WatchNextStore.safeIntent("org.jellyfin.androidtv",jellyfin.toUri(Intent.URI_INTENT_SCHEME))==null,"explicit destructive action rejected");
        check(deep!=null&&deep.getPackage().equals("org.example.player"),"deep link pinned to publishing app");
        check(deep.getFlags()==(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),"only approved launch flags");
        for(String raw:new String[]{"", "file:///private/file", "content://private/secret", "javascript:alert(1)",
            "intent://watch/1#Intent;scheme=video;package=org.other.app;end",
            "intent://watch/1#Intent;scheme=video;component=org.other.app/.View;end",
            "intent://watch/1#Intent;scheme=video;action=android.intent.action.DELETE;end"})
            check(WatchNextStore.safeIntent("org.example.player",raw)==null,"unsafe deep link rejected");
        Intent sanitized=WatchNextStore.safeIntent("org.example.player","intent://watch/1#Intent;scheme=video;launchFlags=0x10000003;S.id=episode;end");
        check(sanitized!=null&&sanitized.getFlags()==(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),"grant flags removed");
        check(sanitized.getStringExtra("id").equals("episode"),"playback identifier preserved");
        try(MatrixCursor c=new MatrixCursor(WatchNextStore.COLUMNS)){
            row(c,1,"org.example.player","Older","https://example.invalid/1",0,10,25,100,1);
            row(c,2,"org.example.player","Newest","https://example.invalid/2",0,20,50,100,1);
            row(c,3,"org.example.player","Hidden","https://example.invalid/3",0,30,25,100,0);
            row(c,4,"org.example.player","Completed","https://example.invalid/4",0,30,100,100,1);
            row(c,5,"org.example.player","Watchlist","https://example.invalid/5",3,30,0,100,1);
            row(c,6,"org.example.player","Duplicate","https://example.invalid/2",0,30,0,100,1);
            row(c,7,"org.example.player","Next episode","https://example.invalid/7",1,5,-1,-1,1);
            row(c,8,"org.example.launcher","Self","https://example.invalid/8",0,99,1,100,1);
            List<WatchNextStore.Item> items=WatchNextStore.parse(c,"org.example.launcher");
            check(items.size()==3,"only real visible unfinished/next content, deduplicated");
            check(items.get(0).id==2&&items.get(1).id==1,"latest engagement first");
            check(items.get(0).progress()==.5f&&items.get(1).progress()==.25f,"real playback progress");
            check(items.get(2).progress()==-1,"unknown progress never fabricated");
        }
        try(MatrixCursor c=new MatrixCursor(WatchNextStore.COLUMNS)){
            check(WatchNextStore.parse(c,"org.example.launcher").isEmpty(),"empty provider stays empty");
        }
        try(MatrixCursor c=new MatrixCursor(WatchNextStore.COLUMNS)){
            c.addRow(new Object[]{1,"org.example.player","Film","https://example.invalid/1",0,10,25,100,1,"https://example.invalid/poster.jpg","https://example.invalid/thumb.jpg"});
            check(WatchNextStore.parse(c,"org.example.launcher").get(0).artwork.endsWith("thumb.jpg"),"prefer published thumbnail");
        }
        try(MatrixCursor c=new MatrixCursor(WatchNextStore.COLUMNS)){
            for(int i=0;i<100;i++)row(c,i,"org.example.player","Episode "+i,"https://example.invalid/"+i,0,i,1,100,1);
            List<WatchNextStore.Item> items=WatchNextStore.parse(c,"org.example.launcher");
            check(items.size()==40&&items.get(0).id==99,"bounded feed keeps recent content");
        }
    }
    static void runUi(android.app.Instrumentation test) {
        RemoteSettingsActivity screen=(RemoteSettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),RemoteSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            test.waitForIdleSync();
            java.util.Map<String,?> before=CardStore.prefs(test.getTargetContext()).getAll();
            test.runOnMainSync(() -> {
                check(screen.box.getChildAt(0).isFocusable(),"key learning button reachable by remote");
                screen.box.getChildAt(0).performClick();
                check(screen.learning,"explicit button starts learning");
                screen.dispatchKeyEvent(AppActionsTest.event(0,KeyEvent.KEYCODE_F1,0,0));
                check(screen.learning&&screen.captured==KeyEvent.KEYCODE_F1,"capture down without launching app");
                screen.dispatchKeyEvent(AppActionsTest.event(1,KeyEvent.KEYCODE_F1,0,KeyEvent.FLAG_CANCELED));
                check(!screen.learning,"cancelled key does not open selection");
                screen.box.getChildAt(0).performClick();
                screen.dispatchKeyEvent(AppActionsTest.event(0,KeyEvent.KEYCODE_BACK,0,0));
                screen.dispatchKeyEvent(AppActionsTest.event(1,KeyEvent.KEYCODE_BACK,0,0));
                check(!screen.learning&&!screen.isFinishing(),"Back cancels learning without leaving settings");
                screen.box.getChildAt(0).performClick();
                screen.timeout.run();
                check(!screen.learning,"reserved system key cannot leave learner stuck");
                check(before.equals(CardStore.prefs(test.getTargetContext()).getAll()),"cancel and timeout never change settings");
            });
        } finally { test.runOnMainSync(screen::finish); }
    }
}
