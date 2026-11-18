package com.clawd.pet;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.PathInterpolator;
import androidx.core.graphics.ColorUtils;
import java.util.Random;

final class ClawdPreview extends View {
    static final int[] COATS = {0xffd97757, 0xff4fbf98, 0xff9b8aee, 0xff4fa8e8, 0xffee85ae, 0xff5e6670};
    static final int[] EYES = {0xff1a1a1a, 0xff12241d, 0xff1b1631, 0xff0e1c29, 0xff2a1220, 0xfff2f2f2};
    static final String[] NAMES = {"Терракотовый", "Мятный", "Лавандовый", "Небесный", "Розовый", "Графитовый"};
    private static final int COLUMNS = PixelMotion.COLUMNS;
    private static final int[] FEET = {4, 6, 11, 13};
    private static final int[] EYE_X = {6, 11};
    private static final PathInterpolator ENTER = new PathInterpolator(.2f, 0f, .15f, 1f);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path contour = new Path();
    private final Path moonCutout = new Path();
    private final Random random = new Random();
    private final float[] sparks = new float[9];
    private final int[] body = new int[COLUMNS];
    private final int[] eyes = new int[COLUMNS];
    private final boolean dark;
    private final Runnable idle = this::idle;
    private ValueAnimator tint;
    private ValueAnimator movement;
    private ValueAnimator entrance;
    private float reveal = 1f;
    private int coat = -1;
    private int sceneCoat = COATS[0];
    private int size = 1;
    private int gesture = -1;
    private int lastIdle = -1;
    private float phase = 1f;
    private float wash = 1f;
    private boolean backward;

    ClawdPreview(Context context, boolean dark) {
        super(context);
        this.dark = dark;
    }

    boolean configure(int color, int size) {
        int target = Math.max(0, Math.min(COATS.length - 1, color));
        this.size = Math.max(0, Math.min(2, size));
        if (coat == target) {
            invalidate();
            return false;
        }
        boolean changed = coat != -1;
        coat = target;
        if (tint != null) tint.cancel();
        if (changed && ValueAnimator.areAnimatorsEnabled()) {
            int[] previousBody = body.clone();
            int[] previousEyes = eyes.clone();
            int previousScene = sceneCoat;
            backward = !backward;
            for (int i = 0; i < sparks.length; i++) sparks[i] = random.nextFloat();
            tint = ValueAnimator.ofFloat(0f, 1f);
            tint.setDuration(PixelMotion.COAT_DURATION_MS);
            tint.addUpdateListener(animation -> {
                wash = (float) animation.getAnimatedValue();
                sceneCoat = ColorUtils.blendARGB(previousScene, COATS[target], smooth(wash));
                for (int x = 0; x < COLUMNS; x++) {
                    int edge = Math.min(x, COLUMNS - 1 - x);
                    int column = backward ? edge : (COLUMNS / 2 - 1 - edge);
                    float amount = smooth((wash - column * .58f / (COLUMNS / 2 - 1)) / .42f);
                    body[x] = ColorUtils.blendARGB(previousBody[x], COATS[target], amount);
                    eyes[x] = ColorUtils.blendARGB(previousEyes[x], EYES[target], amount);
                }
                invalidate();
            });
            tint.setInterpolator(PixelMotion::expoInOut);
            tint.start();
        } else {
            java.util.Arrays.fill(body, COATS[target]);
            java.util.Arrays.fill(eyes, EYES[target]);
            sceneCoat = COATS[target];
            wash = 1f;
        }
        invalidate();
        return changed;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    void hop() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        int next = gesture < 2 ? 2 + random.nextInt(3) : 2 + (gesture - 1 + random.nextInt(2)) % 3;
        play(next, next == 4 ? 760 : 620);
    }

    private void idle() {
        if (!isShown() || !ValueAnimator.areAnimatorsEnabled()) return;
        lastIdle = lastIdle == -1 ? random.nextInt(3) : (lastIdle + 1 + random.nextInt(2)) % 3;
        play(lastIdle, lastIdle == 0 ? 150 : 710);
    }

    private void play(int next, long duration) {
        removeCallbacks(idle);
        if (movement != null) movement.cancel();
        gesture = next;
        movement = ValueAnimator.ofFloat(0f, 1f);
        movement.setDuration(duration);
        if (next == 3) movement.setInterpolator(ENTER);
        movement.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        movement.start();
        scheduleIdle(duration);
    }

