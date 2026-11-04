package fr.maison.tvlauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.Display;

public final class DisplayToolsActivity extends CardScreen {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        screen(I18n.t("Écran et HDR"),I18n.t("Formats déclarés par Android, pas le mode actif d’une vidéo ni une garantie pour les services de streaming."));
        int[] types=HdrCapabilities.types(this);
        box.addView(text(HdrCapabilities.summary(types),22,0xffa5d6d2));
        box.addView(text(I18n.t("Mires HDR10 locales : HEVC 10 bits, BT.2020 et PQ. Elles servent au contrôle visuel, pas à une calibration certifiée. Aucun réglage d’image n’est changé. Dolby Vision et HDR10+ n’ont pas de mire dédiée ici."),15,0xffc7d1da));
        boolean supported=HdrCapabilities.has(types,Display.HdrCapabilities.HDR_TYPE_HDR10);
        button(box,I18n.t("Mire HDR10 · détails dans les noirs"),()->pattern(0)).setEnabled(supported);
        button(box,I18n.t("Mire HDR10 · hautes lumières"),()->pattern(1)).setEnabled(supported);
        if(!supported)box.addView(text(I18n.t("Mires désactivées : Android ne déclare pas de sortie HDR10 sur cet écran."),14,0xffc7d1da));
        box.addView(text(I18n.t("Chaque mire s’arrête après 12 secondes. Retour permet de quitter immédiatement. Une capture d’écran SDR ne permet pas de juger son rendu HDR."),14,0xffc7d1da));
        button(footer,I18n.t("Retour"),this::finish);TvFocus.focusFirst(root);
    }
    void pattern(int kind){
        String levels=kind==0?"0 · 0,005 · 0,01 · 0,025 · 0,05 · 0,1 · 0,5 · 1 · 2 · 5 cd/m²":"10 · 100 · 203 · 400 · 600 · 800 · 1000 cd/m²";
        confirm(I18n.t("Lancer la mire HDR10 ?"),I18n.t("De gauche à droite, niveaux encodés :")+"\n"+levels+"\n"+I18n.t("La TV adapte ces niveaux selon ses capacités. Évitez une exposition prolongée aux images fixes."),
            ()->startActivity(new Intent(this,HdrPatternActivity.class).putExtra("pattern",kind)));
    }
}
