package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class HomeRowSettingsActivity extends Activity {
    SharedPreferences prefs; EditText title,groupEntity; TextView columnsValue;
    @Override public void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);prefs=getSharedPreferences(HaClient.PREFS,MODE_PRIVATE);build();}
    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(0xff1f242d);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(82),dp(54),dp(45),dp(44));root.addView(left,new LinearLayout.LayoutParams(0,-1,.42f));
        TextView main=text("Paramètres",30,Color.WHITE,false);main.setPadding(0,0,0,dp(32));left.addView(main);TextView group=text("RANGÉES DE L’ACCUEIL",10,0xffa9adb5,true);group.setPadding(0,0,0,dp(10));left.addView(group);TextView selected=text("Votre maison",16,0xff17191e,false);selected.setGravity(Gravity.CENTER_VERTICAL);selected.setPadding(dp(24),0,0,0);selected.setBackground(round(0xffeff1f4,14));TvFocus.sectionMarker(selected);left.addView(selected,new LinearLayout.LayoutParams(-1,dp(56)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(0,-1,.58f));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(55),dp(42),dp(72),dp(38));scroll.addView(box,new ScrollView.LayoutParams(-1,-2));box.addView(text("Votre maison",30,Color.WHITE,false));TextView help=text("Choisissez le contenu et l’apparence de cette rangée.",14,0xffa6abb4,false);help.setPadding(0,dp(7),0,dp(14));box.addView(help);
        box.addView(toggle("Afficher la rangée","show_home",true));box.addView(toggle("Piloter le contenu depuis Home Assistant","home_source_ha",false));box.addView(label("GROUPE HOME ASSISTANT"));groupEntity=field(prefs.getString("home_group_entity","group.tv_launcher"));box.addView(groupEntity,new LinearLayout.LayoutParams(-1,dp(52)));TextView groupHelp=text("Pour mélanger lumières, volets et scripts : groupe YAML group.tv_launcher. Pour des lumières seules, un groupe HA light.… convient aussi. Les membres deviennent les cartes de cette rangée.",12,0xffa6abb4,false);groupHelp.setPadding(dp(4),dp(5),0,dp(8));box.addView(groupHelp);box.addView(label("TITRE DE LA RANGÉE"));title=field(prefs.getString("home_row_title","Votre maison"));box.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
        box.addView(label("NOMBRE DE CARTES VISIBLES"));LinearLayout sliderLine=card();columnsValue=text(String.valueOf(prefs.getInt("home_columns",4)),16,0xffffc75d,true);columnsValue.setGravity(Gravity.CENTER);sliderLine.addView(columnsValue,new LinearLayout.LayoutParams(dp(42),-2));SeekBar seek=new SeekBar(this);seek.setMax(2);seek.setProgress(Math.max(0,Math.min(2,prefs.getInt("home_columns",4)-3)));seek.setProgressTintList(ColorStateList.valueOf(0xffffc75d));seek.setThumbTintList(ColorStateList.valueOf(0xffffc75d));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean from){columnsValue.setText(String.valueOf(v+3));prefs.edit().putInt("home_columns",v+3).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});sliderLine.addView(seek,new LinearLayout.LayoutParams(0,dp(42),1));box.addView(sliderLine);
        box.addView(toggle("Afficher l’état des appareils","home_show_states",true));
        Button custom=button("Cartes personnalisées et dashboard natif",false);custom.setOnClickListener(v->startActivity(new Intent(this,CardManagerActivity.class)));box.addView(custom);
        Button entities=button("Modifier la sélection locale",false);entities.setOnClickListener(v->startActivity(new Intent(this,EntityManagerActivity.class)));box.addView(entities);Button done=button("Enregistrer et revenir",true);done.setOnClickListener(v->{String value=title.getText().toString().trim(),groupId=groupEntity.getText().toString().trim();prefs.edit().putString("home_row_title",value.isEmpty()?"Votre maison":value).putString("home_group_entity",groupId.isEmpty()?"group.tv_launcher":groupId).apply();finish();});box.addView(done);TvFocus.prepare(root);setContentView(root);TvFocus.focusFirst(root);
    }
    LinearLayout toggle(String name,String key,boolean def){LinearLayout row=card();row.addView(text(name,15,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(prefs.getBoolean(key,def));sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffffc75d,Color.GRAY}));sw.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean(key,on).apply());row.addView(sw);return row;}
    LinearLayout card(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(8),dp(16),dp(8));row.setBackground(round(0xff191e26,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.bottomMargin=dp(7);row.setLayoutParams(lp);return row;}
    TextView label(String s){TextView t=text(s,10,0xffffc75d,true);t.setPadding(dp(4),dp(11),0,dp(6));return t;}
    EditText field(String value){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextSize(16);e.setTextColor(Color.WHITE);e.setPadding(dp(18),0,dp(18),0);e.setBackground(round(0xff191e26,14));return e;}
    Button button(String s,boolean accent){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(accent?0xff17191e:Color.WHITE);StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},round(0xffeff1f4,14));d.addState(new int[]{},round(accent?0xffffc75d:0xff191e26,14));b.setBackground(d);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.bottomMargin=dp(7);b.setLayoutParams(lp);return b;}
    TextView text(String s,int z,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(color);t.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);return t;}
    GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
