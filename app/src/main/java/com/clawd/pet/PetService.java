package com.clawd.pet;

import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import java.util.Collections;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.color.DynamicColors;

public final class PetService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String STOP = "com.clawd.pet.STOP";
    static final String CENTER = "com.clawd.pet.CENTER";
    static final String SNACK = "com.clawd.pet.SNACK";
    static final String PLATFORM = "com.clawd.pet.PLATFORM";
    static final String CHANGED = "com.clawd.pet.CHANGED";
    private static final String CHANNEL = "clawd_overlay";
    private static final int NOTIFICATION = 20;
    static PetService current;
    static String startError;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PetStage stage;
    private PyObject pet;
    private boolean observing;
    private boolean awake;
    private boolean excluded;
    private ForegroundApps foregroundApps;
    private String currentPackage;
    private boolean ownActivity;

    boolean hiddenForApp() { return excluded; }

    boolean usesPhonePlatforms() {
        return awake && !excluded && prefs != null && prefs.getBoolean("show", false)
                && prefs.getBoolean("phone_platforms", false);
    }
    void refreshExcludedApps() { watchForeground(); }
    boolean hasOwnPlatforms() { return ownActivity; }

    void ownPlatforms(android.view.View... views) {
        if (stage != null) stage.setOwnPlatforms(views);
        boolean active = views.length > 0;
        if (ownActivity != active) {
            ownActivity = active;
            InterfacePlatforms.requestRefresh();
        }
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateVisibility(); }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("clawd", Context.MODE_PRIVATE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (STOP.equals(action)) {
            prefs(this).edit().putBoolean("show", false).apply();
            if (pet == null) stopSelf();
            else applyVisibility();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this) || !prefs(this).getBoolean("show", false)) {
            if (pet == null) stopSelf();
            else applyVisibility();
            return START_NOT_STICKY;
        }
        if (pet == null) {
            try {
                startError = null;
                startNotification();
                prefs = prefs(this);
                var themed = new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_Clawd);
                Context context = DynamicColors.wrapContextIfAvailable(themed);
                stage = new PetStage(context, prefs, handler);
                stage.attach();
                if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
                pet = Python.getInstance().getModule("clawd").callAttr("create", stage);
                prefs.registerOnSharedPreferenceChangeListener(this);
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
                observing = true;
                foregroundApps = new ForegroundApps(this, handler, (packageName, hidden) -> {
                    // The shade may surface System UI while handling a notification action.
                    if (!"com.android.systemui".equals(packageName)) {
                        stage.setActivePackage(packageName);
                        if (!java.util.Objects.equals(currentPackage, packageName)) {
                            currentPackage = packageName;
                            invoke("platforms_changed");
                        }
                    }
                    if (excluded != hidden) {
                        excluded = hidden;
                        applyVisibility();
                        broadcastState();
                    }
                });
                current = this;
                InterfacePlatforms.requestRefresh();
                updateVisibility();
            } catch (RuntimeException e) {
                Log.e("Clawd", "Cannot start overlay", e);
                startError = "Не удалось показать Clawd";
                prefs(this).edit().putBoolean("show", false).apply();
                stopSelf();
                broadcastState();
                return START_NOT_STICKY;
            }
        }
        if (CENTER.equals(action)) handler.post(() -> invoke("_recenter"));
        if (SNACK.equals(action)) handler.post(() -> invoke("snack"));
        if (PLATFORM.equals(action) && stage != null && !excluded) {
            int result = stage.togglePlatformAtDrop();
            if (result > 0) invoke("land_on_manual_platform");
            String message = switch (result) {
                case 1 -> "Опора сохранена для этого приложения";
                case 0 -> "Опора убрана";
                default -> "Перетащи Clawd на нужную высоту, затем нажми «Опора»";
            };
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
        }
        return START_STICKY;
    }

    private void startNotification() {
        var manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Питомец на экране",
                NotificationManager.IMPORTANCE_LOW));
        int pending = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        var open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), pending);
        var stop = PendingIntent.getService(this, 2, new Intent(this, PetService.class).setAction(STOP), pending);
        var notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.clawd_status)
                .setContentTitle("Clawd на экране")
                .setContentIntent(open)
                .addAction(0, "Скрыть", stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (prefs(this).getBoolean("manual_platforms", false)) {
            PendingIntent ledge = PendingIntent.getService(this, 3,
                    new Intent(this, PetService.class).setAction(PLATFORM), pending);
            notification.addAction(0, "Опора", ledge);
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION, notification.build());
        }
    }

    private void updateVisibility() {
        awake = getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        watchForeground();
    }

    private void watchForeground() {
        if (foregroundApps == null) return;
        var packages = prefs.getStringSet(ForegroundApps.EXCLUDED, Collections.emptySet());
        if (awake && (!packages.isEmpty() || prefs.getBoolean("manual_platforms", false))
                && ForegroundApps.hasAccess(this)) {
            foregroundApps.start(packages);
        } else {
            foregroundApps.stop();
            if (stage != null) stage.setActivePackage(null);
            currentPackage = null;
            excluded = false;
        }
        applyVisibility();
        broadcastState();
    }

    private void applyVisibility() {
        boolean allowed = Settings.canDrawOverlays(this);
        boolean wanted = prefs.getBoolean("show", false) && allowed;
        boolean visible = wanted && awake && !excluded;
        InterfacePlatforms.requestRefresh();
        invoke("pause", !visible);
        if (stage != null) {
            stage.setVisible(visible, awake && allowed, wanted ? null : () -> {
                if (!prefs.getBoolean("show", false) || !Settings.canDrawOverlays(this)) stopSelf();
            });
        }
    }

    private void invoke(String method, Object... values) {
        if (pet == null) return;
        try { pet.callAttr(method, values); }
        catch (RuntimeException e) { Log.e("Clawd", method, e); }
    }

    void notificationArrived(String text) { handler.post(() -> invoke("notice", text)); }

    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
        if ("show".equals(key)) {
            InterfacePlatforms.requestRefresh();
            applyVisibility();
            return;
        }
        switch (key) {
            case ForegroundApps.EXCLUDED -> watchForeground();
            case "phone_platforms" -> {
                InterfacePlatforms.requestRefresh();
                invoke("config_changed", key);
                invoke("platforms_changed");
            }
            case "manual_platforms" -> {
                startNotification();
                if (stage != null && !shared.getBoolean("manual_platforms", false)) stage.setActivePackage(null);
                watchForeground();
            }
            case "fx_blur", "fx_impact", "fx_dust", "fx_trail", "fx_strength" -> {
                if (stage != null) stage.configureEffects();
            }
            case "gravity" -> {
                InterfacePlatforms.requestRefresh();
                invoke("config_changed", key);
            }
            case "size", "color", "shake", "wander", "phrases", "sleep", "haptics", "dance", "poop", "tg_events", "ui_platforms" ->
                    invoke("config_changed", key);
        }
    }

    @Override public void onDestroy() {
        if (foregroundApps != null) foregroundApps.stop();
        if (observing) unregisterReceiver(screen);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        invoke("close");
        pet = null;
        if (stage != null) stage.close();
        stage = null;
        if (current == this) current = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        broadcastState();
        super.onDestroy();
    }

    private void broadcastState() { sendBroadcast(new Intent(CHANGED).setPackage(getPackageName())); }
}
