package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText url, token, sceneMovie, sceneOff, light;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences(HaClient.PREFS, MODE_PRIVATE);
        buildUi();
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t;
    }

    private EditText field(LinearLayout box, String title, String key, String fallback, boolean secret) {
        TextView label = text(title, 14, Color.rgb(190,193,200));
        label.setPadding(0, dp(18), 0, dp(7)); box.addView(label);
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(secret?"":prefs.getString(key, fallback));
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.DKGRAY); e.setTextSize(16); e.setPadding(dp(16),0,dp(16),0);
        e.setBackgroundColor(Color.rgb(28,31,38)); e.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setSaveEnabled(false);e.setLongClickable(false);
            if(android.os.Build.VERSION.SDK_INT>=26)e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            e.setHint(TokenStore.get(this).isEmpty()?"Jeton du compte TV non administrateur":"Jeton protégé enregistré · vide = conserver");
        }
        box.addView(e); return e;
    }

    private Button button(String title) {
        Button b = new Button(this); b.setText(title); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(48,51,59)); b.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin=dp(12); b.setLayoutParams(lp); return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(7,9,13));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(76),dp(42),dp(76),dp(50)); scroll.addView(box);
        TextView kicker=text("PARAMÈTRES DU LAUNCHER",13,Color.rgb(243,183,95)); kicker.setLetterSpacing(.12f); box.addView(kicker);
        TextView title=text("TV Assistant",36,Color.WHITE); title.setPadding(0,dp(7),0,dp(5)); box.addView(title);
        box.addView(text("Configuration locale de Home Assistant et du comportement de l’écran d’accueil.",16,Color.rgb(160,164,172)));
        Button homeRow=button("Configurer la rangée Votre maison");homeRow.setBackgroundColor(Color.rgb(3,169,244));homeRow.setTextColor(Color.BLACK);homeRow.setOnClickListener(v->{if(!save())return;startActivity(new Intent(this,HomeRowSettingsActivity.class));});box.addView(homeRow);
        Button entities=button("Choisir les appareils et fonctions affichés");entities.setOnClickListener(v -> { if(!save())return; startActivity(new Intent(this,EntityManagerActivity.class)); });box.addView(entities);
        Button panel=button("Édition depuis Home Assistant · plusieurs TV");panel.setOnClickListener(v->{if(!save())return;startActivity(new Intent(this,PanelSettingsActivity.class));});box.addView(panel);
        Button cards=button("Cartes personnalisées et dashboard natif");cards.setOnClickListener(v->{if(!save())return;startActivity(new Intent(this,CardManagerActivity.class));});box.addView(cards);
        Button webCards=button("Connexion web HA · vraies cartes et tableaux de bord");webCards.setOnClickListener(v->{if(!save())return;startActivity(new Intent(this,HaDashboardActivity.class).putExtra("dashboard_path","/home-assistant-tv-cards/"+PanelStore.identity(this)));});box.addView(webCards);
        url=field(box,"Adresse HTTPS Home Assistant","ha_url","https://homeassistant.example",false);
        token=field(box,"Jeton d’accès longue durée","ha_token","",true);
        sceneMovie=field(box,"Scène soirée cinéma","scene_movie","scene.soiree_cinema",false);
        sceneOff=field(box,"Scène tout éteindre","scene_off","scene.tout_eteindre",false);
        light=field(box,"Lumières du salon","light_entity","light.salon",false);
        box.addView(text("HTTPS et certificat valide obligatoires. Le jeton est chiffré avec Android Keystore et exclu des sauvegardes. Utilisez un compte TV non administrateur.",14,Color.LTGRAY));
        Button forget=button("Effacer le jeton et la session web de cette TV");forget.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Effacer la connexion locale ?").setMessage("Les réglages restent conservés. Le jeton n’est pas révoqué dans HA ; il faudra le ressaisir pour reconnecter cette TV.").setNegativeButton("Annuler",null).setPositiveButton("Effacer",(d,w)->{
            if(TokenStore.clear(this)){token.setText("");token.setHint("Jeton du compte TV non administrateur");android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.WebStorage.getInstance().deleteAllData();Toast.makeText(this,"Connexion locale effacée",Toast.LENGTH_SHORT).show();}
        }).show());box.addView(forget);

        Button save=button("Enregistrer les paramètres"); save.setBackgroundColor(Color.rgb(225,168,78)); save.setTextColor(Color.BLACK); save.setOnClickListener(v -> save()); box.addView(save);
        Button test=button("Tester la connexion Home Assistant"); test.setOnClickListener(v -> { if(!save())return; test.setText("Connexion en cours…"); HaClient.test(this,(ok,msg)->runOnUiThread(()->{test.setText("Tester la connexion Home Assistant"); Toast.makeText(this,ok?"Home Assistant connecté":"Échec : "+msg,Toast.LENGTH_LONG).show();})); }); box.addView(test);
        Button choose=button("Définir TV Assistant comme launcher par défaut"); choose.setOnClickListener(v -> requestHomeRole()); box.addView(choose);
        Button system=button("Ouvrir les paramètres Android TV"); system.setOnClickListener(v -> { try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();} }); box.addView(system);
        Button back=button("Retour à l’accueil"); back.setOnClickListener(v -> finish()); box.addView(back);
        TvFocus.prepare(scroll);setContentView(scroll); homeRow.requestFocus();
    }

    private boolean save() {
        try{
            String base=ServerAddress.normalize(url.getText().toString()),value=token.getText().toString().trim();
            if(value.isEmpty()){
                if(TokenStore.get(this).isEmpty()||!base.equals(ServerAddress.normalize(prefs.getString("ha_url",""))))throw new IllegalArgumentException("Renseignez le jeton pour cette adresse HTTPS.");
            }else TokenStore.save(prefs,base,value);
            prefs.edit().putString("ha_url",base).putString("scene_movie",sceneMovie.getText().toString().trim()).putString("scene_off",sceneOff.getText().toString().trim()).putString("light_entity",light.getText().toString().trim()).apply();
            token.setText("");token.setHint("Jeton protégé enregistré · vide = conserver");
            Toast.makeText(this,"Paramètres enregistrés",Toast.LENGTH_SHORT).show();return true;
        }catch(Exception e){Toast.makeText(this,e instanceof IllegalArgumentException?e.getMessage():"Enregistrement sécurisé impossible. Aucun nouveau jeton n’a été accepté.",Toast.LENGTH_LONG).show();return false;}
    }

    private void requestHomeRole() {
        startActivity(new Intent(this,LauncherSetupActivity.class));
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
