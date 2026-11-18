package com.clawd.pet;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;
import java.util.WeakHashMap;

final class PetEffects extends View {
    private static final int MAX_CHIPS = 104;
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();
    private final Random random = new Random();
    private final ArrayList<Chip> chips = new ArrayList<>();
    private final MotionBlurKernel kernel = new MotionBlurKernel();
    private final WeakHashMap<Bitmap, int[]> colors = new WeakHashMap<>();
    private Bitmap blurBitmap;
    private Bitmap previousSprite;
    private int previousDx, previousDy;
    private float blurX, blurY, blurW, blurH, blurTurn;
    private boolean blurVisible;
    private final float density;
    private boolean blur, impact, dust, trail;
    private float strength;
    private boolean active;
    private float lastX, lastY;
    private long lastSample, lastDraw, lastTrail;

    private static final class Chip {
        float x, y, vx, vy;
        final float size, floor, life;
        final int color;
        final boolean falls;
        final long born;
        Chip(float x, float y, float vx, float vy, float size, float floor, float life, int color, boolean falls, long born) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.floor = floor; this.life = life;
            this.color = color; this.falls = falls; this.born = born;
        }
    }


    PetEffects(Context context, SharedPreferences prefs) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        configure(prefs);
    }

    void configure(SharedPreferences prefs) {
        blur = prefs.getBoolean("fx_blur", false);
        impact = prefs.getBoolean("fx_impact", false);
        dust = prefs.getBoolean("fx_dust", false);
        trail = prefs.getBoolean("fx_trail", false);
        strength = switch (Math.max(0, Math.min(2, prefs.getInt("fx_strength", 1)))) {
            case 0 -> .55f;
            case 2 -> 1.45f;
            default -> 1f;
        };
        clear();
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (!active) clear();
    }

    private void clear() {
        chips.clear();
        blurVisible = false;
        previousSprite = null;
        lastSample = lastDraw = lastTrail = 0;
        invalidate();
    }

    boolean needsMotionSamples() { return active && (blur || trail); }

    boolean isBlurring() { return blurVisible; }

    boolean sample(Bitmap bitmap, float x, float y, float w, float h, float turn, int color) {
        if (!active || (!blur && !trail) || bitmap == null || !ValueAnimator.areAnimatorsEnabled()) {
            clearBlur();
            return false;
        }
        long now = SystemClock.uptimeMillis();
        long elapsed = now - lastSample;
        float dx = x - lastX, dy = y - lastY;
        float distance = (float) Math.hypot(dx, dy);
        boolean visible = false;
        if (lastSample != 0 && elapsed > 0 && elapsed < 100 && distance < dp(175)) {
            float speed = distance * 1000f / (elapsed * density);
            if (blur && speed > 250) {
                double turnRad = Math.toRadians(turn);
                float cos = (float) Math.cos(turnRad), sin = (float) Math.sin(turnRad);
                float sampleWidth = w / (PixelMotion.COLUMNS * MotionBlurKernel.SCALE);
                float sampleHeight = h / (PixelMotion.ROWS * MotionBlurKernel.SCALE);
                float shutter = -30f * strength / elapsed;
                int sx = cap(Math.round((dx * cos + dy * sin) * shutter / sampleWidth));
                int sy = cap(Math.round((-dx * sin + dy * cos) * shutter / sampleHeight));
                if (sx != 0 || sy != 0) {
                    updateBlur(bitmap, sx, sy);
                    blurX = x; blurY = y; blurW = w; blurH = h; blurTurn = turn;
                    visible = true;
                    invalidate();
                }
            }
            if (trail && speed > 410 && now - lastTrail >= 28) {
                lastTrail = now;
                float drift = 68f / elapsed;
                add(new Chip(x + w * .5f - dx * drift / 5, y + h * .55f - dy * drift / 5,
                        -dx * drift, -dy * drift, dp(between(2, 5)) * strength, getHeight(),
                        between(.19f, .30f), color, false, now));
                invalidate();
            }
        }
        if (!visible) clearBlur();
        else blurVisible = true;
        lastSample = now;
        lastX = x;
        lastY = y;
        return visible;
    }

    void clearBlur() {
        if (blurVisible) {
            blurVisible = false;
            invalidate();
        }
    }

    private int cap(int value) {
        return Math.max(-MotionBlurKernel.PAD, Math.min(MotionBlurKernel.PAD, value));
    }

    private void updateBlur(Bitmap bitmap, int dx, int dy) {
        if (previousSprite == bitmap && previousDx == dx && previousDy == dy) return;
        int[] source = colors.get(bitmap);
        if (source == null) {
            int w = bitmap.getWidth() / PixelMotion.COLUMNS;
            int h = bitmap.getHeight() / PixelMotion.ROWS;
            source = new int[PixelMotion.COLUMNS * PixelMotion.ROWS];
            for (int y = 0; y < PixelMotion.ROWS; y++)
                for (int x = 0; x < PixelMotion.COLUMNS; x++)
                    source[y * PixelMotion.COLUMNS + x] = bitmap.getPixel(x * w + w / 2, y * h + h / 2);
            colors.put(bitmap, source);
        }
        if (blurBitmap == null) blurBitmap = Bitmap.createBitmap(MotionBlurKernel.WIDTH,
                MotionBlurKernel.HEIGHT, Bitmap.Config.ARGB_8888);
        blurBitmap.setPixels(kernel.render(source, dx, dy), 0, MotionBlurKernel.WIDTH, 0, 0,
                MotionBlurKernel.WIDTH, MotionBlurKernel.HEIGHT);
        previousSprite = bitmap;
        previousDx = dx;
        previousDy = dy;
    }

    void burst(float x, float y, float nx, float ny, float speedPx, float floor, int color, boolean landing) {
        if (!active || !ValueAnimator.areAnimatorsEnabled()) return;
        float speed = speedPx / density;
        long now = SystemClock.uptimeMillis();
        if ((landing ? dust : impact) && speed >= (landing ? 175 : 245)) {
            int count = Math.max(3, Math.min(23, Math.round((4 + Math.min(speed, 2700) / 135) * strength)));
            for (int i = 0; i < count; i++) {
                float speedOut = dp(between(.18f, .52f) * Math.min(2100, speed) + 35);
                float tangent = speedOut * between(-.83f, .83f);
                float size = dp(between(1.7f, 5.7f) * strength + Math.min(2.5f, speed / 800));
                float along = dp(between(-10f, 10f));
                add(new Chip(x - ny * along, y + nx * along,
                        nx * speedOut - ny * tangent, ny * speedOut + nx * tangent,
                        size, floor, between(.27f, .59f), color, true, now));
            }
        }
        if (!chips.isEmpty()) invalidate();
    }

    private void add(Chip chip) {
        if (chips.size() >= MAX_CHIPS) chips.remove(0);
        chips.add(chip);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastDraw == 0 ? 0 : Math.min(.042f, (now - lastDraw) / 1000f);
        lastDraw = now;

        paint.setStyle(Paint.Style.FILL);
        if (blurVisible && blurBitmap != null) {
            float padX = MotionBlurKernel.PAD * blurW / (PixelMotion.COLUMNS * MotionBlurKernel.SCALE);
            float padY = MotionBlurKernel.PAD * blurH / (PixelMotion.ROWS * MotionBlurKernel.SCALE);
            paint.setColor(0xffffffff);
            paint.setFilterBitmap(true);
            canvas.save();
            canvas.rotate(blurTurn, blurX + blurW / 2, blurY + blurH / 2);
            destination.set(blurX - padX, blurY - padY, blurX + blurW + padX, blurY + blurH + padY);
            canvas.drawBitmap(blurBitmap, null, destination, paint);
            canvas.restore();
        }
        for (int i = 0; i < chips.size();) {
            Chip chip = chips.get(i);
            float progress = (now - chip.born) / (1000f * chip.life);
            if (progress >= 1f) { chips.remove(i); continue; }
            chip.x += chip.vx * dt;
            chip.y += chip.vy * dt;
            if (chip.falls) {
                chip.vy += dp(980) * dt;
                float floor = Math.min(chip.floor, getHeight()) - chip.size / 2;
                if (chip.y >= floor && chip.vy > 0) {
                    chip.y = floor;
                    chip.vy *= -.24f;
                    chip.vx *= .72f;
                }
            }
            paint.setColor(chip.color);
            paint.setAlpha(Math.round(224 * (1f - progress) * (1f - progress)));
            float half = chip.size * (.5f - .17f * progress);
            canvas.drawRect(chip.x - half, chip.y - half, chip.x + half, chip.y + half, paint);
            i++;
        }
        if (!chips.isEmpty()) postInvalidateOnAnimation();
        else lastDraw = 0;
    }

    private float dp(float size) { return density * size; }
    private float between(float from, float to) { return from + random.nextFloat() * (to - from); }
}
