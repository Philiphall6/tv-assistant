package fr.maison.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only Android TV Watch Next feed. No account scraping, fake entries or history upload. */
final class WatchNextStore {
    static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");
    static final String[] COLUMNS = {"_id", "package_name", "title", "intent_uri", "watch_next_type",
        "last_engagement_time_utc_millis", "last_playback_position_millis", "duration_millis", "browsable", "poster_art_uri", "thumbnail_uri"};
    static final class Item {
        final long id, engagement, position, duration;
        final String pkg, title, artwork;
        final Intent intent;
        Item(long id, String pkg, String title, Intent intent, long engagement, long position, long duration, String artwork) {
            this.id=id; this.pkg=pkg; this.title=title; this.intent=intent; this.engagement=engagement; this.position=position; this.duration=duration; this.artwork=artwork;
        }
        float progress() { return position >= 0 && duration > 0 ? Math.min(1f, (float)position/duration) : -1f; }
    }
    interface Listener { void result(List<Item> items, boolean available); }
    final Context context;
    final Listener listener;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final ContentObserver observer = new ContentObserver(main) { @Override public void onChange(boolean selfChange) { refresh(); } };
    final Runnable refreshTask = this::load;
    boolean closed, querying, again, registered;
    CancellationSignal cancellation;
    WatchNextStore(Context context, Listener listener) { this.context=context.getApplicationContext(); this.listener=listener; }
    void start() {
        if(closed)return;
        if(!PlaybackPermission.granted(context)){listener.result(new ArrayList<>(),false);return;}
        try { context.getContentResolver().registerContentObserver(URI, true, observer); registered=true; } catch(RuntimeException ignored) { }
        load();
    }
    void refresh() { if(!closed) { main.removeCallbacks(refreshTask); main.postDelayed(refreshTask, 250); } }
    void load() {
        if(closed)return;
        if(querying) { again=true; return; }
        querying=true;
        CancellationSignal signal = new CancellationSignal(); cancellation=signal;
        worker.execute(() -> {
            List<Item> items = new ArrayList<>(); boolean available=false;
            try(Cursor cursor=context.getContentResolver().query(URI,COLUMNS,null,null,null,signal)) {
                if(cursor!=null) { items=parse(cursor,context.getPackageName()); available=true; }
            } catch(RuntimeException ignored) { /* Missing provider/permission is not an empty success. */ }
            List<Item> result=items; boolean readable=available;
            main.post(() -> { if(closed)return; querying=false; cancellation=null; listener.result(result,readable); if(again){again=false;refresh();} });
        });
    }
    void close() {
        closed=true; main.removeCallbacks(refreshTask);
        if(cancellation!=null)cancellation.cancel();
        if(registered)try{context.getContentResolver().unregisterContentObserver(observer);}catch(RuntimeException ignored){}
        worker.shutdownNow();
    }
    static String string(Cursor cursor,String name) { int index=cursor.getColumnIndex(name); return index<0||cursor.isNull(index)?"":cursor.getString(index); }
    static long number(Cursor cursor,String name,long fallback) { int index=cursor.getColumnIndex(name); return index<0||cursor.isNull(index)?fallback:cursor.getLong(index); }
    static List<Item> parse(Cursor cursor,String self) {
        List<Item> items=new ArrayList<>(); Set<String> seen=new HashSet<>(); int rows=0;
        while(rows++<2000&&cursor.moveToNext()) {
            try {
                String pkg=string(cursor,"package_name"), title=string(cursor,"title").trim(), raw=string(cursor,"intent_uri");
                long type=number(cursor,"watch_next_type",-1), position=number(cursor,"last_playback_position_millis",-1), duration=number(cursor,"duration_millis",-1);
                if(type!=0&&type!=1||number(cursor,"browsable",1)!=1||title.isEmpty()||title.length()>1000||pkg.equals(self)||duration>0&&position>=duration)continue;
                Intent intent=safeIntent(pkg,raw);
                if(intent==null||!seen.add(pkg+"\n"+raw))continue;
                String artwork=string(cursor,"thumbnail_uri");if(artwork.isEmpty())artwork=string(cursor,"poster_art_uri");
                items.add(new Item(number(cursor,"_id",0),pkg,title,intent,number(cursor,"last_engagement_time_utc_millis",0),position,duration,artwork));
            } catch(RuntimeException ignored) { /* Skip a malformed entry, keep the other applications. */ }
        }
        items.sort(Comparator.comparingLong((Item i)->i.engagement).reversed());
        return new ArrayList<>(items.subList(0,Math.min(40,items.size())));
    }
    static Intent safeIntent(String pkg,String raw) {
        if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||raw==null||raw.isEmpty()||raw.length()>16384)return null;
        try {
            Intent intent=Intent.parseUri(raw,Intent.URI_INTENT_SCHEME);
            boolean explicit=intent.getComponent()!=null&&pkg.equals(intent.getComponent().getPackageName());
            if(!(Intent.ACTION_VIEW.equals(intent.getAction())||explicit&&(intent.getAction()==null||Intent.ACTION_MAIN.equals(intent.getAction())))||intent.getSelector()!=null)return null;
            if(intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))return null;
            if(intent.getComponent()!=null&&!pkg.equals(intent.getComponent().getPackageName()))return null;
            String scheme=intent.getScheme();
            // Jellyfin publishes an explicit activity + item ID, without a data URI.
            // No-URI launches are allowed only for a component owned by the publisher.
            if(scheme==null?!explicit:scheme.equalsIgnoreCase("file")||scheme.equalsIgnoreCase("content")||scheme.equalsIgnoreCase("javascript")||scheme.equalsIgnoreCase("data"))return null;
            intent.setPackage(pkg); intent.setClipData(null);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        } catch(Exception ignored) { return null; }
    }
}