    private void scheduleIdle(long after) {
        removeCallbacks(idle);
        if (isShown() && ValueAnimator.areAnimatorsEnabled()) {
            postDelayed(idle, after + 1900L + random.nextInt(2800));
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ValueAnimator.areAnimatorsEnabled()) {
            reveal = .47f;
            entrance = ValueAnimator.ofFloat(reveal, 1f);
            entrance.setDuration(265);
            entrance.setInterpolator(new PathInterpolator(.23f, .62f, .27f, 1f));
            entrance.addUpdateListener(animation -> {
                reveal = (float) animation.getAnimatedValue();
                invalidate();
            });
            entrance.start();
        }
        scheduleIdle(0);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (idle != null) {
            if (visibility == VISIBLE) scheduleIdle(0);
            else removeCallbacks(idle);
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(idle);
        if (entrance != null) {
            entrance.cancel();
            entrance = null;
        }
        reveal = 1f;
        if (movement != null) movement.cancel();
        if (tint != null) {
            tint.end();
            tint = null;
        }
        phase = 1f;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int shade = ColorUtils.blendARGB(sceneCoat, dark ? 0xff111c20 : 0xffede3d7, .72f);
        int medium = ColorUtils.blendARGB(sceneCoat, dark ? 0xff17292e : 0xfff1e8d9, .47f);
        int highlight = ColorUtils.blendARGB(sceneCoat, dark ? 0xffe1fff2 : 0xfffff5d6, .66f);
        int deep = ColorUtils.blendARGB(shade, dark ? 0xff080d13 : 0xff5e5a69, dark ? .53f : .34f);
        drawLandscape(canvas, w, h, shade, medium, highlight, deep);
        drawPet(canvas, w, h);
        if (wash < 1f) drawWash(canvas, w, h, highlight);
    }

    private void drawLandscape(Canvas canvas, float w, float h, int shade, int medium, int highlight, int deep) {
        float cx = w * .51f, cy = h * .44f;
        float pulse = (float) Math.sin(Math.PI * wash);
        paint.setColor(shade);
        contour.rewind();
        contour.moveTo(0, h * .38f);
        contour.cubicTo(w * .10f, h * .27f, w * .13f, h * .56f, w * .29f, h * .48f);
        contour.cubicTo(w * .46f, h * .38f, w * .63f, h * .06f, w * .82f, h * .17f);
        contour.cubicTo(w * .91f, h * .23f, w * .96f, h * .13f, w, h * .14f);
        contour.lineTo(w, h);
        contour.lineTo(0, h);
        contour.close();
        canvas.drawPath(contour, paint);

        paint.setColor(highlight);
        paint.setAlpha(dark ? 48 : 72);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w / 330f));
        for (int i = 0; i < 3; i++) {
            float shift = h * i * .054f;
            canvas.drawOval(-w * .16f, h * .12f + shift, w * .62f, h * .76f + shift, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        paint.setColor(medium);
        contour.rewind();
        float radius = Math.min(w * .29f, h * .41f);
        for (int point = 0; point <= 108; point++) {
            double a = point * Math.PI / 54;
            double r = radius * (1 + (.09 + .045 * pulse) * Math.cos(6 * a + pulse) + .055 * Math.sin(3 * a));
            float x = cx + (float) (Math.cos(a) * r) * 1.22f;
            float y = cy + (float) (Math.sin(a) * r);
            if (point == 0) contour.moveTo(x, y);
            else contour.lineTo(x, y);
        }
        contour.close();
        canvas.drawPath(contour, paint);

        paint.setColor(highlight);
        contour.rewind();
        contour.addCircle(w * .82f, h * .24f, h * .105f, Path.Direction.CW);
        moonCutout.rewind();
        moonCutout.addCircle(w * .85f, h * .21f, h * .092f, Path.Direction.CW);
        contour.op(moonCutout, Path.Op.DIFFERENCE);
        canvas.drawPath(contour, paint);
        float grid = Math.max(2, Math.round(w / 89f));
        paint.setColor(deep);
        for (int i = 0; i < 5; i++) {
            float x = w * .085f + i * grid * 2;
            canvas.drawRect(x, h * .22f + (i % 2) * grid, x + grid, h * .22f + (i % 2 + 1) * grid, paint);
        }
        paint.setColor(highlight);
        star(canvas, w * .24f, h * .27f, grid);
        star(canvas, w * .90f, h * .48f, grid * .7f);
        star(canvas, w * .11f, h * .44f, grid * .5f);

        float center = w * .5f;
        float deckHalf = w * .235f;
        float baseHalf = w * .285f;
        paint.setColor(deep);
        contour.rewind();
        contour.moveTo(0, h * .79f);
        contour.lineTo(center - baseHalf, h * .79f);
        contour.lineTo(center - deckHalf, h * .72f);
        contour.lineTo(center + deckHalf, h * .72f);
        contour.lineTo(center + baseHalf, h * .79f);
        contour.lineTo(w, h * .79f);
        contour.lineTo(w, h);
        contour.lineTo(0, h);
        contour.close();
        canvas.drawPath(contour, paint);
        paint.setColor(highlight);
        paint.setAlpha(dark ? 122 : 158);
        canvas.drawRect(center - deckHalf + grid, h * .72f, center + deckHalf - grid, h * .72f + grid, paint);
        for (int i = 0; i < 4; i++) {
            float x = w * .70f + grid * i * 3;
            float y = h * .85f + grid * (i % 2);
            canvas.drawRect(x, y, x + grid * 1.5f, y + grid * .55f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawPet(Canvas canvas, float w, float h) {
        float cx = w * .5f;
        float ground = h * .715f;
        float unit = Math.max(2, Math.round(Math.min(w / 32f, h / 19f) * (.88f + size * .12f)));
        float envelope = (float) Math.sin(Math.PI * phase);
        float dx = 0, up = 0, turn = 0, stretch = 0;
        boolean waving = gesture == 1 && phase < 1f;
        boolean blink = gesture == 0 && phase < 1f;
        switch (gesture) {
            case 2 -> {
                up = envelope * h * .15f;
                stretch = .11f * (float) Math.sin(phase * Math.PI * 2);
            }
            case 3 -> {
                dx = envelope * w * .065f;
                turn = envelope * (float) Math.sin(phase * 4 * Math.PI) * 10f;
                up = envelope * h * .045f;
            }
            case 4 -> {
                up = envelope * (float) Math.abs(Math.sin(phase * 2 * Math.PI)) * h * .19f;
                turn = envelope * 7f * (float) Math.sin(phase * Math.PI * 2);
            }
            case 1 -> turn = -envelope * 4f;
            default -> { }
        }
        if (wash < 1f) stretch += .085f * (float) Math.sin(Math.PI * wash);
        canvas.save();
        canvas.translate(cx + dx, Math.round(ground - up));
        canvas.rotate(turn);
        canvas.scale(unit * (1f + stretch), unit * (1f - stretch));
        canvas.translate(-9, -12);
        paint.setAntiAlias(false);
        pixels(canvas, 3, 3, 15, 10, body);
        pixels(canvas, 1, waving ? 3 : 6, 3, waving ? 7 : 8, body);
        pixels(canvas, 15, 6, 17, 8, body);
        for (int x : FEET) pixels(canvas, x, 10, x + 1, 12, body);
        for (int x : EYE_X) {
            int eye = x + (gesture == 3 && phase < .65f ? 1 : 0);
            pixels(canvas, eye, blink ? 6 : 5, eye + 1, 7, eyes);
        }
        paint.setAntiAlias(true);
        canvas.restore();
    }

    private void pixels(Canvas canvas, int x0, int y0, int x1, int y1, int[] colors) {
        for (int x = x0; x < x1; x++) {
            paint.setColor(colors[x]);
            if (reveal >= 1f) {
                canvas.drawRect(x, y0, x + 1, y1, paint);
            } else {
                for (int y = y0; y < y1; y++) {
                    paint.setAlpha(PixelMotion.opacity(x, y, reveal));
                    canvas.drawRect(x, y, x + 1, y + 1, paint);
                }
            }
        }
    }

    private void drawWash(Canvas canvas, float w, float h, int accent) {
        float power = (float) Math.sin(wash * Math.PI);
        if (power <= 0) return;
        float drift = backward ? 1 - wash : wash;
        float tile = Math.max(2, w / 92f);
        paint.setColor(accent);
        paint.setAlpha(Math.round(power * 235));
        for (int i = 0; i < sparks.length; i++) {
            float x = w * (.20f + .63f * drift + (sparks[i] - .5f) * .16f);
            float y = h * (.21f + .41f * i / (sparks.length - 1f) - wash * sparks[i] * .11f);
            if (i % 3 == 0) star(canvas, x, y, tile * .7f);
            else canvas.drawRect(x, y, x + tile, y + tile, paint);
        }
        paint.setAlpha(255);
    }

    private void star(Canvas canvas, float x, float y, float size) {
        canvas.drawRect(x - 2 * size, y - size * .5f, x + 2 * size, y + size * .5f, paint);
        canvas.drawRect(x - size * .5f, y - 2 * size, x + size * .5f, y + 2 * size, paint);
    }
}
