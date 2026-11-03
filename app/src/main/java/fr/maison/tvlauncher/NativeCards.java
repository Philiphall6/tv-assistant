package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.widget.Toast;
import java.util.*;
import java.io.*;
import java.util.regex.*;
import org.json.JSONObject;

final class NativeCards {
    static android.content.SharedPreferences appearance;
    static int font(String key,int def){return appearance==null?def:appearance.getInt(key,def);}
    static int tint(String key,int def){return appearance==null?def:LauncherLayout.color(appearance,key,def);}
    static Typeface iconFont;static Map<String,String> glyphs;
    static void init(Activity a){appearance=CardStore.prefs(a);if(glyphs!=null)return;glyphs=new HashMap<>();try{
        if(android.os.Build.VERSION.SDK_INT>=26)iconFont=a.getResources().getFont(R.font.material_design_icons);
        else {File f=new File(a.getCacheDir(),"mdi.ttf");try(InputStream in=a.getResources().openRawResource(R.font.material_design_icons);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}iconFont=Typeface.createFromFile(f);}
        StringBuilder css=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(a.getResources().openRawResource(R.raw.material_design_icons)))){String line;while((line=r.readLine())!=null)css.append(line).append('\n');}
        Matcher m=Pattern.compile("\\.mdi-([a-z0-9-]+)::before\\s*\\{\\s*content:\\s*\"\\\\([A-Fa-f0-9]+)\"",Pattern.MULTILINE).matcher(css);
        while(m.find())glyphs.put("mdi:"+m.group(1),new String(Character.toChars(Integer.parseInt(m.group(2),16))));
    }catch(Exception ignored){}}
    static String title(Map<String,Object> card,JSONObject state){String name=CardConfig.str(card,"name","");if(!name.isEmpty())return dynamicText(name);JSONObject a=state==null?null:state.optJSONObject("attributes");return a!=null?a.optString("friendly_name",CardConfig.str(card,"entity",I18n.t("Carte"))):CardConfig.str(card,"entity",I18n.t("Carte"));}
    static String stateText(JSONObject state){if(state==null)return I18n.t("Indisponible");String s=state.optString("state","unknown");switch(s){case "on":s=I18n.t("Allumé");break;case "off":s=I18n.t("Éteint");break;case "open":s=I18n.t("Ouvert");break;case "closed":s=I18n.t("Fermé");break;case "unknown":s=I18n.t("Inconnu");break;case "unavailable":s=I18n.t("Indisponible");break;}JSONObject a=state.optJSONObject("attributes");String unit=a==null?"":a.optString("unit_of_measurement","");return s+(unit.isEmpty()?"":" "+unit);}
    static String greeting(int hour){return hour<12?I18n.t("Bonne matinée."):hour<18?I18n.t("Bon après-midi."):hour<20?I18n.t("Bonne soirée."):I18n.t("Bonsoir.");}
    static String dynamicText(String text){return text.replace("{greeting}",greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));}
    static String content(Map<String,Object> card,JSONObject state){String text=dynamicText(CardConfig.str(card,"content",""));String value=stateText(state);return text.replace("{state}",value).replace("{name}",title(card,state)).replace("{entity}",CardConfig.str(card,"entity",""));}
    static void text(Canvas c,Paint p,String value,float x,float y,float width,float size,int color,boolean bold){p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);String s=value.replace('\n',' ');int count=p.breakText(s,true,Math.max(0,width),null);if(count<s.length())s=s.substring(0,Math.max(0,count-1))+"…";c.drawText(s,x,y,p);}
    static void draw(Canvas c,Paint p,Map<String,Object> card,JSONObject state,float x,float y,float w,float h,boolean selected,boolean online){
        if(HeatPumpState.isCard(card)){HeatPumpCard.draw(c,p,card,state,x,y,w,h,selected,online);return;}
        selected=selected&&CardConfig.bool(card,"interactive",true);
        p.setShader(null);int accent=TvFocus.ACCENT;try{accent=Color.parseColor(CardConfig.str(card,"color","#85DCF2"));}catch(Exception ignored){}
        int background=0xff19232c;try{background=Color.parseColor(CardConfig.str(card,"background","#19232C"));}catch(Exception ignored){}int alpha=card.containsKey("background_opacity")?((Number)card.get("background_opacity")).intValue()*255/100:238;background=(alpha<<24)|(background&0xffffff);p.setColor(selected?TvFocus.ACTIVE_SURFACE:background);RectF bounds=new RectF(x,y,x+w,y+h);c.drawRoundRect(bounds,20,20,p);
        if(selected){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(TvFocus.ACCENT);c.drawRoundRect(bounds,20,20,p);p.setStyle(Paint.Style.FILL);}
        String icon=CardConfig.str(card,"icon","mdi:home");String glyph=glyphs==null?null:glyphs.get(icon);
        boolean showIcon=CardConfig.bool(card,"show_icon",true);float inset=showIcon?80:22;
        if(showIcon){if(glyph!=null&&iconFont!=null){p.setTypeface(iconFont);p.setTextSize(h>150?40:32);p.setColor(accent);c.drawText(glyph,x+22,y+54,p);}else text(c,p,icon.startsWith("mdi:")?"◆":icon,x+22,y+50,50,28,accent,false);}
        float titleSize=card.containsKey("title_size")?((Number)card.get("title_size")).intValue():font("card_title_size",19),titleY=y+Math.max(42,titleSize+14);
        text(c,p,title(card,state),x+inset,titleY,w-inset-22,titleSize,tint("card_text_color",Color.WHITE),true);
        boolean showState=CardConfig.bool(card,"show_state",true)&&!CardConfig.str(card,"entity","").isEmpty();
        String content=content(card,state);float top=Math.max(y+73,titleY+31);
        int body=card.containsKey("body_size")?((Number)card.get("body_size")).intValue():font("card_state_size",14);float lineHeight=body+9;
        if(h>150){for(String line:wrap(content,p,w-44,body,Math.max(1,(int)((h-116)/lineHeight)))){text(c,p,line,x+22,top+lineHeight,w-44,body,tint("card_secondary_color",0xffcbd3dc),false);top+=lineHeight;}
            if(showState)text(c,p,stateText(state)+(online?"":I18n.t(" · hors ligne")),x+22,y+h-23,w-44,body,accent,true);
        }else text(c,p,content.isEmpty()&&showState?stateText(state)+(online?"":I18n.t(" · hors ligne")):content,x+inset,top,w-inset-22,body,tint("card_secondary_color",0xffcbd3dc),false);
    }
    static List<String> wrap(String text,Paint p,float width,float size,int max){List<String> lines=new ArrayList<>();p.setTypeface(Typeface.DEFAULT);p.setTextSize(size);for(String paragraph:text.split("\n",-1)){String rest=paragraph;do{int n=Math.max(1,p.breakText(rest,true,width,null));if(n<rest.length()){int space=rest.lastIndexOf(' ',n);if(space>n/2)n=space;}String line=rest.substring(0,Math.min(n,rest.length()));rest=rest.substring(Math.min(n,rest.length())).trim();if(lines.size()==max-1&&(!rest.isEmpty()||lines.size()+1<text.split("\n",-1).length))line+="…";lines.add(line);if(lines.size()>=max)return lines;}while(!rest.isEmpty());}return lines;}
    interface Navigation {void go(String path);}
    static void activate(Activity activity,Map<String,Object> card,JSONObject state,boolean hold,Navigation navigation){
        if(!CardConfig.bool(card,"interactive",true))return;
        Map<String,Object> action=CardConfig.action(card,hold?"hold_action":"tap_action");
        if(hold&&"none".equals(CardConfig.str(action,"action","none"))&&card.get("actions") instanceof List&&!((List<?>)card.get("actions")).isEmpty()){
            List<?> actions=(List<?>)card.get("actions");String[] names=new String[actions.size()];for(int i=0;i<names.length;i++){Map<String,Object> a=CardConfig.map(actions.get(i),I18n.t("Action"));names[i]=CardConfig.str(a,"name",CardConfig.str(a,"perform_action",CardConfig.str(a,"action",I18n.t("Action"))));}
            AlertDialog d=new AlertDialog.Builder(activity).setTitle(title(card,state)).setItems(names,(v,i)->run(activity,card,state,CardConfig.map(actions.get(i),I18n.t("Action")),navigation)).setNegativeButton(I18n.t("Retour"),null).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();return;
        }
        run(activity,card,state,action,navigation);
    }
    static void run(Activity a,Map<String,Object> card,JSONObject state,Map<String,Object> action,Navigation nav){
        String kind=CardConfig.str(action,"action","none");if(kind.equals("none"))return;
        if(kind.equals("more-info")){String info=content(card,state);if(HeatPumpState.isCard(card))info=stateText(state)+"\n\n"+(state==null?I18n.t("Capteurs indisponibles"):state.optString("pac_details",""));else if(!CardConfig.str(card,"entity","").isEmpty())info+="\n\n"+CardConfig.str(card,"entity","")+"\n"+stateText(state);AlertDialog d=new AlertDialog.Builder(a).setTitle(title(card,state)).setMessage(info).setPositiveButton(I18n.t("Retour"),null).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();return;}
        if(kind.equals("navigate")){nav.go(CardConfig.str(action,"navigation_path","dashboard"));return;}
        Runnable execute=()->{try{
            if(kind.equals("url")){a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(CardConfig.str(action,"url_path",""))));return;}
            if(kind.equals("launch-app")){String pkg=CardConfig.str(action,"package","");Intent i=a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=a.getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)throw new Exception();a.startActivity(i);return;}
            String entity=CardConfig.str(card,"entity","");String service=kind.equals("toggle")?"homeassistant.toggle":CardConfig.str(action,"perform_action",CardConfig.str(action,"service",""));String[] parts=service.split("\\.");
            Map<String,Object> data=action.containsKey("data")?CardConfig.map(action.get("data"),"data"):new LinkedHashMap<>();Map<String,Object> target=action.containsKey("target")?CardConfig.map(action.get("target"),"target"):new LinkedHashMap<>();
            if(target.isEmpty()&&!entity.isEmpty()&&!data.containsKey("entity_id")&&!data.containsKey("area_id")&&!data.containsKey("device_id")&&!data.containsKey("label_id")&&!data.containsKey("floor_id"))target.put("entity_id",entity);
            data.putAll(target);HaClient.callService(a,parts[0],parts[1],new JSONObject(data),(ok,msg)->a.runOnUiThread(()->Toast.makeText(a,ok?I18n.t("Commande envoyée"):msg,Toast.LENGTH_LONG).show()));
        }catch(Exception e){Toast.makeText(a,I18n.t("Action indisponible"),Toast.LENGTH_LONG).show();}};
        if(CardConfig.bool(action,"confirmation",true)){AlertDialog d=new AlertDialog.Builder(a).setTitle(title(card,state)).setMessage(I18n.t("Exécuter : ")+CardConfig.str(action,"name",CardConfig.str(action,"perform_action",CardConfig.str(action,"service",kind)))+" ?").setNegativeButton(I18n.t("Annuler"),null).setPositiveButton(I18n.t("Exécuter"),(v,i)->execute.run()).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();}else execute.run();
    }
}
