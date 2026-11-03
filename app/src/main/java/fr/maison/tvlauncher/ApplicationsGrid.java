package fr.maison.tvlauncher;

import android.content.SharedPreferences;

/** Applications tab geometry; independent of home favourites and bounded to the available cells. */
final class ApplicationsGrid {
    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    static int columns(SharedPreferences p) { return clamp(p.getInt("app_grid_columns", 10), 3, 12); }
    static int rows(SharedPreferences p) { return clamp(p.getInt("app_grid_rows", 5), 1, 6); }
    static int labelSize(SharedPreferences p) { return clamp(p.getInt("app_grid_label_size", p.getInt("app_label_size", 15)), 10, 32); }
    static float radius(SharedPreferences p, float cellWidth, float cellHeight) {
        // Legacy icon_radius was capped at 53. Preserve that appearance until explicitly changed.
        int legacyDiameter = 2 * clamp(p.getInt("icon_radius", 62), 24, 53);
        float requested = clamp(p.getInt("app_grid_icon_size", legacyDiameter), 48, 240) / 2f;
        return Math.max(1, Math.min(requested, Math.min(cellWidth * .4f,
            Math.min(cellHeight * .32f, cellHeight * .6f - 28 - labelSize(p)))));
    }
}
