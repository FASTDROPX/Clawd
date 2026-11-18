package com.clawd.pet;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ExcludedAppsActivity extends AppCompatActivity {
    private record App(String name, String pkg, ResolveInfo info) {}
    private final List<App> apps = new ArrayList<>();
    private final List<App> filtered = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private SharedPreferences prefs;
    private TextView count;
    private TextView empty;
    private MaterialButton grant;
    private MaterialButton selectAll;
    private MaterialButton clear;
    private AppsAdapter adapter;
    private RecyclerView list;
    private String query = "";
    private boolean loaded;

    @Override protected void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 34) {
            UiMotion.pageTransition(this, true);
            UiMotion.pageTransition(this, false);
        }
        prefs = PetService.prefs(this);
        selected.addAll(prefs.getStringSet(ForegroundApps.EXCLUDED, Collections.emptySet()));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            var keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(dp(20) + bars.left, dp(8) + bars.top, dp(20) + bars.right,
                    dp(8) + Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        setContentView(root);

        MaterialButton back = button("Готово");
        back.setOnClickListener(view -> finish());
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        top.addView(back);
        root.addView(top);
        TextView title = label("Скрывать\nв приложениях", 33);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ViewCompat.setAccessibilityHeading(title, true);
        root.addView(title);
        count = label("", 14);
        root.addView(count);
        grant = button("Доступ к данным об использовании");
        grant.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (ActivityNotFoundException e) {
                UiMotion.notice(root, "Настройки доступа недоступны");
            }
        });
        root.addView(grant, new LinearLayout.LayoutParams(-1, -2));

        TextInputLayout field = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint("Поиск приложений");
        field.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText search = new TextInputEditText(field.getContext());
        search.setSingleLine(true);
        field.addView(search, new LinearLayout.LayoutParams(-1, -2));
        var fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = dp(12);
        root.addView(field, fieldParams);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                filter();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        LinearLayout bulk = new LinearLayout(this);
        selectAll = button("Выбрать все");
        selectAll.setOnClickListener(view -> {
            for (App app : filtered) selected.add(app.pkg());
            persist();
        });
        clear = button("Снять выбор");
        clear.setOnClickListener(view -> {
            if (query.isEmpty()) selected.clear();
            else for (App app : filtered) selected.remove(app.pkg());
            persist();
        });
        bulk.addView(selectAll, new LinearLayout.LayoutParams(0, -2, 1));
        var clearParams = new LinearLayout.LayoutParams(0, -2, 1);
        clearParams.setMarginStart(dp(6));
        bulk.addView(clear, clearParams);
        var bulkParams = new LinearLayout.LayoutParams(-1, -2);
        bulkParams.topMargin = dp(5);
        root.addView(bulk, bulkParams);

        empty = label("Загрузка…", 16);
        empty.setPadding(0, dp(16), 0, 0);
        root.addView(empty);
        list = new RecyclerView(this);
        adapter = new AppsAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        refreshCount();
        if (state == null) UiMotion.enterChildren(root);
        new Thread(this::loadApps, "clawd-app-picker").start();
    }

    @Override public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT < 34) UiMotion.pageTransition(this, false);
    }

    @Override protected void onResume() {
        super.onResume();
        grant.setVisibility(ForegroundApps.hasAccess(this) ? View.GONE : View.VISIBLE);
        if (PetService.current != null) PetService.current.refreshExcludedApps();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        var launchers = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        var byPackage = new LinkedHashMap<String, App>();
        for (ResolveInfo info : launchers) {
            String pkg = info.activityInfo.packageName;
            if (byPackage.containsKey(pkg)) continue;
            CharSequence label = info.activityInfo.applicationInfo.loadLabel(pm);
            byPackage.put(pkg, new App(label.length() == 0 ? pkg : label.toString(), pkg, info));
        }
        var result = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        result.sort((a, b) -> collator.compare(a.name(), b.name()));
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            apps.addAll(result);
            loaded = true;
            filter();
            UiMotion.listEntrance(list);
        });
    }

    private void filter() {
        if (!loaded) return;
        filtered.clear();
        for (App app : apps) {
            if (query.isEmpty() || app.name().toLowerCase(Locale.ROOT).contains(query)
                    || app.pkg().contains(query)) filtered.add(app);
        }
        empty.setText(query.isEmpty() ? "Список пуст" : "Ничего не найдено");
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        selectAll.setText(query.isEmpty() ? "Выбрать все" : "Выбрать найденные");
        adapter.notifyDataSetChanged();
        refreshCount();
    }

    private void persist() {
        prefs.edit().putStringSet(ForegroundApps.EXCLUDED, new HashSet<>(selected)).apply();
        adapter.notifyDataSetChanged();
        refreshCount();
    }

    private void refreshCount() {
        count.setText(selected.isEmpty() ? "Приложения не выбраны" : "Выбрано " + selected.size());
        selectAll.setEnabled(filtered.stream().anyMatch(app -> !selected.contains(app.pkg())));
        clear.setEnabled(query.isEmpty() ? !selected.isEmpty() : filtered.stream().anyMatch(app -> selected.contains(app.pkg())));
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        return view;
    }

    private MaterialButton button(String value) {
        MaterialButton b = (MaterialButton) getLayoutInflater().inflate(R.layout.clawd_action, null);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private int color(int attr) { return MaterialColors.getColor(this, attr, 0xff25201d); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class AppsAdapter extends RecyclerView.Adapter<AppRow> {
        @Override public AppRow onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(ExcludedAppsActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(58));
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            ImageView icon = new ImageView(ExcludedAppsActivity.this);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            MaterialCheckBox label = new MaterialCheckBox(ExcludedAppsActivity.this);
            label.setTextSize(16);
            label.setMinHeight(dp(52));
            var lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMarginStart(dp(10));
            row.addView(label, lp);
            return new AppRow(row, icon, label);
        }

        @Override public void onBindViewHolder(AppRow holder, int index) {
            App app = filtered.get(index);
            holder.icon.setImageDrawable(app.info().loadIcon(getPackageManager()));
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setText(app.name());
            holder.check.setChecked(selected.contains(app.pkg()));
            holder.check.setOnCheckedChangeListener((view, checked) -> {
                if (checked) selected.add(app.pkg());
                else selected.remove(app.pkg());
                prefs.edit().putStringSet(ForegroundApps.EXCLUDED, new HashSet<>(selected)).apply();
                refreshCount();
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private static final class AppRow extends RecyclerView.ViewHolder {
        final ImageView icon;
        final MaterialCheckBox check;

        AppRow(View item, ImageView icon, MaterialCheckBox check) {
            super(item);
            this.icon = icon;
            this.check = check;
        }
    }
}
