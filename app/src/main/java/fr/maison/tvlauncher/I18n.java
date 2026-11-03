package fr.maison.tvlauncher;

import android.content.Context;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Translate only built-in labels. Never pass user content through this class. */
final class I18n {
    private static Context application;
    private static final ConcurrentHashMap<String,Integer> ids=new ConcurrentHashMap<>();
    // Only the Application or a configuration context is passed, never an Activity.
    static void init(Context context){application=context;}
    static Locale locale(){return application==null?Locale.getDefault():application.getResources().getConfiguration().getLocales().get(0);}
    static String t(String source){
        if(application==null)return source; // Standalone model tests have no Android resources.
        int id=ids.computeIfAbsent(source,key->application.getResources().getIdentifier("ui_"+Integer.toHexString(key.hashCode()),"string",application.getPackageName()));
        return id==0?source:application.getString(id);
    }
    static String voiceLanguage(android.content.SharedPreferences prefs){
        String language=prefs.getString("voice_language","auto").trim();
        return language.isEmpty()||language.equalsIgnoreCase("auto")?locale().toLanguageTag():language;
    }
    static String clockSample(){
        if(application==null)return "88:88";
        java.util.Calendar calendar=java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY,23);calendar.set(java.util.Calendar.MINUTE,58);
        return android.text.format.DateFormat.getTimeFormat(application).format(calendar.getTime());
    }
}
