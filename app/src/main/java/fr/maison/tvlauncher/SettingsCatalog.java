package fr.maison.tvlauncher;

/** Each shared preference belongs to exactly one settings category. */
final class SettingsCatalog {
    static String category(String key) {
        if(key.startsWith("cinema_"))return "Cinéma";
        if (key.startsWith("nav_") || key.startsWith("voice_") || key.equals("show_clock") || key.equals("show_status")) return "Barre";
        if (key.startsWith("wallpaper_") || key.startsWith("photo_album_") || key.equals("background_dim")) return "Fond";
        if (key.startsWith("start_")) return "Démarrage";
        if (key.startsWith("layout_")) return "Accueil";
        if (key.startsWith("app_") || key.startsWith("favorite_") || key.equals("show_app_labels") || key.equals("show_mobile_apps")) return "Applications";
        if (key.startsWith("home_") || key.startsWith("dashboard_") || key.startsWith("card_") || key.equals("show_home") || key.equals("focus_width")) return "Cartes";
        return "Accueil";
    }
    static String group(String key,String declared) {
        if (!declared.isEmpty()) return declared;
        switch (category(key)) {
            case "Applications": return key.startsWith("app_grid_")?"Disposition des applications":"Applications favorites";
            case "Cartes": return key.startsWith("dashboard_")?"Dashboard Maison":"Cartes de la maison";
            default: return "Contenu de l’accueil";
        }
    }
}
