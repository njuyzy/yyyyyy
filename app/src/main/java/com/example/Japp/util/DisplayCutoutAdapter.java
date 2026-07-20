package com.example.Japp.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.Japp.R;

/**
 * 让页面内容避开状态栏 / 刘海 / 底部导航手势区，避免电量、时间等系统图标挡住顶部 UI。
 */
public final class DisplayCutoutAdapter {

    private DisplayCutoutAdapter() {
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        // setContentView 之后再挂 insets，避免 content 子 View 尚未就绪
        content.post(() -> attachToRoot(activity));
    }

    private static void attachToRoot(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }

        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() <= 0) {
            return;
        }

        View root = contentGroup.getChildAt(0);
        Object applied = root.getTag(R.id.tag_system_bar_insets);
        if (Boolean.TRUE.equals(applied)) {
            ViewCompat.requestApplyInsets(root);
            return;
        }
        root.setTag(R.id.tag_system_bar_insets, Boolean.TRUE);

        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom
            );
            // 根布局已经消费了系统栏与刘海安全区，不再让 BottomNavigationView
            // 等子 View 重复增加同一份底部 inset；IME 等其他 inset 仍继续下发。
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout(),
                            Insets.NONE
                    )
                    .build();
        });
        ViewCompat.requestApplyInsets(root);
    }
}
