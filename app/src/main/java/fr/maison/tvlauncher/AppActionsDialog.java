package fr.maison.tvlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Small, remote-friendly app menu. Destructive operations always belong to Android. */
final class AppActionsDialog extends Dialog {
    interface Actions {
        void favorite();
        void information();
        void uninstall();
    }

    final Button favorite, information, uninstall;
    private boolean waitingForRelease;

    AppActionsDialog(Activity activity, String label, boolean isFavorite, boolean systemApp,
                     boolean fromHeldKey, Actions actions) {
        super(activity);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        waitingForRelease = fromHeldKey;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(16));
        content.setBackground(TvFocus.surface(content, 0xff161c24, 0xff435362, 1));
        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(TvFocus.PAPER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(8), 0, dp(8), dp(14));
        content.addView(title);
        favorite = button(content, isFavorite ? "Retirer des favoris" : "Ajouter aux favoris", actions::favorite);
        information = button(content, "Informations sur l’application", actions::information);
        uninstall = button(content, "Désinstaller", actions::uninstall);
        uninstall.setEnabled(!systemApp);
        if (systemApp) {
            TextView note = new TextView(activity);
            note.setText("Application système · désinstallation indisponible");
            note.setTextSize(12);
            note.setTextColor(0xffb1bdc8);
            note.setPadding(dp(8), dp(10), dp(8), 0);
            content.addView(note);
        }
        setContentView(content);
        setCanceledOnTouchOutside(true);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.55f);
        window.setGravity(Gravity.CENTER);
        favorite.setFocusableInTouchMode(true);
        favorite.requestFocus();
        setOnShowListener(d -> {
            int maxWidth = activity.getResources().getDisplayMetrics().widthPixels - dp(40);
            window.setLayout(Math.min(dp(370), maxWidth), WindowManager.LayoutParams.WRAP_CONTENT);
            favorite.requestFocus();
        });
    }

    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }

    private Button button(LinearLayout content, String label, Runnable action) {
        Button button = new Button(getContext());
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        TvFocus.button(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(6);
        content.addView(button, params);
        button.setOnClickListener(v -> { dismiss(); action.run(); });
        return button;
    }

    static boolean isConfirm(int key) { return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER; }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // The release/repeats of the OK which opened us must never select the first action.
        if (waitingForRelease && isConfirm(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP) { waitingForRelease = false; return true; }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) waitingForRelease = false;
            else return true;
        }
        return super.dispatchKeyEvent(event);
    }

    static boolean systemApp(Activity activity, String pkg) {
        try { return (activity.getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0; }
        catch (Exception missing) { return true; }
    }

    static Intent informationIntent(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
    }

    @SuppressWarnings("deprecation")
    static Intent uninstallIntent(String pkg) {
        // Starts Android's confirmation UI; no privileged/silent package deletion.
        return new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", pkg, null));
    }

    static void open(Activity activity, Intent intent) {
        try { activity.startActivity(intent); }
        catch (Exception unavailable) { Toast.makeText(activity, "Action indisponible sur cet appareil", Toast.LENGTH_LONG).show(); }
    }
}
