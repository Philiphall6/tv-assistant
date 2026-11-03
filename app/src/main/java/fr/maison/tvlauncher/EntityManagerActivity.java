package fr.maison.tvlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EntityManagerActivity extends Activity {
    static class Row {
        String id, name, state, domain, searchable;
        public String toString() { return name + "\n" + id + "  ·  " + state; }
    }
    private final List<Row> rows = new ArrayList<>(), visibleRows = new ArrayList<>();
    // Selection is keyed by entity ID, never by a position in filtered results.
    private final Set<String> selected = new LinkedHashSet<>();
    private ListView list;
    private ArrayAdapter<Row> adapter;
    private ProgressBar progress;
    private TextView status;
    private EditText search;
    private Button save, cancel, clear, selections;
    private SharedPreferences prefs;
    private boolean loaded, selectedOnly;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(HaClient.PREFS, MODE_PRIVATE);
        ArrayList<String> pending = state == null ? null : state.getStringArrayList("selected_entities");
        if (pending != null) selected.addAll(pending);
        else {
            for (String id : prefs.getString("dashboard_entities_order", "").split("\n"))
                if (!id.isEmpty()) selected.add(id);
            Set<String> saved = prefs.getStringSet("dashboard_entities", null);
            if (saved != null) selected.addAll(saved);
            else if (selected.isEmpty()) {
                selected.add(prefs.getString("light_entity", "light.salon"));
                selected.add(prefs.getString("scene_movie", "scene.soiree_cinema"));
                selected.add(prefs.getString("scene_off", "scene.tout_eteindre"));
            }
        }
        selectedOnly = state != null && state.getBoolean("selected_only", false);
        buildUi();
        if (state != null) search.setText(state.getString("search_query", ""));
        load();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putStringArrayList("selected_entities", new ArrayList<>(selected));
        state.putString("search_query", search.getText().toString());
        state.putBoolean("selected_only", selectedOnly);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String label, int size, int color) {
        TextView view = new TextView(this);
        view.setText(label);view.setTextSize(size);view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);button.setTextSize(15);button.setTextColor(Color.WHITE);
        button.setAllCaps(false);button.setFocusable(true);button.setId(View.generateViewId());
        return button;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(64), dp(30), dp(64), dp(28));
        root.setBackgroundColor(0xff1f242d);
        root.addView(text("APPAREILS ET FONCTIONS", 12, TvFocus.ACCENT));
        TextView title = text("Choisir les cartes de l’accueil", 28, Color.WHITE);
        title.setPadding(0, dp(4), 0, dp(4));root.addView(title);
        root.addView(text("← Recherche   ·   → Retour / Enregistrer   ·   OK Cocher / décocher", 13, 0xffa6abb4));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(50));
        searchParams.topMargin = dp(14);root.addView(searchRow, searchParams);
        search = new EditText(this);
        search.setId(View.generateViewId());search.setSingleLine(true);search.setTextSize(16);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setTextColor(Color.WHITE);search.setHintTextColor(0xffa6abb4);
        search.setHint("Rechercher un nom ou un identifiant…");
        search.setContentDescription("Rechercher les cartes par nom ou identifiant d’entité");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
        clear = button("Effacer");
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(120), -1);
        clearParams.leftMargin = dp(10);searchRow.addView(clear, clearParams);
        clear.setOnClickListener(view -> { search.setText("");search.requestFocus(); });
        selections = button("Les sélections");
        LinearLayout.LayoutParams selectionParams = new LinearLayout.LayoutParams(dp(200), -1);
        selectionParams.leftMargin = dp(10);searchRow.addView(selections, selectionParams);
        selections.setOnClickListener(view -> {
            selectedOnly = !selectedOnly;
            filter();
            list.setSelection(0);
        });
        updateSelectionButton();

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressParams.gravity = Gravity.CENTER;progressParams.topMargin = dp(10);
        root.addView(progress, progressParams);
        status = text("Chargement depuis Home Assistant…", 13, 0xffbec1c7);
        status.setPadding(0, dp(8), 0, dp(8));root.addView(status);

        list = new ListView(this);
        list.setId(View.generateViewId());list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setDividerHeight(dp(3));list.setBackgroundColor(0xff1f242d);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new ArrayAdapter<Row>(this, android.R.layout.simple_list_item_multiple_choice, visibleRows) {
            @Override public View getView(int position, View convert, android.view.ViewGroup parent) {
                CheckedTextView view = (CheckedTextView) super.getView(position, convert, parent);
                view.setTextColor(Color.WHITE);view.setTextSize(15);
                view.setPadding(dp(18), dp(10), dp(18), dp(10));
                TvFocus.listItem(view, TvFocus.SURFACE);
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String entity = visibleRows.get(position).id;
            if (list.isItemChecked(position)) selected.add(entity);else selected.remove(entity);
            if (selectedOnly) {
                filter();
                if (visibleRows.isEmpty()) selections.requestFocus();
                else list.setSelection(Math.min(position, visibleRows.size() - 1));
            } else updateStatus();
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);actions.setPadding(0, dp(10), 0, 0);
        cancel = button("Retour");save = button("Enregistrer la sélection");
        save.setEnabled(false);
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(180), dp(48)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(280), dp(48));
        saveParams.leftMargin = dp(12);actions.addView(save, saveParams);root.addView(actions);
        cancel.setOnClickListener(view -> finish());save.setOnClickListener(view -> saveSelection());
        list.setNextFocusUpId(search.getId());list.setNextFocusDownId(save.getId());
        list.setNextFocusLeftId(search.getId());list.setNextFocusRightId(cancel.getId());
        list.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                search.requestFocus();search.setSelection(search.length());return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { cancel.requestFocus();return true; }
            return false;
        });
        cancel.setNextFocusLeftId(search.getId());cancel.setNextFocusRightId(save.getId());
        save.setNextFocusLeftId(cancel.getId());save.setNextFocusRightId(save.getId());
        search.setNextFocusDownId(list.getId());clear.setNextFocusDownId(list.getId());
        search.setNextFocusRightId(clear.getId());clear.setNextFocusLeftId(search.getId());
        clear.setNextFocusRightId(selections.getId());selections.setNextFocusLeftId(clear.getId());
        selections.setNextFocusDownId(list.getId());
        search.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
                focusResults();return true;
            }
            return false;
        });
        search.setOnKeyListener((view, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                focusResults();return true;
            }
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            public void onTextChanged(CharSequence value, int start, int before, int count) { filter(); }
            public void afterTextChanged(Editable value) { }
        });
        TvFocus.prepare(root);setContentView(root);search.requestFocus();
    }

    private void focusResults() {
        if (!visibleRows.isEmpty()) { list.requestFocus();list.setSelection(0); }
        else if (save.isEnabled()) save.requestFocus();
    }

    private void filter() {
        String query = EntitySearch.normalize(search.getText().toString());
        visibleRows.clear();
        for (Row row : rows)
            if (EntitySearch.visible(row.searchable, query, selectedOnly, selected.contains(row.id))) visibleRows.add(row);
        list.clearChoices();adapter.notifyDataSetChanged();
        for (int i = 0; i < visibleRows.size(); i++) list.setItemChecked(i, selected.contains(visibleRows.get(i).id));
        int next = visibleRows.isEmpty() ? save.getId() : list.getId();
        search.setNextFocusDownId(next);clear.setNextFocusDownId(next);selections.setNextFocusDownId(next);
        int previous = visibleRows.isEmpty() ? search.getId() : list.getId();
        cancel.setNextFocusUpId(previous);save.setNextFocusUpId(previous);
        updateSelectionButton();
        if (loaded) updateStatus();
    }

    private void updateSelectionButton() {
        selections.setText(selectedOnly ? "Toutes les cartes" : "Les sélections (" + selected.size() + ")");
        selections.setContentDescription(selectedOnly ? "Afficher toutes les cartes"
                : "Afficher uniquement les " + selected.size() + " cartes sélectionnées");
    }

    private void updateStatus() {
        updateSelectionButton();
        String results;
        if (selectedOnly) {
            results = selected.isEmpty() ? "Aucune carte sélectionnée"
                    : visibleRows.isEmpty() ? "Aucune sélection ne correspond à la recherche"
                    : "Les sélections : " + visibleRows.size() + " carte(s) affichée(s)";
        } else results = visibleRows.isEmpty() ? "Aucun résultat" : visibleRows.size() + " résultat(s) sur " + rows.size();
        status.setText(results + "  ·  " + selected.size() + " carte(s) sélectionnée(s)");
    }

    private void saveSelection() {
        // Keep the existing order, including selected entities temporarily unavailable in HA.
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String id : prefs.getString("dashboard_entities_order", "").split("\n"))
            if (selected.contains(id)) order.add(id);
        for (Row row : rows) if (selected.contains(row.id)) order.add(row.id);
        order.addAll(selected);
        prefs.edit().putStringSet("dashboard_entities", new HashSet<>(selected))
                .putString("dashboard_entities_order", String.join("\n", order)).apply();
        Toast.makeText(this, selected.size() + " carte(s) enregistrée(s)", Toast.LENGTH_SHORT).show();finish();
    }

    private void load() {
        HaClient.getStates(this, (ok, data, message) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            progress.setVisibility(View.GONE);
            if (!ok) { status.setText("Impossible de charger : " + message);return; }
            try {
                rows.clear();
                Set<String> available = new HashSet<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject object = data.getJSONObject(i);
                    Row row = new Row();row.id = object.optString("entity_id");
                    if (row.id.isEmpty()) continue;
                    row.domain = row.id.contains(".") ? row.id.substring(0, row.id.indexOf('.')) : "autre";
                    row.state = object.optString("state");
                    JSONObject attributes = object.optJSONObject("attributes");
                    row.name = attributes != null ? attributes.optString("friendly_name", row.id) : row.id;
                    row.searchable = EntitySearch.normalize(row.name + " " + row.id + " " + row.domain);
                    rows.add(row);available.add(row.id);
                }
                // Saved entities removed from HA must remain visible and removable in selections.
                for (String id : selected) if (!available.contains(id)) {
                    Row missing = new Row();missing.id = id;missing.name = id;
                    missing.domain = id.contains(".") ? id.substring(0, id.indexOf('.')) : "autre";
                    missing.state = "Introuvable dans Home Assistant";
                    missing.searchable = EntitySearch.normalize(id);
                    rows.add(missing);
                }
                Collections.sort(rows, (a, b) -> {
                    int domain = a.domain.compareToIgnoreCase(b.domain);
                    return domain != 0 ? domain : a.name.compareToIgnoreCase(b.name);
                });
                loaded = true;save.setEnabled(true);filter();
            } catch (Exception exception) { status.setText("Erreur de lecture : " + exception.getMessage()); }
        }));
    }
}
