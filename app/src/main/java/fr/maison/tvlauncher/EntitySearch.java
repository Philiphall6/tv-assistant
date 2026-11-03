package fr.maison.tvlauncher;

import java.text.Normalizer;
import java.util.Locale;

/** Accent-insensitive matching shared by the entity picker and its tests. */
final class EntitySearch {
    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[._-]+", " ").trim().replaceAll("\\s+", " ");
    }

    static boolean matches(String normalizedText, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        for (String word : normalizedQuery.split(" ")) {
            if (!normalizedText.contains(word)) return false;
        }
        return true;
    }

    static boolean visible(String normalizedText, String normalizedQuery, boolean selectedOnly, boolean selected) {
        return (!selectedOnly || selected) && matches(normalizedText, normalizedQuery);
    }
}
