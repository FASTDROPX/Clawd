package com.clawd.pet;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects visible control geometry only, in screen coordinates. */
public final class InterfacePlatforms extends AccessibilityService {
    static volatile boolean connected;
    static volatile int lastExternalCount;
    static volatile Rect[] ledges = new Rect[0];
    private static final Rect[] EMPTY = new Rect[0];
    private HandlerThread thread;
    private Handler worker;
    private volatile boolean running;
    private volatile boolean needsGeometry;
    private volatile boolean ownActivity;
    private volatile int generation;
    private volatile int retryBudget;
    private final java.util.concurrent.atomic.AtomicBoolean pending = new java.util.concurrent.atomic.AtomicBoolean();
    private final Runnable collect = () -> {
        pending.set(false);
        scan();
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        thread = new HandlerThread("clawd-platform-bounds");
        thread.start();
        worker = new Handler(thread.getLooper());
        running = true;
        connected = true;
        requestScan();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (getPackageName().contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) retryBudget = 2;
        requestScan();
    }

    static void requestRefresh() {
        var service = live;
        if (service != null) service.requestScan();
    }

    private static InterfacePlatforms live;

    private void requestScan() {
        if (worker == null) return;
        live = this;
        var overlay = PetService.current;
        boolean needed = overlay != null && overlay.usesPhonePlatforms();
        boolean own = overlay != null && overlay.hasOwnPlatforms();
        if (needed != needsGeometry || own != ownActivity) {
            generation++;
            retryBudget = 2;
        }
        needsGeometry = needed;
        ownActivity = own;
        if (needed) {
            if (pending.compareAndSet(false, true)) worker.postDelayed(collect, 90);
        } else {
            worker.removeCallbacks(collect);
            pending.set(false);
            ledges = EMPTY;
        }
    }

    @SuppressWarnings("deprecation")
    private void scan() {
        if (!running || !needsGeometry) return;
        int requestGeneration = generation;
        try {
            Rect extent = new Rect();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                root.getBoundsInScreen(extent);
                if (isOwnNode(root) && ownActivity) {
                    root.recycle();
                    publish(requestGeneration, EMPTY);
                    return;
                }
                if (extent.isEmpty() || isOwnNode(root)) {
                    root.recycle();
                    root = null;
                }
            }
            if (root == null) root = fallbackRoot(extent);
            if (root == null || extent.isEmpty()) {
                if (root != null) root.recycle();
                publish(requestGeneration, EMPTY);
                retry();
                return;
            }
            Rect[] candidates = collect(root, extent).toArray(new Rect[0]);
            if (publish(requestGeneration, candidates)) {
                lastExternalCount = candidates.length;
                if (candidates.length == 0) retry();
                else retryBudget = 2;
            }
        } catch (RuntimeException ignored) {
            publish(requestGeneration, EMPTY);
            retry();
        }
    }

    @SuppressWarnings("deprecation")
    private AccessibilityNodeInfo fallbackRoot(Rect extent) {
        List<AccessibilityWindowInfo> windows = getWindows();
        Rect bounds = new Rect();
        float screenHeight = getResources().getDisplayMetrics().heightPixels;
        ArrayList<AccessibilityWindowInfo> eligible = new ArrayList<>();
        for (AccessibilityWindowInfo candidate : windows) {
            if (Build.VERSION.SDK_INT >= 30 && candidate.getDisplayId() != android.view.Display.DEFAULT_DISPLAY)
                continue;
            int type = candidate.getType();
            boolean app = type == AccessibilityWindowInfo.TYPE_APPLICATION;
            if (!app && type != AccessibilityWindowInfo.TYPE_SYSTEM) continue;
            candidate.getBoundsInScreen(bounds);
            if (bounds.isEmpty() || (!app && (!candidate.isFocused() || bounds.height() < screenHeight * .4f))) continue;
            eligible.add(candidate);
        }
        eligible.sort(Comparator.comparingInt(this::score).reversed());
        AccessibilityNodeInfo root = null;
        try {
            for (AccessibilityWindowInfo candidate : eligible) {
                AccessibilityNodeInfo node = candidate.getRoot();
                if (node == null) continue;
                if (isOwnNode(node)) {
                    node.recycle();
                    if (ownActivity) break;
                    continue;
                }
                candidate.getBoundsInScreen(extent);
                root = node;
                break;
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }
        return root;
    }

    private int score(AccessibilityWindowInfo window) {
        return (window.isActive() ? 4 : 0) + (window.isFocused() ? 2 : 0)
                + (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION ? 1 : 0);
    }

    private boolean isOwnNode(AccessibilityNodeInfo root) {
        CharSequence owner = root.getPackageName();
        return owner != null && getPackageName().contentEquals(owner);
    }

    private boolean publish(int requestGeneration, Rect[] result) {
        if (!running || !needsGeometry || generation != requestGeneration) return false;
        ledges = result;
        return true;
    }

    private void retry() {
        if (retryBudget <= 0 || !running || !needsGeometry) return;
        if (pending.compareAndSet(false, true)) {
            retryBudget--;
            worker.postDelayed(collect, 210);
        }
    }

    @SuppressWarnings("deprecation")
    private ArrayList<Rect> collect(AccessibilityNodeInfo root, Rect extent) {
        ArrayList<Rect> result = new ArrayList<>();
        float density = getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        Rect bounds = new Rect();
        try {
            for (int visited = 0; visited < 280 && !queue.isEmpty(); visited++) {
                AccessibilityNodeInfo node = queue.removeFirst();
                try {
                    if (!node.isVisibleToUser()) continue;
                    int children = node.getChildCount();
                    if (visited > 0 && (!node.isScrollable() || node.isClickable())) {
                        node.getBoundsInScreen(bounds);
                        if (bounds.top >= extent.top + 3 * density
                                && bounds.height() >= 12 * density && bounds.height() <= 240 * density
                                && bounds.width() >= 32 * density && bounds.intersect(extent)) {
                            offer(result, bounds, density);
                        }
                    }
                    for (int i = 0; i < children && queue.size() < 105; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(child);
                    }
                } finally {
                    node.recycle();
                }
            }
        } finally {
            while (!queue.isEmpty()) queue.removeFirst().recycle();
        }
        result.sort(Comparator.comparingInt((Rect r) -> r.top).thenComparingInt(r -> r.left));
        return result;
    }

    private void offer(ArrayList<Rect> result, Rect candidate, float density) {
        for (Rect r : result) {
            if (Math.abs(r.top - candidate.top) <= 5 * density
                    && Math.min(r.right, candidate.right) >= Math.max(r.left, candidate.left)) {
                if (candidate.width() > r.width()) r.set(candidate);
                return;
            }
        }
        if (result.size() < 64) result.add(new Rect(candidate));
    }

    @Override public void onInterrupt() { ledges = EMPTY; }

    @Override public void onDestroy() {
        running = false;
        connected = false;
        lastExternalCount = 0;
        if (live == this) live = null;
        ledges = EMPTY;
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }
}
