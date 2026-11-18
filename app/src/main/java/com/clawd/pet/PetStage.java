package com.clawd.pet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.IdentityHashMap;

public final class PetStage {
    private final Context context;
    private final Context windowContext;
    private final WindowManager manager;
    private final AudioManager audio;
    private final Handler handler;
    private final SharedPreferences prefs;
    private final FrameLayout effects;
    private final PetEffects visualEffects;
    private Runnable nextFrame;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback frameCallback = time -> {
        Runnable next = nextFrame;
        nextFrame = null;
        if (next != null) next.run();
    };
    private final IdentityHashMap<View, Sprite> sprites = new IdentityHashMap<>();
    private final int padding;
    private final int color;
    private final int stroke;
    private final int text;
    private final int[] location = new int[2];
    private final Rect safe = new Rect();
    private int rootX;
    private int rootY;
    private int rotation;
    private int geometryVersion;
    private int keyboardTop = -1;
    private final Rect platformVisibility = new Rect();
    private View[] ownPlatforms = new View[0];
    private String activePackage;
    private String dropPackage;
    private long dropTime;
    private float dropX, dropY;
    private float manualX = -1f, manualY = -1f;
    private boolean effectsAttached;
    private boolean visible = true;
    private PixelSprite pet;
    private boolean prefersFastFrames;
    private static final int PHONE_PLATFORM_BASE = 6;
    private ValueAnimator visibilityMotion;
    private boolean motionTarget;
    private Runnable hiddenAction;

    private static final class Sprite {
        final FrameLayout frame;
        final WindowManager.LayoutParams window;
        float x;
        float y;
        boolean positioned;

        Sprite(FrameLayout frame, WindowManager.LayoutParams window) {
            this.frame = frame;
            this.window = window;
        }
    }

