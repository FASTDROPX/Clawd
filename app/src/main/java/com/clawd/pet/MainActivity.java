package com.clawd.pet;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.widget.ImageView;
import androidx.core.graphics.ColorUtils;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String[] SLEEP = {"Через 1 минуту", "Через 3 минуты", "Через 10 минут", "Никогда"};
    private static final String[] SIZES = {"Маленький", "Средний", "Большой"};
    private static final int NOTICE_PERMISSION = 10;
    private SharedPreferences prefs;
    private LinearLayout page;
    private ClawdPreview preview;
    private TextView effectStrength;
    private boolean inForeground;
    private View heroEntrance;
    private TextView phonePlatformState;
    private TextView sleepValue;
    private TextView accessHint;
    private MaterialButton show;
    private MaterialButton center;
    private MaterialButton snack;
    private MaterialButton battery;
    private MaterialButton noticePermission;
    private MaterialSwitch telegram;
    private MaterialButton[] swatches;
    private boolean awaitingOverlay;
    private boolean awaitingTelegram;
    private boolean refreshing;
    private boolean dark;
    private Typeface heading;
    private int surface, onSurface, muted, primary, container, soft;
    private TextView exclusions;
    private GradientDrawable heroBackground;
    private GradientDrawable paletteBackground;
    private int currentHeroColor;
    private int selectedCoat = -1;
    private ValueAnimator heroTint;
    private String shownStartError;
    private final BroadcastReceiver stateObserver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refresh();
            if (PetService.startError != null && !PetService.startError.equals(shownStartError) && page.isShown()) {
                shownStartError = PetService.startError;
                UiMotion.notice(page, shownStartError);
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        prefs = PetService.prefs(this);
        if (state != null) {
            awaitingOverlay = state.getBoolean("overlay");
            awaitingTelegram = state.getBoolean("telegram");
        }
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Typeface font = getResources().getFont(R.font.clawd_display);
        heading = Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 650, false) : Typeface.create(font, Typeface.BOLD);
        surface = tone(com.google.android.material.R.attr.colorSurface);
        onSurface = tone(com.google.android.material.R.attr.colorOnSurface);
        muted = tone(com.google.android.material.R.attr.colorOnSurfaceVariant);
        primary = tone(androidx.appcompat.R.attr.colorPrimary);
        container = tone(com.google.android.material.R.attr.colorSurfaceContainerLow);
        soft = tone(com.google.android.material.R.attr.colorSecondaryContainer);
        buildPage();
        prefs.registerOnSharedPreferenceChangeListener(this);
        ContextCompat.registerReceiver(this, stateObserver, new IntentFilter(PetService.CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        refresh();
        UiMotion.enterChildren(page, heroEntrance);
    }

    @Override protected void onResume() {
        super.onResume();
        inForeground = true;
        InterfacePlatforms.requestRefresh();
        if (PetService.current != null && prefs.getBoolean("manual_platforms", false))
            PetService.current.refreshExcludedApps();
        if (awaitingOverlay) {
            awaitingOverlay = false;
            if (Settings.canDrawOverlays(this)) setShowing(true);
        } else if (prefs.getBoolean("show", false) && Settings.canDrawOverlays(this)
                && PetService.current == null) {
            setShowing(true);
        }
        if (awaitingTelegram) {
            awaitingTelegram = false;
            prefs.edit().putBoolean("tg_events", TelegramNotices.allowed(this)).apply();
        }
        if (!Settings.canDrawOverlays(this) && prefs.getBoolean("show", false)) setShowing(false);
        refresh();
    }

    @Override protected void onPause() {
        inForeground = false;
        if (PetService.current != null) PetService.current.ownPlatforms();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("overlay", awaitingOverlay);
        state.putBoolean("telegram", awaitingTelegram);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (heroTint != null) heroTint.cancel();
        unregisterReceiver(stateObserver);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    private void buildPage() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        var bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!dark);
        bars.setAppearanceLightNavigationBars(!dark);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(surface);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout column = new FrameLayout(this);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        page = new LinearLayout(this) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(574));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        page.setOrientation(LinearLayout.VERTICAL);
        column.addView(page, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        page.setPadding(dp(20), dp(9), dp(20), dp(28));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            var safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            scroll.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);

        TextView title = text("Clawd", 45, onSurface, true);
        ViewCompat.setAccessibilityHeading(title, true);
        add(page, title, 0, 11);
        buildHero();

        show = button("Показать поверх приложений", true);
        show.setIconResource(R.drawable.ic_eye);
        show.setOnClickListener(view -> toggleOverlay());
        add(page, show, 12, 0);

        LinearLayout actions = (LinearLayout) getLayoutInflater().inflate(R.layout.clawd_actions, page, false);
        center = button("В центр", false);
        center.setIconResource(R.drawable.ic_center);
        snack = button("Угостить", false);
        snack.setIconResource(R.drawable.ic_treat);
        center.setOnClickListener(view -> sendAction(PetService.CENTER));
        snack.setOnClickListener(view -> sendAction(PetService.SNACK));
        fill(actions, center, 0);
        fill(actions, snack, 0);
        add(page, actions, 4, 0);
        buildExclusions();

        section("Размер Clawd");
        LinearLayout looks = group();
        MaterialButtonToggleGroup sizes = (MaterialButtonToggleGroup) getLayoutInflater().inflate(R.layout.clawd_sizes, looks, false);
        sizes.setSingleSelection(true);
        sizes.setSelectionRequired(true);
        int[] ids = new int[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            MaterialButton control = button(SIZES[i], false);
            ids[i] = View.generateViewId();
            control.setId(ids[i]);
            control.setText(SIZES[i]);
            control.setAllCaps(false);
            control.setMinWidth(0);
            control.setMinimumWidth(0);
            control.setMinHeight(dp(48));
            control.setPadding(dp(5), 0, dp(5), 0);
            sizes.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
        }
        sizes.check(ids[Math.max(0, Math.min(2, prefs.getInt("size", 1)))]);
        sizes.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) prefs.edit().putInt("size", i).apply();
            preview.hop();
        });
        add(looks, sizes, 0, 0);

        LinearLayout behavior = disclosure("Поведение");
        toggle(behavior, "Гравитация", "gravity", false);
        toggle(behavior, "Тряска", "shake", true);
        toggle(behavior, "Сам ходит", "wander", true);
        toggle(behavior, "Реплики", "phrases", true);
        toggle(behavior, "Танцует под музыку", "dance", true);
        toggle(behavior, "Вибрация", "haptics", true);
        toggle(behavior, "Кучки", "poop", true);

        LinearLayout sleep = horizontal();
        sleep.setGravity(Gravity.CENTER_VERTICAL);
        sleep.setMinimumHeight(dp(56));
        sleep.addView(text("Сон", 16, onSurface, false), new LinearLayout.LayoutParams(0, -2, 1));
        sleepValue = text("", 14, primary, false);
        sleep.addView(sleepValue);
        sleep.setOnClickListener(view -> UiMotion.dialog(new MaterialAlertDialogBuilder(this).setTitle("Засыпает")
                .setSingleChoiceItems(SLEEP, sleepIndex(), (dialog, index) -> {
                    prefs.edit().putInt("sleep", index).apply();
                    dialog.dismiss();
                }).setNegativeButton("Закрыть", null)));
        add(behavior, sleep, 0, 0);
        buildEffects();
        buildPlatforms();

        LinearLayout access = disclosure("Разрешения");
        accessHint = text("", 14, muted, false);
        add(access, accessHint, 0, 9);
        battery = button("Настроить батарею", false);
        battery.setOnClickListener(view -> batterySettings());
        add(access, battery, 0, 3);
        noticePermission = button("Разрешить уведомление Clawd", false);
        noticePermission.setOnClickListener(view -> allowOwnNotifications());
        add(access, noticePermission, 0, 5);
        telegram = toggle(access, "Реакции на Telegram", "tg_events", false);
        telegram.setOnCheckedChangeListener((view, checked) -> {
            if (refreshing) return;
            if (!checked || TelegramNotices.allowed(this)) {
                prefs.edit().putBoolean("tg_events", checked).apply();
                return;
            }
            refresh();
            UiMotion.dialog(new MaterialAlertDialogBuilder(this)
                    .setTitle("Доступ к уведомлениям")
                    .setMessage("Android открывает доступ ко всем уведомлениям. Clawd использует только входящие уведомления Telegram, чтобы замечать приветствия, смех и сердечки.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть настройки", (dialog, button) -> {
                        awaitingTelegram = open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }));
        });

        MaterialButton help = button("Жесты и статистика", false);
        help.setOnClickListener(view -> UiMotion.dialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Как играть")
                .setMessage("Два касания — прыжок\nУдержание и движение — погладить\nРезкое перетаскивание — бросить\n\nПогладили " + prefs.getInt("pets_count", 0)
                        + "\nУгостили " + prefs.getInt("snacks", 0)
                        + "\nУбрано кучек " + prefs.getInt("poops_cleaned", 0))
                .setPositiveButton("Понятно", null)));
        add(page, help, 6, 0);
        add(page, text("Clawd 0.5.2 beta\nПитомец из плагина @itskotovski", 12, muted, false), 18, 0);
    }

    private void buildExclusions() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(74));
        row.setPadding(dp(18), dp(9), dp(15), dp(9));
        row.setBackground(shape(soft, 13, 34, 28, 34));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_apps);
        int color = tone(com.google.android.material.R.attr.colorOnSecondaryContainer);
        icon.setColorFilter(color);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(shape(ColorUtils.blendARGB(soft, color, .13f), 19, 19, 19, 8));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout caption = vertical();
        caption.setPaddingRelative(dp(13), 0, dp(6), 0);
        caption.addView(text("Скрывать в приложениях", 17, color, true));
        exclusions = text("", 13, color, false);
        caption.addView(exclusions);
        row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_expand);
        arrow.setRotation(-90f);
        arrow.setColorFilter(color);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.setOnClickListener(view -> {
            startActivity(new Intent(this, ExcludedAppsActivity.class));
            if (Build.VERSION.SDK_INT < 34) UiMotion.pageTransition(this, true);
        });
        pressMotion(row);
        row.setFocusable(true);
        add(page, row, 13, 0);
    }

    private int effectIntensity() {
        return Math.max(0, Math.min(2, prefs.getInt("fx_strength", 1)));
    }

    private static final String[] EFFECT_INTENSITIES = {"Мягкая", "Средняя", "Яркая"};

    private void buildEffects() {
        LinearLayout effects = disclosure("Эффекты");
        toggle(effects, "Размытие в движении", "fx_blur", false);
        toggle(effects, "Осколки от удара", "fx_impact", false);
        toggle(effects, "Пыль при посадке", "fx_dust", false);
        toggle(effects, "Пиксельный след", "fx_trail", false);

        LinearLayout intensity = horizontal();
        intensity.setMinimumHeight(dp(52));
        intensity.setGravity(Gravity.CENTER_VERTICAL);
        intensity.addView(text("Интенсивность", 16, onSurface, false), new LinearLayout.LayoutParams(0, -2, 1));
        effectStrength = text(EFFECT_INTENSITIES[effectIntensity()], 14, primary, false);
        intensity.addView(effectStrength);
        intensity.setOnClickListener(view -> UiMotion.dialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Интенсивность")
                .setSingleChoiceItems(EFFECT_INTENSITIES, effectIntensity(), (dialog, index) -> {
                    prefs.edit().putInt("fx_strength", index).apply();
                    dialog.dismiss();
                }).setNegativeButton("Закрыть", null)));
        pressMotion(intensity);
        add(effects, intensity, 0, 0);
    }

    private void buildPlatforms() {
        LinearLayout platforms = disclosure("Опоры");
        toggle(platforms, "Клавиатура и элементы Clawd", "ui_platforms", true);
        MaterialSwitch phone = toggle(platforms, "Опоры по всему телефону", "phone_platforms", false);
        phone.setOnCheckedChangeListener((view, enabled) -> {
            prefs.edit().putBoolean("phone_platforms", enabled).apply();
            InterfacePlatforms.requestRefresh();
            if (enabled && !InterfacePlatforms.connected) showPhonePlatformAccess();
        });
        phonePlatformState = text("", 13, muted, false);
        add(platforms, phonePlatformState, 0, 6);
        MaterialButton access = button("Доступ к опорам", false);
        access.setOnClickListener(view -> showPhonePlatformAccess());
        add(platforms, access, 2, 0);
        toggle(platforms, "Свои опоры в приложениях", "manual_platforms", false);
        MaterialButton help = button("Как поставить опору", false);
        help.setOnClickListener(view -> UiMotion.dialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Опоры")
                .setMessage("Включи гравитацию, свои опоры и уведомление Clawd. Дай доступ к данным об использовании, чтобы запоминать опору отдельно для каждого приложения. Перетащи Clawd на нужную высоту, отпусти и нажми «Опора» в уведомлении. Повторное нажатие на той же высоте уберёт опору.")
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Доступ к использованию", (dialog, button) ->
                        open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))));
        add(platforms, help, 2, 0);
    }

    private void showPhonePlatformAccess() {
        UiMotion.dialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Опоры по всему телефону")
                .setMessage("Включи службу «Опоры Clawd» в специальных возможностях Android. На доступный уступ Clawd может запрыгнуть сам. Чтобы выбрать место, перетащи его чуть выше края элемента и отпусти. Для опор используются координаты видимых элементов.")
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Открыть настройки", (dialog, button) ->
                        open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
    }

    private void buildHero() {
        LinearLayout habitat = vertical();
        heroEntrance = habitat;
        FrameLayout scene = new FrameLayout(this);
        currentHeroColor = surface;
        heroBackground = shape(surface, 48, 28, 12, 12);
        scene.setBackground(heroBackground);
        scene.setClipToOutline(true);
        preview = new ClawdPreview(this, dark);
        preview.setContentDescription("Поиграть с Clawd");
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        preview.setOnClickListener(view -> {
            preview.hop();
            if (prefs.getBoolean("haptics", true)) preview.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        });
        scene.addView(preview, new FrameLayout.LayoutParams(-1, dp(236)));
        add(habitat, scene, 0, 0);
        add(habitat, buildPalette(), 3, 0);
        add(page, habitat, 0, 0);
    }

    private View buildPalette() {
        LinearLayout colors = horizontal();
        colors.setPadding(dp(8), dp(1), dp(8), dp(1));
        paletteBackground = shape(container, 12, 12, 30, 30);
        colors.setBackground(paletteBackground);
        swatches = new MaterialButton[ClawdPreview.COATS.length];
        for (int i = 0; i < swatches.length; i++) {
            int choice = i;
            MaterialButton swatch = new MaterialButton(this);
            swatch.setMinWidth(0);
            swatch.setMinimumWidth(0);
            swatch.setMinHeight(dp(48));
            swatch.setInsetTop(dp(5));
            swatch.setInsetBottom(dp(5));
            swatch.setPadding(0, 0, 0, 0);
            swatch.setCornerRadius(dp(12));
            swatch.setTextSize(17);
            swatch.setBackgroundTintList(ColorStateList.valueOf(ClawdPreview.COATS[i]));
            swatch.setTextColor(ClawdPreview.EYES[i]);
            swatch.setContentDescription(ClawdPreview.NAMES[i]);
            swatch.setCheckable(true);
            swatch.setToggleCheckedStateOnClick(false);
            swatch.setOnClickListener(view -> {
                if (choice != selectedCoat) prefs.edit().putInt("color", choice).apply();
            });
            swatches[i] = swatch;
            fill(colors, swatch, i == 0 ? 0 : 5);
        }
        return colors;
    }

    private LinearLayout disclosure(String title) {
        LinearLayout card = vertical();
        LinearLayout header = horizontal();
        header.setMinimumHeight(dp(58));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(17), dp(4), dp(15), dp(4));
        boolean permissions = "Разрешения".equals(title);
        header.setBackground(shape(container, permissions ? 13 : 30, permissions ? 30 : 13, 10, 10));
        header.setFocusable(true);
        pressMotion(header);
        header.setContentDescription(title);
        TextView heading = text(title, 21, onSurface, true);
        heading.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_expand);
        arrow.setColorFilter(onSurface);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
        ViewCompat.setAccessibilityHeading(header, true);
        card.addView(header);
        LinearLayout content = vertical();
        content.setPadding(dp(15), 0, dp(15), dp(8));
        content.setBackground(shape(container, 12, 12, 26, 26));
        RevealLayout body = new RevealLayout(this, content);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        header.setOnClickListener(view -> {
            boolean expand = !body.isExpanded();
            body.setExpanded(expand);
            float turn = expand ? 180f : 0f;
            if (ValueAnimator.areAnimatorsEnabled()) {
                arrow.animate().rotation(turn).setDuration(300)
                        .setInterpolator(UiMotion.ENTER).start();
            } else {
                arrow.setRotation(turn);
            }
            ViewCompat.setStateDescription(header, expand ? "Развёрнуто" : "Свёрнуто");
        });
        ViewCompat.setStateDescription(header, "Свёрнуто");
        add(page, card, 10, 0);
        return content;
    }

    private MaterialSwitch toggle(LinearLayout group, String name, String key, boolean fallback) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        LinearLayout labels = vertical();
        add(labels, text(name, 16, onSurface, false), 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch control = new MaterialSwitch(this);
        control.setContentDescription(name);
        control.setMinimumWidth(dp(52));
        control.setMinimumHeight(dp(48));
        control.setChecked(prefs.getBoolean(key, fallback));
        control.setOnCheckedChangeListener((view, checked) -> prefs.edit().putBoolean(key, checked).apply());
        row.addView(control, new LinearLayout.LayoutParams(-2, -2));
        labels.setOnClickListener(view -> control.toggle());
        labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        add(group, row, 0, 0);
        return control;
    }

    private void section(String title) {
        TextView label = text(title, 23, onSurface, true);
        ViewCompat.setAccessibilityHeading(label, true);
        add(page, label, 25, 9);
    }

    private LinearLayout group() {
        LinearLayout group = vertical();
        group.setPadding(dp(15), dp(12), dp(15), dp(12));
        group.setBackground(shape(container, 16, 32, 32, 24));
        add(page, group, 0, 0);
        return group;
    }

    private GradientDrawable shape(int color, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{dp(topLeft), dp(topLeft), dp(topRight), dp(topRight),
                dp(bottomRight), dp(bottomRight), dp(bottomLeft), dp(bottomLeft)});
        return drawable;
    }

    private TextView text(String content, int size, int color, boolean display) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        if (display) view.setTypeface(heading);
        return view;
    }

    private MaterialButton button(String text, boolean filled) {
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(filled ? R.layout.clawd_primary : R.layout.clawd_action, page, false);
        button.setText(text);
        button.setAllCaps(false);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        pressMotion(button);
        return button;
    }

    private void pressMotion(View view) {
        view.setOnTouchListener((target, event) -> {
            if (!ValueAnimator.areAnimatorsEnabled()) return false;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN -> target.animate().scaleX(.973f).scaleY(.95f)
                        .setStartDelay(0).setDuration(95).setInterpolator(new PathInterpolator(.2f, 0, 0, 1)).start();
                case android.view.MotionEvent.ACTION_CANCEL, android.view.MotionEvent.ACTION_UP ->
                        target.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            }
            return false;
        });
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() { return new LinearLayout(this); }

    private void add(LinearLayout parent, View child, int top, int bottom) {
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        parent.addView(child, params);
    }

    private void fill(LinearLayout parent, View child, int before) {
        var params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(dp(before));
        parent.addView(child, params);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int tone(int attr) { return MaterialColors.getColor(this, attr, 0xff25201d); }
    private int sleepIndex() { return Math.max(0, Math.min(SLEEP.length - 1, prefs.getInt("sleep", 1))); }

    private void refresh() {
        if (show == null) return;
        boolean phonePlatforms = prefs.getBoolean("phone_platforms", false);
        phonePlatformState.setVisibility(phonePlatforms ? View.VISIBLE : View.GONE);
        if (phonePlatforms) {
            int count = InterfacePlatforms.lastExternalCount;
            phonePlatformState.setText(!InterfacePlatforms.connected ? "Служба «Опоры Clawd» выключена"
                    : count > 0 ? "Опор в последнем приложении: " + count
                    : "Служба подключена. Открой другое приложение для поиска опор.");
        }
        if (inForeground && PetService.current != null) PetService.current.ownPlatforms(preview, show, center, snack);
        effectStrength.setText(EFFECT_INTENSITIES[effectIntensity()]);
        boolean allowed = Settings.canDrawOverlays(this);
        boolean active = PetService.current != null && prefs.getBoolean("show", false);
        boolean starting = !active && prefs.getBoolean("show", false) && allowed;
        boolean hiddenHere = active && PetService.current.hiddenForApp();
        show.setText(active ? "Скрыть питомца" : starting ? "Запуск…" : "Показать питомца");
        show.setIconResource(active ? R.drawable.ic_hide : R.drawable.ic_eye);
        show.setEnabled(!starting);
        center.setEnabled(active && !hiddenHere);
        snack.setEnabled(active && !hiddenHere);
        accessHint.setText(PetService.startError != null ? PetService.startError
                : allowed ? "" : "Нужно разрешить показ поверх приложений");
        accessHint.setVisibility(accessHint.length() == 0 ? View.GONE : View.VISIBLE);
        int selected = prefs.getStringSet(ForegroundApps.EXCLUDED, java.util.Collections.emptySet()).size();
        exclusions.setText(selected == 0 ? "Нет исключений" : "Выбрано " + selected);
        if (selected > 0 && !ForegroundApps.hasAccess(this)) exclusions.setText("Нужен доступ к активности");
        boolean ignored = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        battery.setText(ignored ? "Батарея: без ограничений" : "Снять ограничение батареи");
        noticePermission.setVisibility(!getSystemService(NotificationManager.class).areNotificationsEnabled()
                ? View.VISIBLE : View.GONE);
        refreshing = true;
        telegram.setChecked(prefs.getBoolean("tg_events", false) && TelegramNotices.allowed(this));
        refreshing = false;
        sleepValue.setText(SLEEP[sleepIndex()]);
        int coat = Math.max(0, Math.min(swatches.length - 1, prefs.getInt("color", 0)));
        if (coat != selectedCoat) updateSwatches(coat);
        boolean changed = preview.configure(coat, prefs.getInt("size", 1));
        int target = ColorUtils.blendARGB(surface, ClawdPreview.COATS[coat], dark ? .27f : .24f);
        if (changed && ValueAnimator.areAnimatorsEnabled()) {
            if (heroTint != null) heroTint.cancel();
            int startingTint = currentHeroColor;
            heroTint = ValueAnimator.ofFloat(0f, 1f);
            heroTint.setDuration(PixelMotion.COAT_DURATION_MS);
            heroTint.setInterpolator(PixelMotion::expoInOut);
            heroTint.addUpdateListener(animation -> applyHeroColor(ColorUtils.blendARGB(
                    startingTint, target, (float) animation.getAnimatedValue())));
            heroTint.start();
        } else if (heroTint == null || !heroTint.isRunning()) {
            applyHeroColor(target);
        }
    }

    private void updateSwatches(int coat) {
        int previous = selectedCoat;
        selectedCoat = coat;
        for (int i = 0; i < swatches.length; i++) {
            MaterialButton swatch = swatches[i];
            boolean selected = i == coat;
            swatch.setText(selected ? "✓" : "");
            swatch.setChecked(selected);
            swatch.setCornerRadius(dp(selected ? 23 : 12));
            if (previous != -1 && ValueAnimator.areAnimatorsEnabled() && (selected || i == previous)) {
                swatch.animate().cancel();
                swatch.setScaleX(selected ? .91f : 1.035f);
                swatch.setScaleY(selected ? .84f : 1.06f);
                swatch.animate().scaleX(1f).scaleY(1f).setDuration(400)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.9f)).start();
            }
        }
    }

    private void applyHeroColor(int color) {
        currentHeroColor = color;
        heroBackground.setColor(color);
        paletteBackground.setColor(ColorUtils.blendARGB(container, color, .65f));
    }

    private void toggleOverlay() {
        if (prefs.getBoolean("show", false)) {
            setShowing(false);
        } else if (Settings.canDrawOverlays(this)) {
            setShowing(true);
            preview.hop();
        } else {
            UiMotion.dialog(new MaterialAlertDialogBuilder(this).setTitle("Показ поверх приложений")
                    .setMessage("Найди Clawd в списке приложений и разреши показ поверх других окон.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть настройки", (dialog, button) ->
                            awaitingOverlay = open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri()))));
        }
    }

    private void setShowing(boolean enabled) {
        PetService.startError = null;
        shownStartError = null;
        prefs.edit().putBoolean("show", enabled).apply();
        if (enabled) {
            try { ContextCompat.startForegroundService(this, new Intent(this, PetService.class)); }
            catch (RuntimeException e) {
                PetService.startError = "Не удалось показать Clawd";
                shownStartError = PetService.startError;
                UiMotion.notice(page, shownStartError);
                prefs.edit().putBoolean("show", false).apply();
            }
        }
        refresh();
    }

    private void sendAction(String action) {
        if (PetService.current != null) {
            startService(new Intent(this, PetService.class).setAction(action));
            preview.hop();
        }
    }

    private void batterySettings() {
        boolean ignored = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        if (!ignored && open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri()))) return;
        open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    private void allowOwnNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTICE_PERMISSION);
        } else {
            open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private Uri appUri() { return Uri.fromParts("package", getPackageName(), null); }

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            UiMotion.notice(page, "Этот раздел настроек недоступен");
            return false;
        }
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
        if (!"pos_x".equals(key) && !"pos_y".equals(key) && !"poop_meter".equals(key)) refresh();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }
}
