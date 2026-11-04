package fr.maison.tvlauncher;

import android.app.Activity;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;

final class HdrCapabilities {
    static int[] types(Activity activity){
        try {Display display=activity.getDisplay();return display==null?new int[0]:display.getHdrCapabilities().getSupportedHdrTypes();}
        catch(RuntimeException unavailable){return new int[0];}
    }
    static boolean has(int[] types,int wanted){for(int type:types)if(type==wanted)return true;return false;}
    static String label(int type){
        switch(type){case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10:return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HLG:return "HLG";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS:return "HDR10+";
            default:return I18n.t("Format inconnu")+" ("+type+")";}
    }
    static String summary(int[] types){
        List<String> names=new ArrayList<>();for(int type:types)if(!names.contains(label(type)))names.add(label(type));
        return names.isEmpty()?I18n.t("Aucun format HDR déclaré par Android"):android.text.TextUtils.join(" · ",names);
    }
}
