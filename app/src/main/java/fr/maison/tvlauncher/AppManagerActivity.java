package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.drawable.ColorDrawable;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppManagerActivity extends Activity {
    static class Row{String label,pkg;public String toString(){return label+"\n"+pkg;}}
    List<Row> rows=new ArrayList<>();Set<String> hidden=new HashSet<>();ListView list;SharedPreferences prefs;
    @Override public void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);Window win=getWindow();win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams wp=win.getAttributes();wp.width=(int)(getResources().getDisplayMetrics().widthPixels*.54f);wp.height=WindowManager.LayoutParams.MATCH_PARENT;wp.gravity=Gravity.END;wp.dimAmount=.5f;win.setAttributes(wp);prefs=getSharedPreferences(HaClient.PREFS,MODE_PRIVATE);hidden.addAll(prefs.getStringSet("hidden_apps",Collections.emptySet()));load();build();}
    void load(){PackageManager pm=getPackageManager();Set<String> seen=new HashSet<>();for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);for(ResolveInfo r:pm.queryIntentActivities(q,0)){String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||!seen.add(pkg))continue;Row row=new Row();row.pkg=pkg;row.label=r.loadLabel(pm).toString();rows.add(row);}}rows.sort((a,b)->a.label.compareToIgnoreCase(b.label));}
    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(34),dp(30),dp(34),dp(28));root.setBackgroundColor(Color.rgb(14,17,22));TextView title=new TextView(this);title.setText(I18n.t("APPLICATIONS ET FAVORIS"));title.setTextColor(Color.rgb(255,199,93));title.setTextSize(13);root.addView(title);TextView info=new TextView(this);info.setText(I18n.t("Applications visibles"));info.setTextColor(Color.WHITE);info.setTextSize(30);info.setPadding(0,dp(5),0,dp(5));root.addView(info);TextView help=new TextView(this);help.setText(I18n.t("Décochez une application pour la masquer. Les favoris se choisissent par appui long dans l’onglet Applications."));help.setTextColor(Color.rgb(166,171,180));help.setTextSize(14);help.setPadding(0,0,0,dp(14));root.addView(help);Button layout=new Button(this);layout.setText(I18n.t("Disposition des applications"));layout.setAllCaps(false);layout.setOnClickListener(v->startActivity(new Intent(this,AppearanceSettingsActivity.class).putExtra("section",6)));root.addView(layout);list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);list.setDivider(new ColorDrawable(Color.rgb(14,17,22)));list.setDividerHeight(dp(4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);list.setLayoutParams(lp);ArrayAdapter<Row>a=new ArrayAdapter<Row>(this,android.R.layout.simple_list_item_multiple_choice,rows){@Override public android.view.View getView(int p,android.view.View v,android.view.ViewGroup parent){CheckedTextView t=(CheckedTextView)super.getView(p,v,parent);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(dp(18),dp(10),dp(18),dp(10));TvFocus.listItem(t,TvFocus.SURFACE);return t;}};list.setAdapter(a);for(int i=0;i<rows.size();i++)list.setItemChecked(i,!hidden.contains(rows.get(i).pkg));root.addView(list);Button save=new Button(this);save.setText(I18n.t("Enregistrer"));save.setTextSize(17);save.setAllCaps(false);save.setBackgroundColor(Color.rgb(255,199,93));save.setTextColor(Color.rgb(18,20,24));save.setOnClickListener(v->{hidden.clear();for(int i=0;i<rows.size();i++)if(!list.isItemChecked(i))hidden.add(rows.get(i).pkg);prefs.edit().putStringSet("hidden_apps",new HashSet<>(hidden)).apply();Toast.makeText(this,I18n.t("Applications mises à jour"),Toast.LENGTH_SHORT).show();finish();});root.addView(save);TvFocus.prepare(root);setContentView(root);list.requestFocus();}
    int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
