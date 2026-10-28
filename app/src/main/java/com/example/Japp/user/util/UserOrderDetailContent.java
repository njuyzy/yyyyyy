package com.example.Japp.user.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import com.example.Japp.R;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.RouteNode;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.example.Japp.user.util.UserOrderPresentation.clean;

/** Builds only the public travel information; IDs, raw status codes and audit fields stay private. */
public final class UserOrderDetailContent {
    private static final int INK = 0xFF182338, MUTED = 0xFF637086, BLUE = 0xFF2563EB;
    private final Activity activity;
    private final Set<String> expandedStops = new HashSet<>();
    private TextView meetingValue;
    private String meetingPlace = "";
    private Project project;

    public interface OnStopSelected { void select(RouteNode node); }

    public UserOrderDetailContent(Activity activity) { this.activity = activity; }

    public void bind(Project project) {
        this.project = project;
        String phase = UserOrderPresentation.phase(project.getStatus());
        TextView status = activity.findViewById(R.id.txtStatus);
        status.setText(UserOrderPresentation.statusLabel(project.getStatus()));
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        status.setTextColor("CANCELLED".equals(phase) ? 0xFFB45353 : BLUE);
        LinearLayout phases = activity.findViewById(R.id.userOrderPhases);
        phases.removeAllViews();
        phases.setVisibility(phase.isEmpty() || "CANCELLED".equals(phase) ? View.GONE : View.VISIBLE);
        String[] labels = {"招募中", "已确认", "已完成"};
        int current = "DONE".equals(phase) ? 2 : "CONFIRMED".equals(phase) ? 1 : 0;
        for (int i = 0; i < labels.length; i++) {
            TextView step = text((i < current ? "✓  " : "●  ") + labels[i], 12, i <= current ? BLUE : MUTED);
            step.setGravity(Gravity.CENTER);
            step.setPadding(0, dp(10), 0, dp(10));
            step.setBackground(background(i <= current ? 0xFFEAF1FF : 0xFFF4F6FA, 10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) params.leftMargin = dp(6);
            phases.addView(step, params);
        }
        String hint;
        switch (phase) {
            case "OPEN": hint = "正在邀请同行伙伴，确认出行安排后即可报名。"; break;
            case "CONFIRMED": hint = "行程已确认，请按约定时间到达集合地点。"; break;
            case "DONE": hint = "本次行程已完成，你可以在这里回顾沿途安排。"; break;
            case "CANCELLED": hint = "本次行程已取消，原出行安排不再生效。"; break;
            default: hint = "行程状态待确认，请下拉刷新后再操作。";
        }
        ((TextView) activity.findViewById(R.id.userOrderStatusHint)).setText(hint);
        ((TextView) activity.findViewById(R.id.userOrderCapacity)).setText(UserOrderPresentation.capacity(project));
        ((TextView) activity.findViewById(R.id.userOrderCapacityHint)).setText(
                UserOrderPresentation.remaining(project) + " · 按每位报名者代表的人数合计");
        LinearProgressIndicator progress = activity.findViewById(R.id.userOrderCapacityProgress);
        progress.setVisibility(project.getMaxMembers() > 0 ? View.VISIBLE : View.GONE);
        progress.setMax(Math.max(1, project.getMaxMembers()));
        progress.setProgressCompat(Math.min(Math.max(0, project.getCurrentMembers()), progress.getMax()), true);
        ((TextView) activity.findViewById(R.id.txtOwner)).setText("发布者  ·  "
                + fallback(project.getPublisherName(), "昵称待完善"));
        ((TextView) activity.findViewById(R.id.userOrderLeader)).setText("领队  ·  "
                + (ProjectUiHelper.hasAssignedLeader(project.getLeaderAccountId())
                ? fallback(project.getLeaderName(), "已安排，昵称待完善") : "等待领队接单"));
        ((TextView) activity.findViewById(R.id.userOrderActionHint)).setText(UserOrderPresentation.remaining(project));

        LinearLayout overview = activity.findViewById(R.id.userOrderOverview);
        clearAfterHeading(overview);
        addField(overview, "出发时间", UserOrderPresentation.departure(project));
        if (!clean(project.getArrivalTime()).isEmpty()) addField(overview, "预计结束", friendlyTime(project.getArrivalTime()));
        meetingValue = addField(overview, "集合地点", "路线首站，正在确认地点…");
        meetingPlace = "";
        TextView copy = action("复制集合信息");
        overview.addView(copy);
        copy.setOnClickListener(v -> {
            if (meetingPlace.isEmpty()) {
                Toast.makeText(activity, "集合地点尚未确认，请稍后刷新", Toast.LENGTH_SHORT).show();
                return;
            }
            copy("集合信息", UserOrderPresentation.departure(project) + "\n" + meetingPlace);
        });

        LinearLayout requirements = activity.findViewById(R.id.userOrderRequirements);
        clearAfterHeading(requirements);
        addExpandable(requirements, "对同行伙伴的要求", fallback(project.getParticipantRequirements(), "发布者未设置额外要求"));
        addExpandable(requirements, "对领队的要求", fallback(project.getLeaderRequirements(), "发布者未设置额外要求"));
        addField(requirements, "报名提醒", "一位报名者可代表多位同行人，请填写实际参团人数。请在规定时间前到达路线首站，营业时间以场所当天公告为准。");
    }

    public void bindStops(List<RouteNode> nodes, OnStopSelected listener) {
        LinearLayout stops = activity.findViewById(R.id.userOrderStops);
        stops.removeAllViews();
        if (!nodes.isEmpty()) {
            RouteNode first = nodes.get(0);
            meetingPlace = fallback(first.getName(), "路线首站");
            if (!clean(first.getAddress()).isEmpty()) meetingPlace += "\n" + clean(first.getAddress());
            meetingValue.setText(meetingPlace);
        }
        for (int i = 0; i < nodes.size(); i++) {
            RouteNode node = nodes.get(i);
            String key = clean(node.getPoiId()) + "|" + clean(node.getName()) + "|" + node.getVisitOrder();
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(4), dp(14), dp(6));
            card.setBackground(background(0xFFF5F8FE, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            stops.addView(card, params);
            String caption = i == 0 ? "集合 · 首站" : i == nodes.size() - 1 ? "最后一站" : "沿途停留";
            TextView header = text(caption + "\n" + fallback(node.getName(), "行程地点"), 16, INK);
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, dp(12), 0, dp(8));
            card.addView(header);
            String duration = node.getRecommendedDuration() > 0
                    ? "建议停留 " + ProjectUiHelper.formatDuration(node.getRecommendedDuration()) : "停留时长待安排";
            TextView subtitle = text(duration, 12, MUTED);
            card.addView(subtitle);
            TextView toggle = action("查看地点详情  ⌄");
            card.addView(toggle);
            LinearLayout details = new LinearLayout(activity);
            details.setOrientation(LinearLayout.VERTICAL);
            card.addView(details);
            addField(details, "地址", fallback(node.getAddress(), "详细地址待补充"));
            if (!clean(node.getVisitTime()).isEmpty()) addField(details, "计划到访", friendlyTime(node.getVisitTime()));
            String hours = clean(node.getOpentimeToday());
            if (hours.isEmpty()) hours = clean(node.getOpentimeWeek());
            addField(details, "营业时间", hours.isEmpty() ? "尚未提供，出发前请确认" : hours);
            String visitNotes = RoutePlanText.withoutDirections(node.getNotes());
            if (!visitNotes.isEmpty()) addField(details, "游览提示", visitNotes);
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            details.addView(actions);
            TextView locate = action("在地图中查看 ↗");
            actions.addView(locate, new LinearLayout.LayoutParams(0, -2, 1));
            locate.setOnClickListener(v -> listener.select(node));
            if (!clean(node.getAddress()).isEmpty()) {
                TextView copy = action("复制地址");
                actions.addView(copy, new LinearLayout.LayoutParams(-2, -2));
                copy.setOnClickListener(v -> copy("地点地址", node.getName() + "\n" + node.getAddress()));
            }
            boolean expanded = expandedStops.contains(key);
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            toggle.setText(expanded ? "收起地点详情  ⌃" : "查看地点详情  ⌄");
            View.OnClickListener expand = v -> {
                boolean show = details.getVisibility() != View.VISIBLE;
                TransitionManager.beginDelayedTransition(card, new AutoTransition().setDuration(180));
                details.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? "收起地点详情  ⌃" : "查看地点详情  ⌄");
                if (show) expandedStops.add(key); else expandedStops.remove(key);
            };
            toggle.setOnClickListener(expand);
            header.setOnClickListener(expand);
        }
    }

