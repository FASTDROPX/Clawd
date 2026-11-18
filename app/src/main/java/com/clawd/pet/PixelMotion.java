package com.clawd.pet;

final class PixelMotion {
    static final long COAT_DURATION_MS = 660;
    static final int COLUMNS = 18;
    static final int ROWS = 13;

    private PixelMotion() { }

    static float expoInOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t < .5f ? (float) (Math.pow(2, 20 * t - 10) / 2)
                : (float) ((2 - Math.pow(2, 10 - 20 * t)) / 2);
    }

    static int opacity(int x, int y, float progress) {
        if (progress <= 0f) return 0;
        if (progress >= 1f) return 255;
        int mirror = Math.min(x, COLUMNS - 1 - x);
        float distance = (COLUMNS / 2 - 1 - mirror) / (COLUMNS / 2f - 1);
        float fleck = ((mirror * 19 + y * 7) % 11) / 10f;
        float delay = .37f * distance + .16f * (ROWS - 1 - y) / (ROWS - 1) + .15f * fleck;
        float amount = Math.max(0f, Math.min(1f, (progress - delay) / .32f));
        return Math.round(255f * amount * amount * (3f - 2f * amount));
    }
}
