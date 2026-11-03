package fr.maison.tvlauncher;

import android.graphics.RectF;
import android.content.Intent;
import android.speech.RecognizerIntent;
import org.json.JSONObject;
import java.util.*;

/** New home settings and voice protocol in memory, never sends a real command. */
final class HomeExperienceTest {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static void run()throws Exception{
        PanelRuntimeTest.Memory m=new PanelRuntimeTest.Memory();
        check(LauncherSetupActivity.offerOnFirstLaunch(m.prefs),"fresh install offers setup");
        m.values.put("setup_seen",true);check(!LauncherSetupActivity.offerOnFirstLaunch(m.prefs),"completed setup not repeated");
        m.values.clear();m.values.put("home_redesign_v2",true);check(!LauncherSetupActivity.offerOnFirstLaunch(m.prefs),"update preserves existing launcher");
        check(LauncherSetupActivity.homeMessage("ours","google",true).contains("ouvre encore"),"role alone never claims success");
        check(LauncherSetupActivity.homeMessage("ours","ours",false).contains("réellement"),"actual resolver determines success");
        check(LauncherSetupActivity.homeIntent().hasCategory(Intent.CATEGORY_HOME),"real HOME test intent");
        check(NativeCards.greeting(8).equals("Bonne matinée.")&&NativeCards.greeting(12).equals("Bon après-midi.")&&NativeCards.greeting(18).equals("Bonne soirée.")&&NativeCards.greeting(20).equals("Bonsoir."),"greeting time boundaries");
        Map<String,Object> card=CardConfig.parse("type: text\nname: '{greeting}'\ncontent: '{greeting} · {name}'\ninteractive: false\nbackground_opacity: 0\ntitle_size: 64\ntap_action:\n  action: none\n").get(0);
        check(!NativeCards.title(card,null).contains("{greeting}"),"greeting resolved in card title");
        check(!NativeCards.content(card,null).contains("{greeting}"),"greeting resolved in body");
        NativeCards.activate(null,card,null,false,null);check(true,"greeting display has no action");
        for(int edge=0;edge<4;edge++)for(int width:new int[]{40,60,80,100})for(int scale:new int[]{70,100,160})for(int clock:new int[]{14,20,40})for(int status:new int[]{10,14,28}){
            m.values.put("nav_position",edge);m.values.put("nav_width",width);m.values.put("nav_scale",scale);m.values.put("nav_clock_size",clock);m.values.put("nav_status_size",status);
            LauncherLayout layout=new LauncherLayout(m.prefs);NavigationStrip strip=new NavigationStrip(m.prefs,layout,new NavigationConfig(m.prefs),NavigationConfig.VOICE,0);
            RectF bar=layout.navigation();check(bar.contains(strip.clock)&&bar.contains(strip.status),"custom clock and status stay within every bar");
            check(!RectF.intersects(strip.clock,strip.status),"clock and status do not overlap");
            check(Math.abs(strip.clockSize-clock*scale/100f)<.01&&Math.abs(strip.statusSize-status*scale/100f)<.01,"independent font sizes respected");
            for(NavigationStrip.Tab tab:strip.tabs)check(!RectF.intersects(tab.rect,strip.clock)&&!RectF.intersects(tab.rect,strip.status),"tabs never overlap custom information");
        }
        JSONObject body=HaConversation.payload("Bonjour","conversation.test","fr","session-test");
        check(body.getString("text").equals("Bonjour")&&body.getString("agent_id").equals("conversation.test")&&body.getString("language").equals("fr")&&body.getString("conversation_id").equals("session-test"),"Assist payload fields");
        check(!HaConversation.payload("Bonjour","","fr","").has("agent_id"),"default HA agent optional");
        for(String text:new String[]{" ",String.join("",Collections.nCopies(4097,"a"))}){boolean rejected=false;try{HaConversation.payload(text,"","fr","");}catch(Exception e){rejected=true;}check(rejected,"bounded conversation input");}
        check(HaConversation.answer(new JSONObject("{\"speech\":{\"plain\":{\"speech\":\"Bonjour\"}}}")).equals("Bonjour"),"HA response parsed");
        Intent recognition=VoiceSearchActivity.recognitionIntent("fr");check(recognition.getAction().equals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)&&recognition.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE).equals("fr"),"Android speech recognition uses requested language");
        check(!recognition.hasExtra("ha_token"),"no HA token passed to speech provider");
        Intent custom=CustomVoiceService.search("https://search.example/?q={query}","été & chauffage ?");
        check(custom.getData().getQueryParameter("q").equals("été & chauffage ?"),"custom query safely encoded");
        check(custom.getExtras()==null&&custom.getData().getHost().equals("search.example"),"custom URL carries no launcher credentials");
        for(String url:new String[]{"http://example.org/?q={query}","https://u:p@example.org/?q={query}","javascript:alert(1)","intent://x","https://{query}.example.org/","https://example.org/#{query}","https://example.org/?q={other}","https://example.org:99999/?q={query}"}){
            boolean invalid=false;try{CustomVoiceService.template(url);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"unsafe custom URL rejected");
        }
        Intent app=CustomVoiceService.application("com.example.assistant",1,"fr");
        check(app.getPackage().equals("com.example.assistant")&&app.getAction().equals(Intent.ACTION_VOICE_COMMAND),"explicit custom app destination");
        check(app.getExtras().size()==1&&!app.hasExtra("ha_token"),"only language passed to custom app");
        boolean recursive=false;try{CustomVoiceService.packageName("fr.maison.tvlauncher");}catch(IllegalArgumentException e){recursive=true;}check(recursive,"no recursive launcher destination");
        List<Map<String,Object>> nav=NavigationConfig.defaults();NavigationConfig.validate(nav);check(nav.get(0).get("type").equals("voice"),"microphone in new installations");
        check(new NavigationConfig(m.prefs).selected(NavigationConfig.VOICE)!=null,"stable microphone destination");
    }
}
