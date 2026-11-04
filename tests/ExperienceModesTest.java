package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONObject;

final class ExperienceModesTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void run(Instrumentation test)throws Exception{
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();
        Context context=new ContextWrapper(test.getTargetContext()){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return m.prefs;}
        };
        check(!GuestMode.active(context),"guest PIN optional and disabled by default");
        check(!GuestMode.protectedIntent(context,new Intent(context,LauncherSettingsActivity.class)),"settings accessible by default");
        check(!GuestMode.valid("12")&&!GuestMode.valid("123456789")&&!GuestMode.valid("12a4"),"PIN restricted to 4–8 digits");
        GuestMode.configure(m.prefs,"7385");
        check(GuestMode.active(context)&&!m.values.containsValue("7385"),"guest lock explicit, no plaintext PIN");
        check(GuestMode.protectedIntent(context,new Intent(context,LauncherSettingsActivity.class)),"guest cannot open settings");
        check(GuestMode.protectedIntent(context,new Intent(context,AppNameActivity.class)),"guest cannot rename apps");
        check(!GuestMode.protectedIntent(context,new Intent(context,MainActivity.class)),"home remains accessible");
        check(!GuestMode.protectedIntent(context,new Intent(context,GuestModeActivity.class)),"unlock remains accessible");
        check(!GuestMode.protectedIntent(context,new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://example.invalid"))),"guest mode not a system kiosk");
        for(int i=0;i<5;i++)check(!GuestMode.unlock(m.prefs,"0000",1000+i),"wrong PIN refused");
        check(!GuestMode.unlock(m.prefs,"7385",1006)&&GuestMode.active(context),"correct PIN also waits for cooldown");
        check(GuestMode.unlock(m.prefs,"7385",32000)&&!GuestMode.active(context),"correct PIN exits after cooldown");
        Map<String,Object> card=CardConfig.parse("type: button\nname: Device\nconditions:\n  - entity: switch.test\n    operator: state\n    value: 'on'\n").get(0);
        Map<String,JSONObject> states=new HashMap<>();
        check(!CardConditions.visible(card,states,true),"missing entity hidden");
        states.put("switch.test",new JSONObject().put("state","on"));check(CardConditions.visible(card,states,true),"running equipment shown");
        check(!CardConditions.visible(card,states,false),"offline stale state hidden");
        states.put("switch.test",new JSONObject().put("state","off"));check(!CardConditions.visible(card,states,true),"stopped equipment hidden");
        Map<String,Object> condition=(Map<String,Object>)((List<?>)card.get("conditions")).get(0);condition.put("operator","not_state");
        for(String value:new String[]{"unknown","unavailable"}){states.put("switch.test",new JSONObject().put("state",value));check(!CardConditions.visible(card,states,true),"unknown never matches not_state");}
        condition.put("operator","above");condition.put("value",10);
        states.put("switch.test",new JSONObject().put("state","11.2"));check(CardConditions.visible(card,states,true),"numeric threshold");
        states.put("switch.test",new JSONObject().put("state","NaN"));check(!CardConditions.visible(card,states,true),"invalid numeric state hidden");
        card.remove("conditions");check(CardConditions.visible(card,states,false),"unconditional cards unchanged offline");
        check(!CinemaMode.enabled(m.prefs)&&CinemaMode.notification(m.prefs,"anything"),"cinema defaults preserve all notifications");
        m.values.put("cinema_mode",true);m.values.put("cinema_notifications",1);m.values.put("cinema_notification_tags","laundry, entrance");
        check(CinemaMode.notification(m.prefs,"laundry")&&CinemaMode.notification(m.prefs,"entrance"),"chosen tags retained");
        check(!CinemaMode.notification(m.prefs,"other")&&!CinemaMode.notification(m.prefs,""),"unselected tags dropped, no queue");
        m.values.put("cinema_notifications",2);check(!CinemaMode.notification(m.prefs,"laundry"),"explicit silence");
        m.values.put(NetworkStatus.ICON,true);m.values.put(NetworkStatus.RATE,true);
        check(!NetworkStatus.enabled(m.prefs),"cinema stops network sampler");
        NavigationStrip strip=new NavigationStrip(m.prefs,new LauncherLayout(m.prefs),new NavigationConfig(m.prefs),0,0);
        check(!strip.showStatus&&!strip.showNetwork&&strip.showClock,"cinema hides secondary info, preserves clock and navigation");
        m.values.put("cinema_mode",false);check(NetworkStatus.enabled(m.prefs),"leaving cinema restores settings without rewriting them");
        runUi(test);
    }
    static void runUi(Instrumentation test)throws Exception{
        Context context=test.getTargetContext();SharedPreferences guest=GuestMode.prefs(context),prefs=CardStore.prefs(context);
        android.app.Activity screen=null;MainActivity home=null;
        try{
            guest.edit().clear().commit();check(!GuestMode.active(context),"fresh installation never asks for PIN");
            home=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final MainActivity launcher=home;
            GuestMode.configure(guest,"7385");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(GuestModeActivity.class.getName(),null,false);
            test.runOnMainSync(()->launcher.startActivity(new Intent(launcher,LauncherSettingsActivity.class)));
            screen=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);
            check(screen instanceof GuestModeActivity,"actual settings navigation redirects to PIN screen");
            GuestModeActivity unlock=(GuestModeActivity)screen;
            test.runOnMainSync(()->{
                ((android.widget.EditText)unlock.box.getChildAt(1)).setText("7385");
                unlock.box.getChildAt(2).performClick();
            });
            unlock.worker.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);test.waitForIdleSync();
            check(!GuestMode.active(context),"PIN UI exits guest mode after correct code");
            screen=null;
            prefs.edit().putBoolean(NetworkStatus.ICON,true).putBoolean(NetworkStatus.RATE,true).commit();
            home=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity.LauncherView view=(MainActivity.LauncherView)home.root.getChildAt(0);
            check(view.network.callback!=null,"foreground launcher starts passive network observer");
            ConnectionDiagnosticActivity diagnostic=(ConnectionDiagnosticActivity)test.startActivitySync(new Intent(context,ConnectionDiagnosticActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));screen=diagnostic;test.waitForIdleSync();
            check(view.network.callback==null,"leaving home unregisters network observer");
            test.runOnMainSync(()->{
                String status=diagnostic.stages.getText().toString();
                check(status.contains("1 ·")&&status.contains("4 ·")&&!status.contains("http")&&!status.contains("test-only"),"diagnostic stages contain no address or credential");
            });
            test.runOnMainSync(diagnostic::finish);
            prefs.edit().putInt("app_icon_shape",7).putInt("app_icon_ratio",160).commit();
            LayoutSettingsActivity icons=(LayoutSettingsActivity)test.startActivitySync(new Intent(context,LayoutSettingsActivity.class).putExtra("scope","Applications").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));screen=icons;
            test.runOnMainSync(()->{
                icons.edit("Icônes des applications");
                android.widget.EditText ratio=icons.root.findViewWithTag("app_icon_ratio");
                check(ratio!=null&&ratio.isEnabled(),"custom shape controls reachable in real TV settings");
                ratio.setText("220");
                android.widget.ImageView preview=icons.root.findViewWithTag("icon-shape-preview");
                check(preview!=null&&preview.getDrawable()!=null,"TV custom shape has a live preview");
                check(prefs.getInt("app_icon_ratio",0)==160,"custom shape is a draft before saving");
                icons.footer.getChildAt(1).performClick();
                check(prefs.getInt("app_icon_ratio",0)==220,"TV custom geometry saved through shared schema");
                prefs.edit().putInt("app_icon_shape",6).commit();icons.edit("Icônes des applications");
                check(!((android.widget.EditText)icons.root.findViewWithTag("app_icon_ratio")).isEnabled(),"fixed rectangle disables custom-only parameters");
            });
        }finally{
            guest.edit().clear().commit();prefs.edit().remove(NetworkStatus.ICON).remove(NetworkStatus.RATE).remove("app_icon_shape").remove("app_icon_ratio").remove("app_icon_corners").remove("app_icon_logo_size").commit();
            if(screen!=null){android.app.Activity last=screen;test.runOnMainSync(last::finish);}
            if(home!=null){MainActivity last=home;test.runOnMainSync(last::finish);}
        }
    }
}
