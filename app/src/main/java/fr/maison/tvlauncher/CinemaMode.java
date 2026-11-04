package fr.maison.tvlauncher;

import android.content.SharedPreferences;

final class CinemaMode {
    static boolean enabled(SharedPreferences p){return p!=null&&p.getBoolean("cinema_mode",false);}
    static boolean notification(SharedPreferences p,String tag){
        if(!enabled(p))return true;
        int mode=p.getInt("cinema_notifications",0);if(mode==0)return true;if(mode==2)return false;
        if(tag==null||tag.isEmpty())return false;
        for(String allowed:p.getString("cinema_notification_tags","").split("[\\s,]+"))if(tag.equals(allowed))return true;
        return false;
    }
}
