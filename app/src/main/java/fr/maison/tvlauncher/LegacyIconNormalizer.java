package fr.maison.tvlauncher;

import android.graphics.Bitmap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Remove a low-contrast, baked-in outer matte around a legacy icon's solid tile.
 * The glyph is never scaled or recolored. Photos, gradients, sparse glyphs and
 * high-contrast artwork extending outside the tile are deliberately preserved. */
final class LegacyIconNormalizer {
    static void flattenMatte(Bitmap bitmap) {
        int w=bitmap.getWidth(),h=bitmap.getHeight(),n=w*h;
        int[] pixels=new int[n];bitmap.getPixels(pixels,0,w,0,0,w,h);
        Set<Integer> candidates=new LinkedHashSet<>();
        // Probe the interior perimeter, not the central logo or outer padding.
        for(float t:new float[]{.18f,.25f,.5f,.75f,.82f})for(float edge:new float[]{.18f,.25f,.75f,.82f}){
            int a=pixels[Math.min(h-1,(int)(edge*h))*w+Math.min(w-1,(int)(t*w))];
            int b=pixels[Math.min(h-1,(int)(t*h))*w+Math.min(w-1,(int)(edge*w))];
            if((a>>>24)>=250)candidates.add(a|0xff000000);
            if((b>>>24)>=250)candidates.add(b|0xff000000);
        }
        // Rich imagery is not a framed flat tile; avoid both alteration and costly scans.
        if(candidates.size()>16)return;
        boolean[] best=null;int bestColor=0,bestCount=0;
        int[] queue=new int[n];boolean[] outside=new boolean[n];
        for(int color:candidates){
            int solid=0,minX=w,minY=h,maxX=0,maxY=0;
            for(int i=0;i<n;i++)if(plate(pixels[i],color)){
                solid++;int x=i%w,y=i/w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
            if(solid<n*.25f||maxX-minX<w*.65f||maxY-minY<h*.65f)continue;
            java.util.Arrays.fill(outside,false);int tail=0;
            for(int i=0;i<n;i++)if((i<w||i>=n-w||i%w==0||i%w==w-1)&&!plate(pixels[i],color)){
                outside[i]=true;queue[tail++]=i;
            }
            for(int head=0;head<tail;head++){
                int i=queue[head],x=i%w;
                if(x>0)tail=visit(i-1,pixels,color,outside,queue,tail);
                if(x<w-1)tail=visit(i+1,pixels,color,outside,queue,tail);
                if(i>=w)tail=visit(i-w,pixels,color,outside,queue,tail);
                if(i<n-w)tail=visit(i+w,pixels,color,outside,queue,tail);
            }
            if(tail>n*.5f||tail<=bestCount)continue;
            boolean safe=true;int matte=0;
            for(int i=0;i<n&&safe;i++)if(outside[i]){
                int x=i%w,y=i/w;
                if(x>=w*.3f&&x<w*.7f&&y>=h*.3f&&y<h*.7f){safe=false;break;}
                if((pixels[i]>>>24)>=240){matte++;if(distance(pixels[i],color)>48)safe=false;}
            }
            // At least a real opaque frame, not the transparent space around a glyph.
            if(safe&&matte>=n*.02f){best=outside.clone();bestColor=color;bestCount=tail;}
        }
        if(best!=null){for(int i=0;i<n;i++)if(best[i])pixels[i]=bestColor;bitmap.setPixels(pixels,0,w,0,0,w,h);}
    }

    private static int visit(int i,int[] pixels,int color,boolean[] outside,int[] queue,int tail){
        if(!outside[i]&&!plate(pixels[i],color)){outside[i]=true;queue[tail++]=i;}return tail;
    }
    private static boolean plate(int pixel,int color){return (pixel>>>24)>=250&&distance(pixel,color)<=2;}
    private static int distance(int a,int b){return Math.max(Math.abs((a>>16&255)-(b>>16&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));}
}
