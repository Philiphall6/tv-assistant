package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class LauncherSettingsActivity extends Activity {
    final String[] titles={"Général","Apparence","Rangées de l’accueil","Cartes et icônes","Fond d’écran","Applications et favoris","Home Assistant","Paramètres de la TV","Installation et autorisations","Sauvegarde et réinitialisation","Retour à l’accueil"};
    final String[] descriptions={"Démarrage, horloge et comportement du launcher","Hero, luminosité et ambiance générale","Favoris, reprise de lecture et maison","Forme des icônes, taille, espacement et focus","Photo, dégradé ou noir minimal","Afficher, masquer et organiser les applications","Connexion, appareils et fonctions de la maison","Image, son, réseau, entrées et comptes","Choisir TV Assistant comme écran d’accueil","Conserver ou restaurer la mise en page","Fermer les paramètres du launcher"};
    LinearLayout detail;
    @Override public void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(Color.rgb(31,36,45));LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(82),dp(54),dp(44),dp(38));root.addView(left,new LinearLayout.LayoutParams(0,-1,.54f));TextView heading=text("Paramètres",30,Color.rgb(242,243,245),false);heading.setPadding(dp(16),0,0,dp(28));left.addView(heading);TextView section=text("PARAMÈTRES DU LAUNCHER",10,Color.rgb(169,173,181),true);section.setPadding(dp(16),0,0,dp(9));left.addView(section);ListView list=new ListView(this);list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(dp(3));list.setSelector(new ColorDrawable(Color.TRANSPARENT));ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,titles){@Override public android.view.View getView(int pos,android.view.View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(pos,v,parent);t.setTextSize(15);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(54),0,dp(20),0);t.setMinHeight(dp(54));int[][] states={new int[]{android.R.attr.state_selected},new int[]{android.R.attr.state_focused},new int[]{}};t.setTextColor(new ColorStateList(states,new int[]{Color.rgb(22,25,30),Color.rgb(22,25,30),Color.rgb(238,239,241)}));StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_selected},pill(Color.rgb(239,241,244),14));bg.addState(new int[]{android.R.attr.state_focused},pill(Color.rgb(239,241,244),14));bg.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));TvFocus.listItem(t,Color.TRANSPARENT);return t;}};list.setAdapter(adapter);left.addView(list,new LinearLayout.LayoutParams(-1,0,1));detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);detail.setPadding(dp(58),dp(62),dp(72),dp(45));detail.setBackgroundColor(Color.rgb(31,36,45));root.addView(detail,new LinearLayout.LayoutParams(0,-1,.46f));list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int pos,long id){showDetail(pos);}public void onNothingSelected(AdapterView<?> p){}});list.setOnItemClickListener((p,v,pos,id)->open(pos));TvFocus.prepare(root);setContentView(root);list.requestFocus();list.setSelection(0);showDetail(0);}
    void showDetail(int pos){detail.removeAllViews();TextView title=text(titles[pos],30,Color.rgb(238,239,242),false);title.setPadding(0,0,0,dp(12));detail.addView(title);TextView desc=text(descriptions[pos],14,Color.rgb(160,165,174),false);desc.setPadding(0,0,0,dp(22));detail.addView(desc);String[] rows=preview(pos);for(int i=0;i<rows.length;i++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(4),dp(7),dp(4),dp(7));TextView icon=text(icon(pos,i),16,Color.rgb(183,188,197),true);icon.setGravity(Gravity.CENTER);icon.setBackground(pill(Color.rgb(39,47,61),32));line.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView label=text(rows[i],15,Color.rgb(218,220,224),false);label.setPadding(dp(18),0,0,0);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));detail.addView(line);}}
    String[] preview(int p){switch(p){case 0:return new String[]{"Démarrage","Barre supérieure","Applications Android"};case 1:return new String[]{"Salutation et hero","Luminosité du fond"};case 2:return new String[]{"Applications favorites","Reprendre la lecture","Votre maison"};case 3:return new String[]{"Forme des icônes","Taille et espacement","Coins arrondis","Style du focus"};case 4:return new String[]{"Salon zen","Dégradé bleu-noir","Noir minimal"};case 5:return new String[]{"Applications visibles","Favoris par appui long"};case 6:return new String[]{"Connexion Home Assistant","Appareils et fonctions"};case 7:return new String[]{"Ouvrir les paramètres Android TV"};case 8:return new String[]{"Accueil par défaut","Vérification réelle","Superposition facultative"};case 9:return new String[]{"Sauvegarder","Restaurer","Réinitialiser"};default:return new String[]{"Revenir au launcher"};}}
    String icon(int p,int i){String[] icons={"◆","◐","☷","▣","▧","▦","●","⚙","◇","↥","←"};return icons[p];}
    void open(int pos){if(pos>=0&&pos<=4){Intent i=new Intent(this,AppearanceSettingsActivity.class);i.putExtra("section",pos);startActivity(i);}else if(pos==5)startActivity(new Intent(this,AppManagerActivity.class));else if(pos==6)startActivity(new Intent(this,SettingsActivity.class));else if(pos==7)startActivity(new Intent(Settings.ACTION_SETTINGS));else if(pos==8)requestHome();else if(pos==9){Intent i=new Intent(this,AppearanceSettingsActivity.class);i.putExtra("section",5);startActivity(i);}else finish();}
    void requestHome(){startActivity(new Intent(this,LauncherSetupActivity.class));}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);return t;}
    GradientDrawable pill(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