    PetStage(Context context, SharedPreferences prefs, Handler handler) {
        this.context = context;
        this.prefs = prefs;
        this.handler = handler;
        if (Build.VERSION.SDK_INT >= 30) {
            Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            windowContext = context.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        } else {
            windowContext = context;
        }
        manager = windowContext.getSystemService(WindowManager.class);
        audio = context.getSystemService(AudioManager.class);
        padding = dp(5);
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xfff7eee7);
        stroke = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0xffded2c9);
        text = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xff25201d);
        effects = new FrameLayout(windowContext);
        visualEffects = new PetEffects(windowContext, prefs);
        effects.addView(visualEffects, new FrameLayout.LayoutParams(-1, -1));
        visualEffects.setActive(true);
        effects.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        effects.setOnApplyWindowInsetsListener((view, insets) -> {
            updateGeometry(insets);
            return insets;
        });
        effects.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            updateGeometry(effects.getRootWindowInsets());
            for (Sprite sprite : sprites.values()) {
                if (sprite.positioned) position(sprite);
            }
        });
    }

    void attach() {
        WindowManager.LayoutParams window = layout(-1, -1, false);
        window.setTitle("Clawd effects");
        window.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        window.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        if (Build.VERSION.SDK_INT >= 31) {
            InputManager input = context.getSystemService(InputManager.class);
            window.alpha = Math.min(0.8f, input.getMaximumObscuringOpacityForTouch());
        }
        manager.addView(effects, window);
        effectsAttached = true;
    }

    private WindowManager.LayoutParams layout(int width, int height, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        flags |= touchable ? WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams window = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        window.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 30) {
            window.setFitInsetsTypes(0);
            window.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return window;
    }

    @SuppressWarnings("deprecation")
    private void updateGeometry(WindowInsets insets) {
        geometryVersion++;
        effects.getLocationOnScreen(location);
        rootX = location[0];
        rootY = location[1];
        rotation = manager.getDefaultDisplay().getRotation();
        if (Build.VERSION.SDK_INT >= 30) {
            var metrics = manager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            safe.set(
                    Math.max(0, bounds.left + bars.left - rootX),
                    Math.max(0, bounds.top + bars.top - rootY),
                    Math.max(0, rootX + effects.getWidth() - bounds.right + bars.right),
                    Math.max(0, rootY + effects.getHeight() - bounds.bottom + bars.bottom));
            int keyboard = insets != null && insets.isVisible(WindowInsets.Type.ime())
                    ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
            keyboardTop = keyboard > safe.bottom ? effects.getHeight() - keyboard : -1;
        } else if (insets != null) {
            int bottom = insets.getStableInsetBottom();
            safe.set(insets.getStableInsetLeft(), insets.getStableInsetTop(),
                    insets.getStableInsetRight(), bottom);
            int keyboard = insets.getSystemWindowInsetBottom();
            keyboardTop = keyboard > bottom ? effects.getHeight() - keyboard : -1;
        }
    }

    public PixelSprite createPetSprite() { return new PixelSprite(windowContext); }

    public void addSprite(View view, int width, int height) {
        FrameLayout frame = new FrameLayout(windowContext);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        FrameLayout.LayoutParams child = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
        frame.addView(view, child);
        WindowManager.LayoutParams window = layout(width + 2 * padding, height + 2 * padding, true);
        window.setTitle("Clawd pet");
        frame.setVisibility(View.INVISIBLE);
        manager.addView(frame, window);
        sprites.put(view, new Sprite(frame, window));
        if (view instanceof PixelSprite pixel) pet = pixel;
    }

    public void resizeSprite(View view, int width, int height) {
        Sprite sprite = sprites.get(view);
        if (sprite == null) return;
        var child = view.getLayoutParams();
        child.width = width;
        child.height = height;
        view.setLayoutParams(child);
        sprite.window.width = width + 2 * padding;
        sprite.window.height = height + 2 * padding;
        manager.updateViewLayout(sprite.frame, sprite.window);
    }

    public void placeSprite(View view, float x, float y, float rotation) {
        Sprite sprite = sprites.get(view);
        if (sprite == null) return;
        view.setRotation(rotation);
        sprite.x = x;
        sprite.y = y;
        boolean firstPlacement = !sprite.positioned;
        sprite.positioned = true;
        position(sprite);
        if (visible && firstPlacement) {
            if (view == pet) revealPet(true, true);
            else sprite.frame.setVisibility(View.VISIBLE);
        }
    }

    private void position(Sprite sprite) {
        int x = Math.round(sprite.x) + rootX - padding;
        int y = Math.round(sprite.y) + rootY - padding;
        if (sprite.window.x == x && sprite.window.y == y) return;
        sprite.window.x = x;
        sprite.window.y = y;
        manager.updateViewLayout(sprite.frame, sprite.window);
    }

    public boolean removeSprite(View view) {
        Sprite sprite = sprites.remove(view);
        if (sprite == null) return false;
        manager.removeViewImmediate(sprite.frame);
        return true;
    }

    void setVisible(boolean visible, boolean animate, Runnable afterHidden) {
        this.visible = visible;
        visualEffects.setActive(visible);
        if (!visible && pet != null && pet.getAlpha() != 1f) pet.setAlpha(1f);
        hiddenAction = visible ? null : afterHidden;
        effects.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        for (var entry : sprites.entrySet()) {
            Sprite sprite = entry.getValue();
            if (entry.getKey() != pet) {
                sprite.frame.setVisibility(visible && sprite.positioned ? View.VISIBLE : View.INVISIBLE);
            }
        }
        revealPet(visible, animate);
    }

    private void revealPet(boolean show, boolean animate) {
        Sprite sprite = sprites.get(pet);
        if (sprite == null || !sprite.positioned) {
            if (!show) fireHidden();
            return;
        }
        float target = show ? 1f : 0f;
        boolean motionAllowed = animate && animationsEnabled();
        if (visibilityMotion != null && motionTarget == show && motionAllowed) return;
        cancelVisibilityMotion();
        if (show) sprite.frame.setVisibility(View.VISIBLE);

        float start = pet.reveal();
        if (!motionAllowed || start == target) {
            pet.setReveal(target);
            if (!show) {
                sprite.frame.setVisibility(View.INVISIBLE);
                fireHidden();
            }
            return;
        }

        PixelSprite moving = pet;
        ValueAnimator motion = ValueAnimator.ofFloat(start, target);
        motionTarget = show;
        visibilityMotion = motion;
        motion.setInterpolator(new LinearInterpolator());
        motion.setDuration(Math.max(90L, Math.round(370 * Math.abs(target - start))));
        motion.addUpdateListener(animation -> moving.setReveal((float) animation.getAnimatedValue()));
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (visibilityMotion != motion) return;
                visibilityMotion = null;
                if (!show) {
                    sprite.frame.setVisibility(View.INVISIBLE);
                    fireHidden();
                }
            }
        });
        motion.start();
    }

    void setOwnPlatforms(View... views) { ownPlatforms = views; }

    void setActivePackage(String name) {
        activePackage = name;
        manualX = manualY = -1f;
        if (name == null || !prefs.getBoolean("manual_platforms", false)) return;
        String stored = prefs.getString("platform:" + name, "");
        int separator = stored.indexOf(',');
        if (separator < 0) return;
        try {
            float x = Float.parseFloat(stored.substring(0, separator));
            float y = Float.parseFloat(stored.substring(separator + 1));
            if (x >= 0f && x <= 1f && y >= 0f && y <= 1f) {
                manualX = x;
                manualY = y;
            }
        } catch (NumberFormatException ignored) {
            // A malformed or older entry does not create a platform.
        }
    }

    public void rememberDrop(float x, float y) {
        if (!prefs.getBoolean("manual_platforms", false) || activePackage == null) return;
        dropPackage = null;
        int width = effects.getWidth(), height = effects.getHeight();
        if (width <= 0 || height <= 0 || y <= safe.top || y >= height - safe.bottom - dp(8)) return;
        dropPackage = activePackage;
        dropTime = SystemClock.uptimeMillis();
        dropX = Math.max(0, Math.min(1, x / width));
        dropY = y / height;
    }

    int togglePlatformAtDrop() {
        if (!prefs.getBoolean("manual_platforms", false) || activePackage == null
                || !activePackage.equals(dropPackage) || SystemClock.uptimeMillis() - dropTime > 60000) {
            return -1;
        }
        String key = "platform:" + activePackage;
        if (manualY >= 0 && Math.abs((manualY - dropY) * effects.getHeight()) < dp(13)) {
            prefs.edit().remove(key).apply();
            setActivePackage(activePackage);
            return 0;
        }
        prefs.edit().putString(key, dropX + "," + dropY).apply();
        setActivePackage(activePackage);
        return 1;
    }

    public boolean hasPhonePlatforms() {
        return prefs.getBoolean("phone_platforms", false) && InterfacePlatforms.connected && InterfacePlatforms.ledges.length > 0;
    }

    public int getPlatformCount() {
        return PHONE_PLATFORM_BASE + (prefs.getBoolean("phone_platforms", false) ? InterfacePlatforms.ledges.length : 0);
    }

    public float[] getPlatform(int index) {
        if (index == 0) {
            return keyboardTop > 0 ? new float[] {safe.left, effects.getWidth() - safe.right, keyboardTop} : null;
        }
        if (index == 1) {
            if (manualY < 0) return null;
            float width = effects.getWidth();
            float center = manualX * width;
            float half = width * .23f;
            return new float[] {Math.max(safe.left, center - half), Math.min(width - safe.right, center + half),
                    manualY * effects.getHeight()};
        }
        if (index >= PHONE_PLATFORM_BASE) {
            Rect[] bounds = InterfacePlatforms.ledges;
            int item = index - PHONE_PLATFORM_BASE;
            if (!prefs.getBoolean("phone_platforms", false) || item >= bounds.length) return null;
            Rect box = bounds[item];
            float left = Math.max(safe.left, box.left - rootX);
            float right = Math.min(effects.getWidth() - safe.right, box.right - rootX);
            return right > left ? new float[] {left, right, box.top - rootY} : null;
        }
        if (index < 2 || index - 2 >= ownPlatforms.length) return null;
        View view = ownPlatforms[index - 2];
        if (!view.isShown() || !view.getLocalVisibleRect(platformVisibility) || platformVisibility.top > dp(2))
            return null;
        view.getLocationOnScreen(location);
        float top = location[1] - rootY;
        float x = location[0] - rootX;
        return new float[] {x + platformVisibility.left, x + platformVisibility.right, top};
    }

    public void scheduleFrame(Runnable action) {
        preferFastFrames(true);
        if (nextFrame == null) choreographer.postFrameCallback(frameCallback);
        nextFrame = action;
    }

    public void cancelFrame() {
        if (nextFrame != null) choreographer.removeFrameCallback(frameCallback);
        nextFrame = null;
    }

    public void idleFrames() { preferFastFrames(false); }

    @SuppressWarnings("deprecation")
    private void preferFastFrames(boolean fast) {
        if (prefersFastFrames == fast) return;
        prefersFastFrames = fast;
        Sprite sprite = sprites.get(pet);
        if (sprite == null) return;
        float preferred = 0f;
        if (fast) {
            Display display = manager.getDefaultDisplay();
            var current = display.getMode();
            for (var mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() <= 121f) {
                    preferred = Math.max(preferred, mode.getRefreshRate());
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            pet.setRequestedFrameRate(preferred);
            visualEffects.setRequestedFrameRate(preferred);
        } else if (sprite.window.preferredRefreshRate != preferred) {
            sprite.window.preferredRefreshRate = preferred;
            manager.updateViewLayout(sprite.frame, sprite.window);
        }
    }

    void configureEffects() {
        visualEffects.configure(prefs);
        if (pet != null) pet.setAlpha(1f);
    }

    public boolean isBlurActive() { return visualEffects.isBlurring(); }

    public void sampleEffects(float x, float y, float w, float h, float rotation, int color) {
        if (pet == null || !visualEffects.needsMotionSamples()) return;
        boolean blur = pet.reveal() >= 1f && visualEffects.sample(pet.spriteBitmap(), x, y, w, h, rotation, color);
        float alpha = blur ? .12f : 1f;
        if (pet.getAlpha() != alpha) pet.setAlpha(alpha);
    }

    public void emitImpact(float x, float y, float nx, float ny, float speed, float floor, int color, boolean landing) {
        visualEffects.burst(x, y, nx, ny, speed, floor, color, landing);
    }

    private void fireHidden() {
        Runnable callback = hiddenAction;
        hiddenAction = null;
        if (callback != null) callback.run();
    }

    private void cancelVisibilityMotion() {
        ValueAnimator previous = visibilityMotion;
        visibilityMotion = null;
        if (previous != null) previous.cancel();
    }

    void close() {
        cancelFrame();
        hiddenAction = null;
        cancelVisibilityMotion();
        for (View view : new ArrayList<>(sprites.keySet())) removeSprite(view);
        if (effectsAttached) {
            manager.removeViewImmediate(effects);
            effectsAttached = false;
        }
    }

    public boolean animationsEnabled() { return android.animation.ValueAnimator.areAnimatorsEnabled(); }

    public int dp(double dp) { return (int) Math.ceil(dp * context.getResources().getDisplayMetrics().density); }
    public Context getUiContext() { return windowContext; }
    public SharedPreferences getPrefs() { return prefs; }
    public Handler getHandler() { return handler; }
    public FrameLayout getEffects() { return effects; }
    public int getInsetLeft() { return safe.left; }
    public int getInsetRight() { return safe.right; }
    public int getInsetTop() { return safe.top; }
    public int getInsetBottom() { return safe.bottom; }
    public int getRotation() { return rotation; }
    public int getGeometryVersion() { return geometryVersion; }
    public int getKeyboardTop() { return keyboardTop; }
    public int getBubbleColor() { return color; }
    public int getBubbleStroke() { return stroke; }
    public int getBubbleText() { return text; }
    public boolean isMusicActive() { return audio != null && audio.isMusicActive(); }
}
