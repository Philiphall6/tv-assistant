package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RectF;

/** Layout coordinates use a 1920 × 1080 design space; the renderer scales to the display. */
final class LauncherLayout {
    final SharedPreferences prefs;
    final RectF content;
    final int edge;
    final float navScale,navSize;
    LauncherLayout(SharedPreferences prefs){
        this.prefs=prefs;edge=number("nav_position",0,0,3);navScale=number("nav_scale",100,70,160)/100f;
        navSize=(edge<2?118:320)*navScale;
        content=new RectF(edge==2?navSize:0,edge==0?navSize:0,edge==3?1920-navSize:1920,edge==1?1080-navSize:1080);
    }
    int number(String key,int def,int min,int max){return Math.max(min,Math.min(max,prefs.getInt(key,def)));}
    RectF navigation(){float w=edge<2?1920*number("nav_width",100,40,100)/100f:navSize;float x=edge<2?(1920-w)*number("nav_alignment",1,0,2)/2f:edge==3?1920-navSize:0;float y=edge==1?1080-navSize:0;return new RectF(x,y,x+w,y+(edge<2?navSize:1080));}
    RectF placed(java.util.Map<String,Object> card){java.util.Map<String,Object> p=CardConfig.map(card.get("placement"),"Placement");float x=((Number)p.get("x")).floatValue()/100f,y=((Number)p.get("y")).floatValue()/100f,w=((Number)p.get("width")).floatValue()/100f,h=((Number)p.get("height")).floatValue()/100f;return new RectF(content.left+content.width()*x,content.top+content.height()*y,content.left+content.width()*(x+w),content.top+content.height()*(y+h));}
    static int color(SharedPreferences prefs,String key,int fallback){try{return Color.parseColor(prefs.getString(key,String.format("#%06X",fallback&0xffffff)));}catch(Exception e){return fallback;}}
    Block block(String key,int defaultY,int defaultWidth,float baseHeight){
        float x=content.left+content.width()*number("layout_"+key+"_x",5,0,90)/100f;
        float y=content.top+content.height()*number("layout_"+key+"_y",defaultY,0,95)/100f;
        float width=Math.max(1,Math.min(content.width()*number("layout_"+key+"_width",defaultWidth,10,100)/100f,content.right-x-6));
        float scale=Math.max(.1f,Math.min(number("layout_"+key+"_scale",100,50,160)/100f,(content.bottom-y-6)/baseHeight));
        return new Block(x,y,width,baseHeight*scale,scale);
    }
    static final class Block {
        final float x,y,width,height,scale;
        Block(float x,float y,float width,float height,float scale){this.x=x;this.y=y;this.width=width;this.height=height;this.scale=scale;}
        float logicalWidth(){return width/scale;}
    }
}
