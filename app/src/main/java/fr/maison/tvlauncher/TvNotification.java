package fr.maison.tvlauncher;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

/** Data-only banner, never an Intent, URL, HTML fragment or executable action. */
final class TvNotification {
    final String tag, title, message, position, session;
    final int seconds;
    final boolean dismiss;

    TvNotification(JSONObject value) throws Exception {
        dismiss = "notification_dismiss".equals(value.getString("kind"));
        if (!dismiss && !"notification".equals(value.getString("kind"))) throw new IllegalArgumentException();
        HashSet<String> allowed = new HashSet<>(Arrays.asList(dismiss
            ? new String[]{"kind", "tag", "session"}
            : new String[]{"kind", "tag", "session", "title", "message", "duration", "position"}));
        for (Iterator<String> keys = value.keys(); keys.hasNext();) if (!allowed.contains(keys.next())) throw new IllegalArgumentException();
        tag = string(value, "tag", 64); session = string(value, "session", 32);
        if (!tag.matches("[A-Za-z0-9_-]{1,64}") || !session.matches("[a-f0-9]{32}")) throw new IllegalArgumentException();
        title = dismiss ? "" : string(value, "title", 80);
        message = dismiss ? "" : string(value, "message", 600);
        position = dismiss ? "top_right" : string(value, "position", 20);
        Object duration = dismiss ? 3 : value.get("duration");
        if (!(duration instanceof Integer) || (Integer) duration < 3 || (Integer) duration > 60) throw new IllegalArgumentException();
        seconds = (Integer) duration;
        if (!Arrays.asList("top_right", "top_left", "bottom_right", "bottom_left").contains(position)) throw new IllegalArgumentException();
    }

    static String string(JSONObject value, String key, int max) throws Exception {
        Object raw = value.get(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException();
        String text = ((String) raw).trim();
        if (text.isEmpty() || text.length() > max) throw new IllegalArgumentException();
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) < 32 && text.charAt(i) != '\n' && text.charAt(i) != '\t') throw new IllegalArgumentException();
        return text;
    }

    static boolean allowed(boolean enabled, boolean overlayPermission, boolean interactive,
                           boolean screenOn, boolean dreaming, boolean locked) {
        return enabled && overlayPermission && interactive && screenOn && !dreaming && !locked;
    }
}
