package com.clawd.pet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

final class RevealLayout extends FrameLayout {
    private ValueAnimator animator;
    private float position;
    private boolean expanded;

    RevealLayout(Context context, ViewGroup content) {
        super(context);
        addView(content, new FrameLayout.LayoutParams(-1, -2));
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    boolean isExpanded() { return expanded; }

    void setExpanded(boolean show) {
        expanded = show;
        cancel();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        float target = show ? 1f : 0f;
        if (position == target || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow()) {
            finish(target);
            return;
        }
        if (show) setVisibility(VISIBLE);
        var motion = ValueAnimator.ofFloat(position, target);
        animator = motion;
        motion.setInterpolator(new LinearInterpolator());
        motion.setDuration(Math.max(85, Math.round((show ? 480 : 365) * Math.abs(target - position))));
        motion.addUpdateListener(value -> update((float) value.getAnimatedValue()));
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != motion) return;
                animator = null;
                finish(target);
            }
        });
        motion.start();
    }

    private void update(float value) {
        position = value;
        var content = (ViewGroup) getChildAt(0);
        int count = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i).getVisibility() != GONE) count++;
        }
        int order = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            View row = content.getChildAt(i);
            if (row.getVisibility() == GONE) {
                row.setAlpha(1f);
                row.setTranslationX(0f);
                row.setTranslationY(0f);
                continue;
            }
            float delay = .38f * order++ / Math.max(1, count - 1);
            float time = Math.max(0f, Math.min(1f, (value - delay) / .62f));
            float visible = UiMotion.ENTER.getInterpolation(time);
            row.setAlpha(visible);
            row.setTranslationX(-UiMotion.dp(row, 20) * (1f - visible));
            row.setTranslationY(-UiMotion.dp(row, 12) * (1f - visible));
        }
        requestLayout();
    }

    private void finish(float target) {
        update(target);
        setVisibility(target == 1f ? VISIBLE : GONE);
        setImportantForAccessibility(target == 1f ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void cancel() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) previous.cancel();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        float height = UiMotion.TRAVEL.getInterpolation(position);
        setMeasuredDimension(getMeasuredWidth(), resolveSize(Math.round(getMeasuredHeight() * height), heightSpec));
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return animator != null || super.dispatchTouchEvent(event);
    }

    @Override protected void onDetachedFromWindow() {
        cancel();
        finish(expanded ? 1f : 0f);
        super.onDetachedFromWindow();
    }
}
