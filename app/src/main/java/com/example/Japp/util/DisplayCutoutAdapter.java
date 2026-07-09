package com.example.Japp.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public final class DisplayCutoutAdapter {

    private DisplayCutoutAdapter() {
    }

    public static void apply(Activity activity) {
        if (activity == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() <= 0) {
            return;
        }

        View root = contentGroup.getChildAt(0);
        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            DisplayCutoutCompat displayCutout = insets.getDisplayCutout();

            int cutoutLeft = displayCutout != null ? displayCutout.getSafeInsetLeft() : 0;
            int cutoutTop = displayCutout != null ? displayCutout.getSafeInsetTop() : 0;
            int cutoutRight = displayCutout != null ? displayCutout.getSafeInsetRight() : 0;
            int cutoutBottom = displayCutout != null ? displayCutout.getSafeInsetBottom() : 0;

            int left = Math.max(systemBars.left, cutoutLeft);
            int top = Math.max(systemBars.top, cutoutTop);
            int right = Math.max(systemBars.right, cutoutRight);
            int bottom = Math.max(systemBars.bottom, cutoutBottom);

            v.setPadding(
                    initialLeft + left,
                    initialTop + top,
                    initialRight + right,
                    initialBottom + bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
