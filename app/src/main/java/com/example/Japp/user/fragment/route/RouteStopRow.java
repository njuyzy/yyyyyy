package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.Japp.R;
import com.example.Japp.network.models.RouteNode;

final class RouteStopRow {
    private RouteStopRow() {}

    static LinearLayout create(Context context, RouteNode node, int position) {
        return create(context, node, position, false);
    }

    static LinearLayout create(Context context, RouteNode node, int position, boolean showTiming) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(context, 4), 0, dp(context, 2), 0);

        TextView number = new TextView(context);
        number.setGravity(Gravity.CENTER);
        number.setText(String.valueOf(position + 1));
        number.setTextColor(ContextCompat.getColor(context, R.color.route_primary));
        number.setTextSize(10f);
        number.setBackgroundResource(R.drawable.bg_route_stop_number);
        row.addView(number, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));

        TextView name = new TextView(context);
        name.setText(node.getName());
        name.setTextColor(ContextCompat.getColor(context, R.color.route_text_primary));
        name.setTextSize(14f);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(dp(context, 12));
        if (showTiming) {
            LinearLayout details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            details.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView timing = new TextView(context);
            String visitTime = node.getVisitTime();
            timing.setText((TextUtils.isEmpty(visitTime) ? "" : visitTime + " · ")
                    + node.getRecommendedDuration() + " 分钟");
            timing.setTextSize(12f);
            timing.setTextColor(ContextCompat.getColor(context, R.color.route_text_secondary));
            details.addView(timing);
            row.addView(details, nameParams);
        } else {
            row.addView(name, nameParams);
        }

        return row;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
