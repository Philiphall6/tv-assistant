package fr.maison.tvlauncher;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.AbsListView;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** One focus palette for every remote-controlled settings screen. */
final class TvFocus {
    static final int INK = 0xff111923;
    static final int PAPER = 0xffedf2f5;
    static final int ACCENT = 0xff85dcf2;
    static final int SURFACE = 0xff1b2028;
    static final int ACTIVE_SURFACE = 0xff263e4a;
    private static final int[][] CONTROL_STATES = {
        {-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
        {android.R.attr.state_focused}, {}
    };
    private static final int[][] LIST_STATES = {
        {android.R.attr.state_selected}, {android.R.attr.state_focused},
        {android.R.attr.state_pressed}, {}
    };

    private TvFocus() {}

    static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable surface(View view, int color, int border, int width) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(view, 14));
        if (width > 0) d.setStroke(dp(view, width), border);
        return d;
    }

    static void button(Button button) {
        StateListDrawable background = new StateListDrawable();
        background.addState(CONTROL_STATES[0], surface(button, SURFACE, 0, 0));
        background.addState(CONTROL_STATES[1], surface(button, ACCENT, ACCENT, 2));
        background.addState(CONTROL_STATES[2], surface(button, PAPER, ACCENT, 2));
        background.addState(CONTROL_STATES[3], surface(button, SURFACE, 0xff46515e, 1));
        button.setBackgroundTintList(null);
        button.setBackground(background);
        button.setTextColor(new ColorStateList(CONTROL_STATES,
                new int[]{0xff87909a, INK, INK, PAPER}));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setFocusable(true);
        button.setPadding(dp(button, 18), dp(button, 3), dp(button, 18), dp(button, 3));
    }

    static void listItem(TextView item, int normalColor) {
        StateListDrawable background = new StateListDrawable();
        for (int i = 0; i < LIST_STATES.length - 1; i++) {
            background.addState(LIST_STATES[i], surface(item, PAPER, ACCENT, 2));
        }
        background.addState(LIST_STATES[3], surface(item, normalColor, 0, 0));
        item.setBackgroundTintList(null);
        item.setBackground(background);
        item.setTextColor(new ColorStateList(LIST_STATES, new int[]{INK, INK, INK, PAPER}));
        if (item instanceof CheckedTextView) {
            item.setSingleLine(false);
            item.setMaxLines(2);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setMinHeight(dp(item, 72));
            item.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ((CheckedTextView) item).setCheckMarkTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {android.R.attr.state_focused},
                    {android.R.attr.state_pressed}, {android.R.attr.state_checked}, {}},
                new int[]{INK, INK, INK, ACCENT, 0xffb2bbc6}));
        }
    }

    /** Current category is context, not a second keyboard focus. */
    static void sectionMarker(TextView label) {
        label.setTextColor(PAPER);
        label.setBackground(surface(label, ACTIVE_SURFACE, 0xff54707e, 1));
        label.setFocusable(false);
    }

    static void edit(EditText field) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                surface(field, ACTIVE_SURFACE, ACCENT, 2));
        background.addState(new int[]{}, surface(field, SURFACE, 0xff526171, 1));
        field.setBackgroundTintList(null);
        field.setBackground(background);
        field.setTextColor(PAPER);
        field.setHintTextColor(0xffb5c3ce);
        field.setHighlightColor(0x6685dcf2);
    }

    private static void control(View control) {
        final View card = (View) control.getParent();
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_activated},
                surface(card, ACTIVE_SURFACE, ACCENT, 2));
        background.addState(new int[]{}, surface(card, SURFACE, 0xff46515e, 1));
        card.setBackground(background);
        card.setFocusable(false);
        control.setFocusable(true);
        StringBuilder label = new StringBuilder();
        collectText(card, label);
        if (label.length() > 0) control.setContentDescription(label.toString());
        control.setOnFocusChangeListener((v, focused) -> {
            card.setActivated(focused);
            if (focused) card.post(() -> card.requestRectangleOnScreen(
                    new Rect(0, -dp(card, 8), card.getWidth(), card.getHeight() + dp(card, 8)), false));
        });
        card.setActivated(control.hasFocus());
        if (control instanceof SeekBar) {
            SeekBar seek = (SeekBar) control;
            seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
            seek.setThumbTintList(ColorStateList.valueOf(PAPER));
            seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xff687887));
            // Up/down leave a slider; left/right change its value.
            seek.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    View next = v.focusSearch(key == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
                    if (next != null) next.requestFocus();
                }
                return true;
            });
        }
    }

    private static void collectText(View view, StringBuilder out) {
        if (view instanceof TextView && !(view instanceof Switch)) {
            if (out.length() > 0) out.append(" · ");
            out.append(((TextView) view).getText());
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), out);
        }
    }

    static void prepare(View view) {
        if (view instanceof SeekBar || view instanceof Switch) control(view);
        else if (view instanceof Button) button((Button) view);
        else if (view instanceof EditText) edit((EditText) view);
        else if (view instanceof ListView) {
            ((ListView) view).setSelector(new ColorDrawable(Color.TRANSPARENT));
            return; // Adapter rows are styled in getView, including recycled rows.
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (view instanceof ScrollView) {
                group.setFocusable(false);
                group.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            for (int i = 0; i < group.getChildCount(); i++) prepare(group.getChildAt(i));
        }
    }

    static void focusFirst(View root) {
        List<View> controls = new ArrayList<>();
        collectControls(root, controls);
        if (!controls.isEmpty()) controls.get(0).requestFocus();
    }

    private static void collectControls(View view, List<View> result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view.isEnabled() && (view instanceof Button || view instanceof EditText
                || view instanceof Switch || view instanceof SeekBar || view instanceof ListView)) {
            result.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectControls(group.getChildAt(i), result);
        }
    }

    static int focusedIndex(View root) {
        List<View> controls = new ArrayList<>();
        collectControls(root, controls);
        return controls.indexOf(root.findFocus());
    }

    static void restoreFocus(View root, int index) {
        List<View> controls = new ArrayList<>();
        collectControls(root, controls);
        if (controls.isEmpty()) return;
        View target = controls.get(Math.max(0, Math.min(index, controls.size() - 1)));
        target.requestFocus();
        target.post(() -> target.requestRectangleOnScreen(
                new Rect(0, 0, target.getWidth(), target.getHeight()), true));
    }

    static void dialog(AlertDialog dialog) {
        for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) button(button);
        }
        Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) cancel.requestFocus();
    }
}
