package fr.maison.tvlauncher;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.KeyEvent;
import java.util.*;

/** No real application launch/deletion, HA action or preference write. */
final class AppActionsTest {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static KeyEvent event(int action, int key, int repeat, int flags) {
        return new KeyEvent(1000, repeat > 0 ? 1800 : 1020, action, key, repeat, 0, -1, 0, flags);
    }
    static final class RecordingView extends MainActivity.LauncherView {
        int launches;
        RecordingView(MainActivity host) { super(host); }
        @Override void launchApp(MainActivity.AppEntry app) { launches++; }
    }
    static void run(Instrumentation instrumentation) throws Exception {
        PanelRuntimeTest.Memory memory = new PanelRuntimeTest.Memory();
        memory.values.put("favorite_apps_order", "org.hidden.app\norg.sample.one");
        check(AppFavorites.toggle(memory.prefs, "org.sample.two"), "add favorite");
        check(memory.values.get("favorite_apps_order").equals("org.hidden.app\norg.sample.one\norg.sample.two"), "keep hidden favorites and order");
        check(!AppFavorites.toggle(memory.prefs, "org.sample.one"), "remove favorite");
        check(memory.values.get("favorite_apps_order").equals("org.hidden.app\norg.sample.two"), "remove only selected app");
        String pkg = "org.sample.two";
        Intent info = AppActionsDialog.informationIntent(pkg), uninstall = AppActionsDialog.uninstallIntent(pkg);
        check(Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(info.getAction()), "native app information");
        check(Intent.ACTION_UNINSTALL_PACKAGE.equals(uninstall.getAction()), "system uninstall confirmation intent");
        check(info.getDataString().equals("package:" + pkg) && uninstall.getDataString().equals("package:" + pkg), "exact package target");
        check(uninstall.getExtras() == null, "no silent/all-user deletion flags");
        SharedPreferences actual = CardStore.prefs(instrumentation.getTargetContext());
        String savedFavorites = actual.getString("favorite_apps_order", null);
        MainActivity host = (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                RecordingView view = new RecordingView(host);
                view.prefs = memory.prefs;
                MainActivity.AppEntry app = new MainActivity.AppEntry();
                app.pkg = pkg; app.label = "Application test"; app.activity = "Fake";
                view.apps.add(app); view.favoriteApps.add(app); view.resumeApps.add(app);
                Map<String,Object> placed = new LinkedHashMap<>();
                placed.put("type", "app"); placed.put("package", pkg); placed.put("placement", new LinkedHashMap<>());
                view.customCards.add(placed);
                memory.values.put("nav_items", "[{\"id\":\"test\",\"type\":\"app\",\"label\":\"Test\",\"package\":\"org.sample.two\"}]");
                try {
                    for (int row : new int[]{0, 5, 3, 6, 4}) for (int key : new int[]{23, 66, 160}) {
                        view.row = row; view.col = row == 4 ? 5 : 0; view.appsPage = row == 0;
                        int before = view.launches;
                        check(view.selectedApp() == app, "app selected in every context");
                        check(view.onKeyDown(key, event(0,key,0,0)), "short down consumed");
                        check(view.launches == before, "no launch before key release");
                        check(view.onKeyUp(key, event(1,key,0,0)), "short up consumed");
                        check(view.launches == before+1, "short press launches once");
                        view.onKeyDown(key,event(0,key,0,0));
                        view.onKeyDown(key,event(0,key,1,KeyEvent.FLAG_LONG_PRESS));
                        AppActionsDialog dialog = view.appMenu;
                        check(dialog != null && dialog.isShowing(), "long press opens compact menu");
                        check(view.pendingApp == null, "pending launch cleared before dialog");
                        dialog.dispatchKeyEvent(event(0,key,2,KeyEvent.FLAG_LONG_PRESS));
                        dialog.dispatchKeyEvent(event(1,key,0,0));
                        view.onKeyUp(key,event(1,key,0,0));
                        check(dialog.isShowing() && view.launches == before+1, "held OK cannot launch/favorite through dialog");
                        check(memory.values.get("favorite_apps_order").equals("org.hidden.app\norg.sample.two"), "release did not change favorites");
                        dialog.cancel();
                        check(!dialog.isShowing(), "dismiss closes popup");
                        view.onKeyDown(key,event(0,key,0,0));
                        view.onKeyUp(key,event(1,key,0,KeyEvent.FLAG_CANCELED));
                        check(view.launches == before+1, "canceled press does not launch");
                    }
                    view.row=0;view.col=0;view.appsPage=true;
                    view.onKeyDown(23,event(0,23,0,0));
                    check(view.onKeyLongPress(23,event(0,23,1,KeyEvent.FLAG_LONG_PRESS)), "Android tracked long-press supported");
                    check(view.appMenu != null, "tracked long press opens menu");
                    view.dismissAppMenu();
                    int before=view.launches;
                    view.onKeyDown(23,event(0,23,0,0));view.col=1;view.onKeyUp(23,event(1,23,0,0));
                    check(view.launches==before,"selection changes cancel app launch");
                    view.row=5;view.col=0;view.appsPage=false;
                    view.toggleFavorite(app);
                    check(view.favoriteApps.isEmpty()&&view.row==4,"removing final favorite restores reachable navigation");
                    check(memory.values.get("favorite_apps_order").equals("org.hidden.app"),"hidden favorite retained after visible removal");
                } finally {view.dismissAppMenu();view.pause();if(view.hero!=null)view.hero.recycle();}

                int[] calls = new int[4];
                AppActionsDialog.Actions actions = new AppActionsDialog.Actions() {
                    public void favorite(){calls[0]++;} public void information(){calls[1]++;} public void uninstall(){calls[2]++;}
                    public void rename(){calls[3]++;}
                };
                for(int action=0;action<4;action++){
                    AppActionsDialog dialog = new AppActionsDialog(host,"IPTVnaptor TV",action==0,false,true,actions);
                    try{
                        dialog.show();dialog.favorite.requestFocus();check(dialog.favorite.hasFocus(),"first item visibly focused");
                        check(dialog.favorite.getCurrentTextColor()==TvFocus.INK,"focused label remains readable");
                        check(dialog.favorite.getText().toString().equals(action==0?"Retirer des favoris":"Ajouter aux favoris"),"contextual favorite label");
                        dialog.dispatchKeyEvent(event(1,23,0,0));
                        if(action==0)dialog.favorite.performClick();else if(action==1)dialog.information.performClick();else if(action==2)dialog.uninstall.performClick();else dialog.rename.performClick();
                        check(!dialog.isShowing()&&calls[action]==1,"selected action dispatched once and popup closed");
                    }finally{dialog.dismiss();}
                }
                AppActionsDialog system = new AppActionsDialog(host,"Application système",false,true,false,actions);
                try{system.show();check(!system.uninstall.isEnabled()&&system.information.isEnabled(),"system apps protected with info accessible");system.onBackPressed();check(!system.isShowing(),"Back dismisses popup");}finally{system.dismiss();}
                check(Arrays.equals(calls,new int[]{1,1,1,1}),"no action on release/repeat/Back");
            });
            check(Objects.equals(savedFavorites,actual.getString("favorite_apps_order",null)),"real user favorites unchanged");
        } finally { instrumentation.runOnMainSync(host::finish); }
    }
}
