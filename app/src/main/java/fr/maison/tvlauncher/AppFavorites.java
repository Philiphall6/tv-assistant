package fr.maison.tvlauncher;

import android.content.SharedPreferences;
import java.util.LinkedHashSet;
import java.util.Set;

final class AppFavorites {
    private AppFavorites() {}

    /** Preserve the order and favorites temporarily hidden/absent from the app grid. */
    static boolean toggle(SharedPreferences prefs, String pkg) {
        Set<String> order = new LinkedHashSet<>();
        for (String item : prefs.getString("favorite_apps_order", "").split("\\n"))
            if (!item.isEmpty()) order.add(item);
        boolean added = !order.remove(pkg);
        if (added) order.add(pkg);
        prefs.edit().putString("favorite_apps_order", android.text.TextUtils.join("\n", order)).apply();
        return added;
    }
}
