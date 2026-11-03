package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;
import org.json.JSONArray;

/** Remote-friendly palette and RGB editor. Cancel never changes the wallpaper. */
public class WallpaperColorActivity extends CardScreen {
    private int selected;
    private boolean syncing;
    private TextView preview;
    private EditText hex;
    private final SeekBar[] channels=new SeekBar[3];
    private final TextView[] values=new TextView[3];
    @Override public void onCreate(Bundle state){super.onCreate(state);
        SharedPreferences prefs=CardStore.prefs(this);
        selected=WallpaperBackdrop.color(state==null?prefs.getString("wallpaper_color",WallpaperBackdrop.DEFAULT_COLOR):state.getString("color"));
        screen(I18n.t("Couleur du fond d’écran"),I18n.t("Choisissez une teinte ou créez la vôtre. Les changements ne sont appliqués qu’avec Utiliser cette couleur."));
        preview=text("",18,Color.WHITE);preview.setGravity(Gravity.CENTER);box.addView(preview,new LinearLayout.LayoutParams(-1,dp(62)));
        try{
            JSONArray presets=PanelStore.schema(this).getJSONObject("wallpaper_color").getJSONArray("presets");
            LinearLayout row=null;
            for(int i=0;i<presets.length();i++){
                if(i%4==0){row=new LinearLayout(this);box.addView(row);}
                JSONArray preset=presets.getJSONArray(i);int color=Color.parseColor(preset.getString(0));
                Button b=button(row,I18n.t(preset.getString(1)),()->setColor(color,true));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));b.setLayoutParams(lp);
                GradientDrawable chip=new GradientDrawable();chip.setColor(color);chip.setCornerRadius(dp(5));chip.setStroke(dp(1),0xff82929e);chip.setBounds(0,0,dp(20),dp(20));
                b.setCompoundDrawables(chip,null,null,null);b.setCompoundDrawablePadding(dp(8));b.setContentDescription(I18n.t(preset.getString(1))+", "+preset.getString(0));
            }
        }catch(Exception e){message(I18n.t("La palette est indisponible ; la couleur personnalisée reste utilisable."));}
        hex=field(I18n.t("Couleur personnalisée · #RRGGBB"),format(selected),false);hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!syncing&&s.toString().matches("#[0-9a-fA-F]{6}")){hex.setError(null);setColor(Color.parseColor(s.toString()),false);}}public void afterTextChanged(Editable e){}});
        String[] names={I18n.t("Rouge"),I18n.t("Vert"),I18n.t("Bleu")};
        for(int i=0;i<3;i++){
            final int index=i;values[i]=text("",13,0xffc7d1da);box.addView(values[i]);
            SeekBar slider=new SeekBar(this);channels[i]=slider;slider.setMax(255);slider.setContentDescription(names[i]);slider.setFocusable(true);slider.setProgressTintList(ColorStateList.valueOf(TvFocus.ACCENT));slider.setThumbTintList(ColorStateList.valueOf(TvFocus.ACCENT));
            box.addView(slider,new LinearLayout.LayoutParams(-1,dp(35)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&!syncing){int shift=(2-index)*8;setColor((selected&~(255<<shift))|(value<<shift),true);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        }
        box.addView(text(I18n.t("Fond uniforme : les dégradés des photos ne s’appliquent pas. L’assombrissement reste réglable dans Fond d’écran. Pour une teinte claire, adaptez aussi la couleur des textes."),12,0xffacb9c6));
        if(PanelStore.enabled(this))box.addView(text(I18n.t("Profil HA actif : utilisez également le réglage Fond d’écran dans HA pour le synchroniser entre vos TV."),12,0xffacb9c6));
        button(footer,I18n.t("Annuler"),this::finish);
        button(footer,I18n.t("Utiliser cette couleur"),()->{
            String raw=hex.getText().toString().trim();
            if(!raw.matches("#[0-9a-fA-F]{6}")){hex.setError(I18n.t("Saisissez # suivi de 6 caractères hexadécimaux"));hex.requestFocus();return;}
            prefs.edit().putString("wallpaper_color",raw.toUpperCase(Locale.ROOT)).putInt("wallpaper_mode",WallpaperBackdrop.SOLID).apply();setResult(RESULT_OK);finish();
        });
        setColor(selected,true);TvFocus.focusFirst(root);
    }
    private static String format(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    private void setColor(int color,boolean updateHex){
        selected=color|0xff000000;syncing=true;
        preview.setBackgroundColor(selected);preview.setText(I18n.t("Aperçu · ")+format(selected));
        preview.setTextColor(Color.red(selected)*.299+Color.green(selected)*.587+Color.blue(selected)*.114>155?0xff101820:Color.WHITE);
        if(updateHex){hex.setText(format(selected));hex.setError(null);}
        int[] rgb={Color.red(selected),Color.green(selected),Color.blue(selected)};String[] names={I18n.t("Rouge"),I18n.t("Vert"),I18n.t("Bleu")};
        for(int i=0;i<3;i++)if(channels[i]!=null){channels[i].setProgress(rgb[i]);values[i].setText(names[i]+" · "+rgb[i]);}
        syncing=false;
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("color",format(selected));}
}
