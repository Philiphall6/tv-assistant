package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Content-sized navigation. Clock space is reserved before laying out any tab. */
final class NavigationStrip {
    static final String BRAND_TEXT="TV Assistant";
    static final class Tab {
        final int index; final RectF rect;
        Tab(int index,RectF rect){this.index=index;this.rect=rect;}
    }
    final List<Tab> tabs=new ArrayList<>();
    final RectF brand=new RectF(),logo=new RectF(),brandText=new RectF(),clock=new RectF(),status=new RectF(),network=new RectF(),area=new RectF();
    final float scale,gap,padding,clockSize,statusSize,networkSize;
    final boolean vertical,showClock,showStatus,showNetwork,showNetworkIcon,showNetworkRate;
    boolean fullStatus;
    int offset;
    float counterY;
    final Paint measure=new Paint(Paint.ANTI_ALIAS_FLAG);

    NavigationStrip(SharedPreferences prefs,LauncherLayout layout,NavigationConfig config,int selected,int previousOffset){
        RectF bar=layout.navigation();scale=layout.navScale;vertical=layout.edge>=2;
        gap=layout.number("nav_tab_gap",8,0,48)*scale;padding=10*scale;
        showClock=prefs.getBoolean("show_clock",true);showStatus=prefs.getBoolean("show_status",true)&&!CinemaMode.enabled(prefs);
        showNetworkIcon=prefs.getBoolean(NetworkStatus.ICON,false)&&!CinemaMode.enabled(prefs);showNetworkRate=prefs.getBoolean(NetworkStatus.RATE,false)&&!CinemaMode.enabled(prefs);showNetwork=showNetworkIcon||showNetworkRate;
        float cs=layout.number("nav_clock_size",20,14,40)*scale,ss=layout.number("nav_status_size",14,10,28)*scale;
        float infoHeight=(showClock?cs*1.3f:0)+(showStatus?ss*1.4f:0)+22*scale+8*scale;
        float infoFit=showNetwork&&!vertical?Math.min(1,(bar.height()-12*scale)/infoHeight):1;
        clockSize=cs*infoFit;statusSize=ss*infoFit;networkSize=14*scale*infoFit;
        float size=layout.number("nav_logo_size",50,32,76)*scale,outer=16*scale,between=12*scale;
        float brandMin=size+16*scale,brandFull=brandMin+12*scale+textWidth(BRAND_TEXT,20*scale,true);
        float clockWidth=showClock?Math.max(textWidth("88:88",clockSize,true),textWidth(I18n.clockSample(),clockSize,true))+8*scale:0;
        float statusWidth=showStatus?Math.max(textWidth(LinkStatus.summary(prefs,true),statusSize,false),textWidth(I18n.t("● Mode local"),statusSize,false))+4*scale:0;
        float networkWidth=(showNetworkIcon?networkSize*1.5f+(showNetworkRate?5*scale:0):0)+(showNetworkRate?textWidth("↓999,9 ↑999,9 Mb/s",networkSize,false):0);
        float infoFull=Math.max(networkWidth,Math.max(clockWidth,statusWidth)),infoMin=Math.max(networkWidth,clockWidth+(showStatus?(showClock?20:12)*scale:0));
        float[] widths=new float[config.items.size()],heights=new float[widths.length];float total=0;
        for(int i=0;i<widths.length;i++){
            Map<String,Object> item=config.items.get(i);int mode=mode(prefs,item);
            float fs=textSize(prefs,item)*scale,is=iconSize(prefs,item)*scale;
            // Regular and bold fonts do not have identical advances. Reserve both
            // states so a translated label does not truncate when focus moves away.
            String label=CardConfig.str(item,"label","");
            float labelWidth=Math.max(textWidth(label,fs,true),textWidth(label,fs,false));
            widths[i]=2*padding+(mode==1?is:labelWidth+(mode==2?is+8*scale:0));
            widths[i]=Math.max(36*scale,widths[i]);heights[i]=Math.max(62*scale,Math.max(mode==0?0:is+16*scale,mode==1?0:fs*1.3f+16*scale));
            total+=widths[i]+(i>0?gap:0);
        }
        if(!vertical){
            float groups=(widths.length>0?between:0)+(infoFull>0?between:0),available=bar.width()-2*outer-groups;
            // Only compact decorations when their measured content prevents the tabs from fitting.
            fullStatus=!showStatus||brandMin+total+infoFull<=available;
            float infoWidth=fullStatus?infoFull:infoMin;
            float brandWidth=Math.max(brandMin,Math.min(brandFull,available-total-infoWidth));
            // Align the compact brand + tabs group as well as the bar itself.
            // At 100% the outer bar cannot move, but its content must still align.
            // Clock/status retain their reserved end slot; overflowing tabs keep
            // the full viewport instead of being shifted outside it.
            float contentRoom=bar.width()-2*outer-(infoWidth>0?infoWidth+between:0);
            float contentWidth=brandWidth+(widths.length>0?between+total:0);
            float contentLeft=bar.left+outer+Math.max(0,contentRoom-contentWidth)*layout.number("nav_alignment",1,0,2)/2f;
            brand.set(contentLeft,bar.centerY()-Math.max(size+16*scale,70*scale)/2,contentLeft+brandWidth,bar.centerY()+Math.max(size+16*scale,70*scale)/2);
            logo.set(brand.left+8*scale,bar.centerY()-size/2,brand.left+8*scale+size,bar.centerY()+size/2);
            brandText.set(logo.right+12*scale,brand.top,Math.max(logo.right+12*scale,brand.right-8*scale),brand.bottom);
            float infoRight=bar.right-outer,infoLeft=infoRight-infoWidth;
            float clockHeight=clockSize*1.3f,statusHeight=statusSize*1.4f;
            float upperHeight=(showClock?clockHeight:0)+(showStatus&&fullStatus?statusHeight:0)+(showClock&&showStatus&&fullStatus?4*scale:0);
            if(showNetwork&&!fullStatus&&showStatus&&!showClock)upperHeight=12*scale;
            float networkHeight=networkSize*1.6f,infoTop=bar.centerY()-(upperHeight+(showNetwork?networkHeight+(upperHeight>0?4*scale:0):0))/2;
            if(showStatus){if(fullStatus)status.set(infoLeft,infoTop,infoRight,infoTop+statusHeight);else{float cy=showNetwork?infoTop+upperHeight/2:bar.centerY();status.set(infoLeft,cy-6*scale,infoLeft+12*scale,cy+6*scale);}}
            if(showClock){float top=infoTop+(showStatus&&fullStatus?statusHeight+4*scale:0);clock.set(infoRight-clockWidth,top,infoRight,top+clockHeight);}
            if(showNetwork){float top=infoTop+upperHeight+(upperHeight>0?4*scale:0);network.set(infoRight-networkWidth,top,infoRight,top+networkHeight);}
            float start=brand.right+(widths.length>0?between:0),end=infoWidth>0?infoLeft-between:bar.right-outer;
            area.set(start,bar.top,Math.max(start,end),bar.bottom);counterY=bar.bottom-8*scale;
        }else{
            fullStatus=true;brand.set(bar.left+12*scale,bar.top+12*scale,bar.right-12*scale,bar.top+106*scale);
            logo.set(bar.left+24*scale,bar.top+24*scale,bar.left+24*scale+size,bar.top+24*scale+size);
            brandText.set(logo.right+12*scale,brand.top,Math.max(logo.right+12*scale,brand.right-8*scale),brand.bottom);
            float bottom=bar.bottom-20*scale;
            if(showNetwork){network.set(bar.left+24*scale,bottom-networkSize*1.6f,bar.right-24*scale,bottom);bottom=network.top-4*scale;}
            if(showClock)clock.set(bar.left+24*scale,bottom-clockSize*1.3f,bar.right-24*scale,bottom);
            if(showStatus){float end=showClock?clock.top-4*scale:bottom;status.set(bar.left+24*scale,end-statusSize*1.4f,bar.right-24*scale,end);}
            float infoTop=showStatus?status.top:showClock?clock.top:showNetwork?network.top:bar.bottom;
            float end=bar.bottom-((showClock||showStatus)?120:20)*scale;
            if(showNetwork)end=Math.min(end,infoTop-20*scale);
            area.set(bar.left+18*scale,bar.top+140*scale,bar.right-18*scale,end);counterY=area.bottom+17*scale;
        }
        float[] extents=vertical?heights:widths;float room=vertical?area.height():area.width();int count=extents.length;
        offset=Math.max(0,Math.min(previousOffset,count-1));int focused=config.index(selected);
        if(fits(extents,0,room)==count||selected==NavigationConfig.BRAND)offset=0;
        if(focused>=0&&focused<offset)offset=focused;
        int end=fits(extents,offset,room);
        while(focused>=end&&offset<focused){offset++;end=fits(extents,offset,room);}
        float cursor=vertical?area.top:area.left;
        for(int i=offset;i<end;i++){
            float extent=Math.min(room,extents[i]);RectF rect=vertical?new RectF(area.left,cursor,area.right,cursor+extent):new RectF(cursor,bar.centerY()-heights[i]/2,cursor+extent,bar.centerY()+heights[i]/2);
            tabs.add(new Tab(i,rect));cursor+=extent+gap;
        }
    }
    int fits(float[] sizes,int from,float room){if(from>=sizes.length||room<=0)return from;float used=Math.min(room,sizes[from]);int end=from+1;while(end<sizes.length&&used+gap+sizes[end]<=room+.01f){used+=gap+sizes[end++];}return end;}
    float textWidth(String value,float size,boolean bold){measure.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);measure.setTextSize(size);return (float)Math.ceil(measure.measureText(value.replace('\n',' ')))+.5f*scale;}
    RectF selectedRect(NavigationConfig config,int code){for(Tab t:tabs)if(config.code(t.index)==code)return t.rect;return brand;}
    static int mode(SharedPreferences p,Map<String,Object> item){return item.containsKey("display")?((Number)item.get("display")).intValue():p.getInt("nav_display",0);}
    static float textSize(SharedPreferences p,Map<String,Object> item){return item.containsKey("text_size")?((Number)item.get("text_size")).floatValue():p.getInt("nav_text_size",19);}
    static float iconSize(SharedPreferences p,Map<String,Object> item){return item.containsKey("icon_size")?((Number)item.get("icon_size")).floatValue():p.getInt("nav_icon_size",28);}
}