    public void routeUnavailable() {
        if (meetingValue == null) return;
        String point = clean(project.getStartPoint());
        // Coordinates and JSON are storage formats, not usable meeting instructions.
        if (!point.isEmpty() && !point.startsWith("{") && !point.startsWith("[")
                && !point.matches("[-+\\d.,，\\s]+")) {
            meetingPlace = point;
            meetingValue.setText(point);
        } else meetingValue.setText("集合地点待确认");
    }

    private void addExpandable(LinearLayout parent, String label, String value) {
        TextView body = addField(parent, label, value);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView toggle = action("展开全文  ⌄");
        toggle.setVisibility(View.GONE);
        parent.addView(toggle);
        body.post(() -> {
            if (body.getLayout() != null && body.getLayout().getLineCount() > 0) {
                int last = body.getLayout().getLineCount() - 1;
                toggle.setVisibility(body.getLayout().getEllipsisCount(last) > 0 ? View.VISIBLE : View.GONE);
            }
        });
        toggle.setOnClickListener(v -> {
            boolean expand = body.getMaxLines() == 3;
            TransitionManager.beginDelayedTransition(parent, new AutoTransition().setDuration(180));
            body.setMaxLines(expand ? Integer.MAX_VALUE : 3);
            toggle.setText(expand ? "收起  ⌃" : "展开全文  ⌄");
        });
    }

    private TextView addField(LinearLayout parent, String label, String value) {
        TextView title = text(label, 12, MUTED);
        title.setPadding(0, dp(16), 0, dp(5));
        parent.addView(title);
        TextView body = text(value, 14, INK);
        body.setTextIsSelectable(true);
        parent.addView(body);
        return body;
    }

    private TextView action(String value) {
        TextView view = text(value, 14, BLUE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(48));
        android.util.TypedValue ripple = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        view.setBackgroundResource(ripple.resourceId);
        view.setFocusable(true);
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setLineSpacing(dp(4), 1f);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void copy(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(activity, "已复制" + label, Toast.LENGTH_SHORT).show();
    }

    private void clearAfterHeading(LinearLayout parent) {
        if (parent.getChildCount() > 1) parent.removeViews(1, parent.getChildCount() - 1);
    }
    private String fallback(String value, String fallback) { return clean(value).isEmpty() ? fallback : clean(value); }
    private String friendlyTime(String value) {
        return clean(value).replace('T', ' ').replaceAll("(\\d{2}:\\d{2}):\\d{2}(?:\\.\\d+)?", "$1");
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
