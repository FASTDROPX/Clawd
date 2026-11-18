package com.clawd.pet;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.PathInterpolator;
import android.view.animation.TranslateAnimation;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

final class UiMotion {
    static final TimeInterpolator ENTER = new PathInterpolator(.08f, .64f, .18f, 1f);
    static final TimeInterpolator TRAVEL = new PathInterpolator(.22f, 0f, .16f, 1f);

    private UiMotion() {}

    static int dp(View view, float dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    static void enterChildren(ViewGroup parent) { enterChildren(parent, null); }

    static void enterChildren(ViewGroup parent, View immediate) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        int order = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.animate().cancel();
            if (child == immediate) {
                child.setAlpha(1f);
                child.setScaleX(.94f);
                child.setScaleY(.94f);
                child.setTranslationY(dp(child, 24));
                child.animate().translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(490).setStartDelay(0).setInterpolator(TRAVEL).start();
                order++;
                continue;
            }
            child.setAlpha(0f);
            child.setTranslationX(-dp(child, 30));
            child.setTranslationY(dp(child, 22));
            child.animate().alpha(1f).translationX(0f).translationY(0f)
                    .setDuration(490).setStartDelay(Math.min(order++, 8) * 40L)
                    .setInterpolator(TRAVEL).start();
        }
    }

    static void listEntrance(ViewGroup list) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        var appearance = new AnimationSet(false);
        var slide = new TranslateAnimation(-dp(list, 18), 0f, -dp(list, 8), 0f);
        var fade = new AlphaAnimation(0f, 1f);
        for (Animation part : new Animation[]{slide, fade}) {
            part.setDuration(290);
            part.setInterpolator(ENTER::getInterpolation);
            appearance.addAnimation(part);
        }
        list.setLayoutAnimation(new LayoutAnimationController(appearance, .075f));
        list.scheduleLayoutAnimation();
    }

    static void dialog(MaterialAlertDialogBuilder builder) {
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(ValueAnimator.areAnimatorsEnabled() ? R.style.ClawdDialogMotion : 0);
        }
        dialog.show();
        if (dialog.getListView() != null) listEntrance(dialog.getListView());
    }

    static void notice(View anchor, String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
                .show();
    }

    @SuppressWarnings("deprecation")
    static void pageTransition(Activity activity, boolean opening) {
        boolean enabled = ValueAnimator.areAnimatorsEnabled();
        int enter = enabled ? (opening ? R.anim.clawd_page_enter : R.anim.clawd_page_return) : 0;
        int exit = enabled ? (opening ? R.anim.clawd_page_depart : R.anim.clawd_page_exit) : 0;
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(opening ? Activity.OVERRIDE_TRANSITION_OPEN
                    : Activity.OVERRIDE_TRANSITION_CLOSE, enter, exit);
        } else {
            activity.overridePendingTransition(enter, exit);
        }
    }
}
