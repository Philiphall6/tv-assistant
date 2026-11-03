package fr.maison.tvlauncher;

/** The system recreates activities when their language changes. */
public final class TvApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();I18n.init(this);}
}
