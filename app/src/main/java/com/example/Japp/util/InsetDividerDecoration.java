package com.example.Japp.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;

/** 在列表项之间绘制两端留白的轻量分隔线。 */
public final class InsetDividerDecoration extends RecyclerView.ItemDecoration {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int leftInset;
    private final int rightInset;
    private final int dividerHeight;

    public InsetDividerDecoration(@NonNull Context context, int leftDp, int rightDp) {
        float density = context.getResources().getDisplayMetrics().density;
        leftInset = Math.round((leftDp + 12) * density);
        rightInset = Math.round((rightDp + 12) * density);
        dividerHeight = Math.max(1, Math.round(0.5f * density));
        paint.setColor(ContextCompat.getColor(context, R.color.list_divider));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int count = parent.getAdapter() == null ? 0 : parent.getAdapter().getItemCount();
        if (position != RecyclerView.NO_POSITION && position < count - 1) {
            outRect.bottom = dividerHeight;
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
        int count = parent.getAdapter() == null ? 0 : parent.getAdapter().getItemCount();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION || position >= count - 1) {
                continue;
            }
            float top = child.getBottom() + child.getTranslationY();
            canvas.drawRect(
                    parent.getPaddingLeft() + leftInset,
                    top,
                    parent.getWidth() - parent.getPaddingRight() - rightInset,
                    top + dividerHeight,
                    paint);
        }
    }
}
