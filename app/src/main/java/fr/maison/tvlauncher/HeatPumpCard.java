package fr.maison.tvlauncher;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

final class HeatPumpCard {
    private static final Path ROTOR=new Path();
    static {
        ROTOR.moveTo(29,23);ROTOR.cubicTo(19,22,21,11,27,14);ROTOR.cubicTo(30,16,29,20,29,23);
        ROTOR.moveTo(29,23);ROTOR.cubicTo(34,14,43,22,38,26);ROTOR.cubicTo(34,28,31,25,29,23);
        ROTOR.moveTo(29,23);ROTOR.cubicTo(35,32,23,36,22,30);ROTOR.cubicTo(22,26,26,24,29,23);
    }
    static JSONObject displayState(Map<String,Object> card,Map<String,JSONObject> states){
        Map<String,String> values=new HashMap<>();String priority=CardConfig.str(card,"entity",HeatPumpState.PRIORITY);
        StringBuilder details=new StringBuilder();String[] labels={I18n.t("Priorité"),I18n.t("Fréquence compresseur"),I18n.t("Dégivrage"),I18n.t("Pompe de circulation"),I18n.t("Chauffage autorisé")};
        for(int i=0;i<5;i++){String id=i==0?priority:CardConfig.str(card,HeatPumpState.KEYS[i-1],HeatPumpState.DEFAULTS[i-1]);JSONObject state=states.get(id);String value=state==null?"unavailable":state.optString("state","unavailable");values.put(id,value);details.append(labels[i]).append(" : ").append(value).append("\n").append(id).append(i==4?"":"\n\n");}
        HeatPumpState value=HeatPumpState.evaluate(card,values);
        JSONObject out=new JSONObject();try{out.put("entity_id",priority).put("state",value.label).put("pac_color",value.color).put("pac_running",value.running).put("pac_details",details.toString());}catch(Exception ignored){}return out;
    }
    static boolean animate(Map<String,Object> card,JSONObject state,boolean online){return !CinemaMode.enabled(NativeCards.appearance)&&HeatPumpState.isCard(card)&&state!=null&&state.optBoolean("pac_running")&&online&&CardConfig.bool(card,"animate",true)&&(Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled());}
    static float angle(long uptime){return (uptime%2000L)*360f/2000f;}
    static void draw(Canvas c,Paint p,Map<String,Object> card,JSONObject state,float x,float y,float w,float h,boolean selected,boolean online){
        selected=selected&&CardConfig.bool(card,"interactive",true);float scale=Math.min(1.75f,h/72f),radius=24*scale;
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setShader(new LinearGradient(x,y,x+w,y+h,new int[]{0x7a71a8b4,0x7a518ba7},null,Shader.TileMode.CLAMP));
        if(card.containsKey("background")||card.containsKey("background_opacity")){int alpha=card.containsKey("background_opacity")?((Number)card.get("background_opacity")).intValue()*255/100:122;int bg=0xff71a8b4;try{bg=Color.parseColor(CardConfig.str(card,"background","#71A8B4"));}catch(Exception ignored){}p.setShader(null);p.setColor((alpha<<24)|(bg&0xffffff));}RectF bounds=new RectF(x,y,x+w,y+h);c.drawRoundRect(bounds,radius,radius,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(selected?4:1.5f);p.setColor(selected?TvFocus.ACCENT:card.containsKey("background_opacity")&&((Number)card.get("background_opacity")).intValue()==0?Color.TRANSPARENT:0x38e2f4ff);c.drawRoundRect(bounds,radius,radius,p);p.setStyle(Paint.Style.FILL);
        int color=state==null?0xff9e9e9e:state.optInt("pac_color",0xff9e9e9e);float size=36*scale,left=x+10*scale,top=y+(h-size)/2;
        boolean showIcon=CardConfig.bool(card,"show_icon",true);if(showIcon)icon(c,p,left,top,size,color,animate(card,state,online)?angle(SystemClock.uptimeMillis()):0);
        float tx=left+(showIcon?size+8*scale:0),tw=w-(tx-x)-10*scale;boolean show=CardConfig.bool(card,"show_state",true);
        NativeCards.text(c,p,NativeCards.title(card,state),tx,y+h/2-(show?5*scale:-5*scale),tw,card.containsKey("title_size")?((Number)card.get("title_size")).intValue():NativeCards.font("card_title_size",19),NativeCards.tint("card_text_color",Color.WHITE),true);
        if(show){String label=state==null?I18n.t("Indisponible"):state.optString("state",I18n.t("Indisponible"));if(!online)label+=I18n.t(" · hors ligne");
            int body=card.containsKey("body_size")?((Number)card.get("body_size")).intValue():NativeCards.font("card_state_size",14);List<String> lines=NativeCards.wrap(label,p,tw,body,2);float baseline=y+h/2+body;for(String line:lines){NativeCards.text(c,p,line,tx,baseline,tw,body,NativeCards.tint("card_secondary_color",0xffeef7ff),false);baseline+=body+4;}}
    }
    static void icon(Canvas c,Paint p,float x,float y,float size,int color,float rotation){
        c.save();c.translate(x,y);c.scale(size/48,size/48);p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(2.5f);
        c.drawRoundRect(new RectF(3,7,45,39),5,5,p);p.setStrokeWidth(2);
        c.drawLine(7,41,15,41,p);c.drawLine(33,41,41,41,p);for(int line=15;line<=30;line+=5)c.drawLine(8,line,13,line,p);
        p.setStrokeWidth(1.8f);c.drawCircle(29,23,11,p);p.setStyle(Paint.Style.FILL);
        c.save();c.rotate(rotation,29,23);c.drawPath(ROTOR,p);c.drawCircle(29,23,2,p);c.restore();c.restore();p.setStrokeCap(Paint.Cap.BUTT);
    }
}
