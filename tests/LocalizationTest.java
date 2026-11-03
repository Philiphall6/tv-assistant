package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.*;
import org.json.JSONObject;

final class LocalizationTest {
    static int checks;
    static void check(boolean valid,String message){checks++;if(!valid)throw new AssertionError(message);}
    static void use(Context context,String tag){Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));I18n.init(context.createConfigurationContext(config));}
    static void navigationLabels(Context context,String tag){
        use(context,tag);
        PanelRuntimeTest.Memory memory=new PanelRuntimeTest.Memory();
        for(int scale:new int[]{70,100,160})for(int mode:new int[]{0,2}){
            memory.values.put("nav_scale",scale);memory.values.put("nav_display",mode);
            NavigationConfig config=new NavigationConfig(memory.prefs);
            NavigationStrip strip=new NavigationStrip(memory.prefs,new LauncherLayout(memory.prefs),config,NavigationConfig.BRAND,0);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            for(NavigationStrip.Tab tab:strip.tabs){
                Map<String,Object> item=config.items.get(tab.index);
                int display=NavigationStrip.mode(memory.prefs,item);if(display==1)continue;
                String label=(String)item.get("label");
                float width=tab.rect.width()-2*strip.padding-(display==2?(NavigationStrip.iconSize(memory.prefs,item)+8)*strip.scale:0);
                paint.setTextSize(NavigationStrip.textSize(memory.prefs,item)*strip.scale);
                for(Typeface font:new Typeface[]{Typeface.DEFAULT,Typeface.DEFAULT_BOLD}){
                    paint.setTypeface(font);
                    check(paint.breakText(label,true,width,null)==label.length(),"Complete navigation label: "+tag+" / "+label+" / "+scale+" / "+mode);
                }
            }
        }
    }
    static void run(Instrumentation test)throws Exception{
        Context context=test.getTargetContext();
        try {
            navigationLabels(context,"en-US");navigationLabels(context,"fr-FR");
            use(context,"en-US");
            check(I18n.t("Paramètres").equals("Settings"),"English settings resources");
            check(NativeCards.greeting(8).equals("Good morning."),"English greeting");
            check(NativeCards.greeting(21).equals("Good evening."),"English evening greeting");
            check(NativeCards.stateText(new JSONObject().put("state","off")).equals("Off"),"English native state");
            check(NavigationConfig.defaults().get(1).get("label").equals("For you"),"English default tabs");
            Map<String,Object> card=new HashMap<>();card.put("name","Paramètres");
            check(NativeCards.title(card,null).equals("Paramètres"),"Never translate a user's card name");
            check(HeatPumpState.frequencyText(32.5).equals("32.5 Hz"),"English decimal formatting");
            check(I18n.voiceLanguage(CardStore.prefs(context)).equals("en-US"),"Voice language follows Android by default");
            use(context,"fr-CA");
            check(I18n.t("Paramètres").equals("Paramètres"),"French regional fallback");
            check(NativeCards.greeting(8).equals("Bonne matinée."),"French greeting after locale change");
            check(NavigationConfig.defaults().get(1).get("label").equals("Pour vous"),"Tabs refreshed after locale change");
            check(HeatPumpState.frequencyText(32.5).equals("32,5 Hz"),"French decimal formatting");
            use(context,"de-DE");
            check(I18n.t("Paramètres").equals("Settings"),"Unsupported UI language uses English resources");
            check(I18n.voiceLanguage(CardStore.prefs(context)).equals("de-DE"),"Speech keeps actual system language, not UI fallback");
        } finally {use(context,"fr-FR");}
    }
}
