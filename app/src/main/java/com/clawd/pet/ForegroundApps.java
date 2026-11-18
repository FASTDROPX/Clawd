package com.clawd.pet;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Process;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.util.function.BiConsumer;

final class ForegroundApps {
    static final String EXCLUDED = "excluded_apps";
    private static final long POLL = 650;
    private static final long LOOKBACK = TimeUnit.HOURS.toMillis(12);
    private final Context context;
    private final Handler handler;
    private final BiConsumer<String, Boolean> listener;
    private ScheduledExecutorService worker;

    ForegroundApps(Context context, Handler handler, BiConsumer<String, Boolean> listener) {
        this.context = context;
        this.handler = handler;
        this.listener = listener;
    }

    @SuppressWarnings("deprecation")
    static boolean hasAccess(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    void start(Set<String> packages) {
        stop();
        var usage = context.getSystemService(UsageStatsManager.class);
        if (usage == null) return;
        var session = new Session(usage, new HashSet<>(packages));
        worker = Executors.newSingleThreadScheduledExecutor();
        session.owner = worker;
        worker.scheduleWithFixedDelay(session, 0, POLL, TimeUnit.MILLISECONDS);
    }

    void stop() {
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    private final class Session implements Runnable {
        private final UsageStatsManager usage;
        private final Set<String> excluded;
        private ScheduledExecutorService owner;
        private long through;
        private long latestTime;
        private String latest;
        private Boolean hidden;
        private String notifiedPackage;

        Session(UsageStatsManager usage, Set<String> excluded) {
            this.usage = usage;
            this.excluded = excluded;
        }

        @Override public void run() {
            if (!hasAccess(context)) {
                through = 0;
                latestTime = 0;
                latest = null;
                deliver(null, false);
                return;
            }
            try {
                long now = System.currentTimeMillis();
                if (now < through) {
                    through = 0;
                    latestTime = 0;
                    latest = null;
                }
                if (through == 0) {
                    scan(now - TimeUnit.MINUTES.toMillis(10), now);
                    if (latest == null) scan(now - LOOKBACK, now);
                } else {
                    // The overlap catches events committed by Android just after the last query.
                    scan(Math.max(0, through - 2500), now);
                }
                through = now;
                deliver(latest, latest != null && excluded.contains(latest));
            } catch (RuntimeException e) {
                deliver(null, false);
            }
        }

        @SuppressWarnings("deprecation")
        private void scan(long from, long to) {
            UsageEvents events = usage.queryEvents(from, to);
            if (events == null) return;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.getNextEvent(event)) {
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                        && event.getTimeStamp() >= latestTime && event.getPackageName() != null) {
                    latestTime = event.getTimeStamp();
                    latest = event.getPackageName();
                }
            }
        }

        private void deliver(String packageName, boolean value) {
            if (hidden != null && hidden == value && Objects.equals(notifiedPackage, packageName)) return;
            hidden = value;
            notifiedPackage = packageName;
            handler.post(() -> {
                if (worker == owner) listener.accept(packageName, value);
            });
        }
    }
}
