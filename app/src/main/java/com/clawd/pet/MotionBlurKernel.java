package com.clawd.pet;

import java.util.Arrays;

/** Integrates premultiplied sprite samples along a backward shutter vector. */
final class MotionBlurKernel {
    static final int SCALE = 2;
    static final int PAD = 24;
    static final int WIDTH = PixelMotion.COLUMNS * SCALE + 2 * PAD;
    static final int HEIGHT = PixelMotion.ROWS * SCALE + 2 * PAD;
    private final int[] alpha = new int[WIDTH * HEIGHT];
    private final int[] red = new int[alpha.length];
    private final int[] green = new int[alpha.length];
    private final int[] blue = new int[alpha.length];
    private final int[] result = new int[alpha.length];

    int[] render(int[] source, int dx, int dy) {
        if (dx < -PAD || dx > PAD || dy < -PAD || dy > PAD) {
            throw new IllegalArgumentException("Shutter vector outside the blur buffer");
        }
        Arrays.fill(alpha, 0);
        Arrays.fill(red, 0);
        Arrays.fill(green, 0);
        Arrays.fill(blue, 0);
        int samples = Math.min(13, Math.max(3, 1 + Math.max(Math.abs(dx), Math.abs(dy))));
        int weightSum = 0;
        for (int i = 0; i < samples; i++) {
            float t = (float) i / (samples - 1);
            int offsetX = PAD + Math.round(dx * t), offsetY = PAD + Math.round(dy * t);
            int weight = Math.round(18 - 11 * t);
            weightSum += weight;
            for (int y = 0; y < PixelMotion.ROWS; y++) {
                for (int x = 0; x < PixelMotion.COLUMNS; x++) {
                    int c = source[y * PixelMotion.COLUMNS + x];
                    int a = (c >>> 24) * weight;
                    if (a == 0) continue;
                    int ar = a * (c >> 16 & 255), ag = a * (c >> 8 & 255), ab = a * (c & 255);
                    int base = (offsetY + y * SCALE) * WIDTH + offsetX + x * SCALE;
                    for (int row = 0; row < SCALE; row++) {
                        for (int column = 0; column < SCALE; column++) {
                            int index = base + row * WIDTH + column;
                            alpha[index] += a;
                            red[index] += ar;
                            green[index] += ag;
                            blue[index] += ab;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < result.length; i++) {
            int a = alpha[i], opacity = (a + weightSum / 2) / weightSum;
            result[i] = opacity == 0 ? 0 : (opacity << 24) | (red[i] / a << 16)
                    | (green[i] / a << 8) | blue[i] / a;
        }
        return result;
    }
}
