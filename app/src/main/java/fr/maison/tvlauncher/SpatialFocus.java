package fr.maison.tvlauncher;

import android.graphics.RectF;
import android.view.KeyEvent;
import java.util.List;

/** Read-only display elements never enter this graph. Coordinates match native drawing. */
final class SpatialFocus {
    static final class Target {
        final int row,col; final RectF rect;
        Target(int row,int col,RectF rect){this.row=row;this.col=col;this.rect=rect;}
    }
    static Target next(List<Target> targets,RectF from,int key){
        boolean horizontal=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
        float sign=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP?-1:1;
        Target best=null;double score=Double.MAX_VALUE;
        for(Target t:targets){float dx=t.rect.centerX()-from.centerX(),dy=t.rect.centerY()-from.centerY();float main=(horizontal?dx:dy)*sign,cross=horizontal?dy:dx;if(main<=1)continue;double s=main*main+4*cross*cross;if(s<score){score=s;best=t;}}
        return best;
    }
}
