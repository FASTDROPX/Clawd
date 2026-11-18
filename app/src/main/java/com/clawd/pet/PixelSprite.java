package com.clawd.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.ImageView;

public final class PixelSprite extends ImageView {
    private final Paint pixels = new Paint();
    private final Rect source = new Rect();
    private final RectF destination = new RectF();
    private Bitmap sprite;
    private float reveal;

    PixelSprite(Context context) { super(context); }

    @Override public void setImageBitmap(Bitmap bitmap) {
        sprite = bitmap;
        super.setImageBitmap(bitmap);
    }

    float reveal() { return reveal; }

    Bitmap spriteBitmap() { return sprite; }

    void setReveal(float progress) {
        if (reveal == progress) return;
        reveal = progress;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (reveal >= 1f) {
            super.onDraw(canvas);
            return;
        }
        Bitmap bitmap = sprite;
        if (reveal <= 0f || bitmap == null) return;
        int sw = bitmap.getWidth() / PixelMotion.COLUMNS;
        int sh = bitmap.getHeight() / PixelMotion.ROWS;
        if (sw == 0 || sh == 0) return;
        float dw = (float) getWidth() / PixelMotion.COLUMNS;
        float dh = (float) getHeight() / PixelMotion.ROWS;
        for (int y = 0; y < PixelMotion.ROWS; y++) {
            for (int x = 0; x < PixelMotion.COLUMNS; x++) {
                int alpha = PixelMotion.opacity(x, y, reveal);
                // Source pixels are drawn as solid pixel-art cells.
                if (alpha == 0 || bitmap.getPixel(x * sw, y * sh) == 0) continue;
                pixels.setAlpha(alpha);
                source.set(x * sw, y * sh, (x + 1) * sw, (y + 1) * sh);
                destination.set(x * dw, y * dh, (x + 1) * dw, (y + 1) * dh);
                canvas.drawBitmap(bitmap, source, destination, pixels);
            }
        }
    }
}
