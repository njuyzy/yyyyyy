package com.example.Japp.user.util;

import android.widget.TextView;

import com.example.Japp.R;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.RouteNode;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ProjectUiHelper {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_MATCHING = "MATCHING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private ProjectUiHelper() {}

    public static String regionAdcodeToCity(String adcode) {
        if (adcode == null || adcode.length() < 4) {
            return "";
        }
        switch (adcode.substring(0, 4)) {
            case "1101": return "北京市";
            case "1201": return "天津市";
            case "2101": return "沈阳市";
            case "2201": return "长春市";
            case "2301": return "哈尔滨市";
            case "3101": return "上海市";
            case "3201": return "南京市";
            case "3301": return "杭州市";
            case "3501": return "福州市";
            case "3601": return "南昌市";
            case "3701": return "济南市";
            case "4101": return "郑州市";
            case "4201": return "武汉市";
            case "4301": return "长沙市";
            case "4401": return "广州市";
            case "4403": return "深圳市";
            case "4501": return "南宁市";
            case "5001": return "重庆市";
            case "5101": return "成都市";
            case "5201": return "贵阳市";
            case "5301": return "昆明市";
            case "6101": return "西安市";
            default: return adcode;
        }
    }

    public static String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "暂无";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0 && mins > 0) {
            return hours + "小时" + mins + "分钟";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        return mins + "分钟";
    }

    public static String buildRouteSummary(List<RouteNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "途经：暂无景点信息";
        }
        List<RouteNode> ordered = new java.util.ArrayList<>(nodes);
        Collections.sort(ordered, Comparator.comparingInt(RouteNode::getVisitOrder));
        StringBuilder sb = new StringBuilder("途经：");
        for (int i = 0; i < ordered.size(); i++) {
            String name = ordered.get(i).getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (sb.length() > 3) {
                sb.append(" → ");
            }
            sb.append(name);
        }
        return sb.length() > 3 ? sb.toString() : "途经：暂无景点信息";
    }

    public static int sumDurationMinutes(List<RouteNode> nodes) {
        if (nodes == null) {
            return 0;
        }
        int total = 0;
        for (RouteNode node : nodes) {
            total += node.getRecommendedDuration();
        }
        return total;
    }

    public static String statusLabel(String status) {
        switch (normalizeStatus(status)) {
            case STATUS_OPEN: return "招募中";
            case STATUS_MATCHING: return "匹配中";
            case STATUS_CONFIRMED: return "已确认";
            case STATUS_IN_PROGRESS: return "进行中";
            case STATUS_DONE: return "已完成";
            case STATUS_CANCELLED: return "已取消";
            default: return status == null || status.isEmpty() ? "未知" : status;
        }
    }

    public static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean hasAssignedLeader(Integer leaderAccountId) {
        return leaderAccountId != null && leaderAccountId > 0;
    }

    public static boolean isLeaderAcceptableStatus(String status) {
        String normalized = normalizeStatus(status);
        return STATUS_OPEN.equals(normalized) || STATUS_MATCHING.equals(normalized);
    }

    public static void bindStatusBadge(TextView view, String status) {
        if (view == null) {
            return;
        }
        view.setText(statusLabel(status));
        int background;
        switch (normalizeStatus(status)) {
            case STATUS_OPEN:
                background = R.drawable.tag_status_open;
                break;
            case STATUS_MATCHING:
                background = R.drawable.tag_status_matching;
                break;
            case STATUS_CONFIRMED:
                background = R.drawable.tag_status_confirmed;
                break;
            case STATUS_IN_PROGRESS:
                background = R.drawable.tag_status_in_progress;
                break;
            case STATUS_DONE:
                background = R.drawable.tag_status_done;
                break;
            case STATUS_CANCELLED:
                background = R.drawable.tag_status_cancelled;
                break;
            default:
                background = R.drawable.tag_status_unknown;
                break;
        }
        view.setBackgroundResource(background);
    }

    private static int statusSortOrder(String status) {
        switch (normalizeStatus(status)) {
            case STATUS_OPEN: return 0;
            case STATUS_MATCHING: return 1;
            case STATUS_CONFIRMED: return 2;
            case STATUS_IN_PROGRESS: return 3;
            case STATUS_DONE: return 4;
            case STATUS_CANCELLED: return 5;
            default: return 99;
        }
    }

    public static void sortProjectsByStatus(List<Project> projects) {
        if (projects == null) {
            return;
        }
        projects.sort(Comparator.comparingInt(p -> statusSortOrder(p.getStatus())));
    }

    public static int compareProjectsByStatus(Project left, Project right) {
        return Integer.compare(
                statusSortOrder(left != null ? left.getStatus() : null),
                statusSortOrder(right != null ? right.getStatus() : null));
    }
}
