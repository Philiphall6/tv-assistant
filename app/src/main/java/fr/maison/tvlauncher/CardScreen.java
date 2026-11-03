package fr.maison.tvlauncher;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Common TV settings layout, with fixed actions reachable by the remote. */
abstract class CardScreen extends Activity {
    LinearLayout box,footer;TextView status;View root;
    @Override public void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    void screen(String title,String subtitle){LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(0xff141c24);page.setPadding(dp(48),dp(28),dp(48),dp(20));root=page;
        page.addView(text(title,28,Color.WHITE));TextView help=text(subtitle,13,0xffacb9c6);help.setPadding(0,dp(8),0,dp(10));page.addView(help);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(4),dp(4),dp(4),dp(8));scroll.addView(box);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",13,TvFocus.ACCENT);status.setPadding(0,dp(6),0,dp(6));page.addView(status);footer=new LinearLayout(this);page.addView(footer);setContentView(page);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);LinearLayout.LayoutParams lp=parent==footer?new LinearLayout.LayoutParams(0,dp(50),1):new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(3),dp(4),dp(3),dp(4));b.setMinHeight(dp(50));parent.addView(b,lp);TvFocus.button(b);b.setOnClickListener(v->action.run());return b;}
    EditText field(String label,String value,boolean multiline){TextView t=text(label,13,0xffacb9c6);t.setPadding(dp(4),dp(12),0,dp(6));box.addView(t);EditText e=new EditText(this);e.setText(value);e.setTextSize(15);e.setPadding(dp(16),dp(12),dp(16),dp(12));e.setSingleLine(!multiline);if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);}box.addView(e,new LinearLayout.LayoutParams(-1,-2));TvFocus.edit(e);return e;}
    void message(String s){status.setText(s);}
    void confirm(String title,String message,Runnable yes){AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Annuler",null).setPositiveButton("Confirmer",(v,i)->yes.run()).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();}
    void choices(String title,String[] labels,java.util.function.IntConsumer choose){AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setItems(labels,(v,i)->choose.accept(i)).setNegativeButton("Retour",null).create();d.setOnShowListener(v->TvFocus.dialog(d));d.show();}
    int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
