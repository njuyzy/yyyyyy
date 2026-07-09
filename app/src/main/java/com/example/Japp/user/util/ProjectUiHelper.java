package com.example.Japp.user.util;

import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.Japp.R;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.RouteNode;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ProjectUiHelper {

    private ProjectUiHelper() {}

    /** 招募中：开放报名，等待成员加入，领队可接单 */
    public static final String STATUS_OPEN = "OPEN";
    /** 匹配中：成员/领队匹配阶段，仍可报名或接单 */
    public static final String STATUS_MATCHING = "MATCHING";
    /** 已确认：成团确认，行程已定，不可再接单 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 进行中：研学活动进行中，不可接单 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 已完成：活动结束，不可接单 */
    public static final String STATUS_DONE = "DONE";
    /** 已取消：项目取消，不可接单 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Nullable
    public static String normalizeStatus(@Nullable String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        switch (trimmed) {
            case "招募中":
                return STATUS_OPEN;
            case "匹配中":
                return STATUS_MATCHING;
            case "已确认":
                return STATUS_CONFIRMED;
            case "进行中":
                return STATUS_IN_PROGRESS;
            case "已完成":
                return STATUS_DONE;
            case "已取消":
                return STATUS_CANCELLED;
            default:
                return trimmed.toUpperCase(Locale.ROOT);
        }
    }

    public static boolean hasAssignedLeader(@Nullable Integer leaderAccountId) {
        return leaderAccountId != null && leaderAccountId > 0;
    }

    /** 领队是否仍可接单（无领队且处于招募/匹配阶段） */
    public static boolean canLeaderAccept(@Nullable Project project) {
        if (project == null) {
            return false;
        }
        if (hasAssignedLeader(project.getLeaderAccountId())) {
            return false;
        }
        return isLeaderAcceptableStatus(project.getStatus());
    }

    /** 与 onlyAvailable 一致：OPEN / MATCHING 阶段可报名或可接单 */
    public static boolean isLeaderAcceptableStatus(@Nullable String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return true;
        }
        return STATUS_OPEN.equals(normalized) || STATUS_MATCHING.equals(normalized);
    }

    public static boolean isTerminalStatus(@Nullable String status) {
        String normalized = normalizeStatus(status);
        return STATUS_DONE.equals(normalized) || STATUS_CANCELLED.equals(normalized);
    }

    public static String statusDescription(@Nullable String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return "状态未知";
        }
        switch (normalized) {
            case STATUS_OPEN:
                return "开放招募成员，领队可接单";
            case STATUS_MATCHING:
                return "匹配成员或领队中，仍可接单";
            case STATUS_CONFIRMED:
                return "成团已确认，不可接单";
            case STATUS_IN_PROGRESS:
                return "研学进行中，不可接单";
            case STATUS_DONE:
                return "活动已完成，不可接单";
            case STATUS_CANCELLED:
                return "项目已取消，不可接单";
            default:
                return statusLabel(normalized);
        }
    }

    /** 排序权重：招募中 → 匹配中 → 已确认 → 进行中 → 已完成 → 已取消 */
    public static int statusSortOrder(@Nullable String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return 99;
        }
        switch (normalized) {
            case STATUS_OPEN:
                return 0;
            case STATUS_MATCHING:
                return 1;
            case STATUS_CONFIRMED:
                return 2;
            case STATUS_IN_PROGRESS:
                return 3;
            case STATUS_DONE:
                return 4;
            case STATUS_CANCELLED:
                return 5;
            default:
                return 99;
        }
    }

    public static int compareProjectsByStatus(@Nullable Project left, @Nullable Project right) {
        int order = Integer.compare(
                statusSortOrder(left != null ? left.getStatus() : null),
                statusSortOrder(right != null ? right.getStatus() : null));
        if (order != 0) {
            return order;
        }
        int leftId = left != null ? left.getId() : 0;
        int rightId = right != null ? right.getId() : 0;
        return Integer.compare(leftId, rightId);
    }

    public static void sortProjectsByStatus(@Nullable List<Project> projects) {
        if (projects == null || projects.size() < 2) {
            return;
        }
        Collections.sort(projects, ProjectUiHelper::compareProjectsByStatus);
    }

    @DrawableRes
    public static int statusBackgroundRes(@Nullable String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return R.drawable.tag_status_unknown;
        }
        switch (normalized) {
            case STATUS_OPEN:
                return R.drawable.tag_status_open;
            case STATUS_MATCHING:
                return R.drawable.tag_status_matching;
            case STATUS_CONFIRMED:
                return R.drawable.tag_status_confirmed;
            case STATUS_IN_PROGRESS:
                return R.drawable.tag_status_in_progress;
            case STATUS_DONE:
                return R.drawable.tag_status_done;
            case STATUS_CANCELLED:
                return R.drawable.tag_status_cancelled;
            default:
                return R.drawable.tag_status_unknown;
        }
    }

    public static void bindStatusBadge(@NonNull TextView textView, @Nullable String status) {
        textView.setText(statusLabel(status));
        textView.setTextColor(textView.getContext().getColor(R.color.white));
        textView.setBackgroundResource(statusBackgroundRes(status));
    }

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
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return "未知";
        }
        switch (normalized) {
            case STATUS_OPEN: return "招募中";
            case STATUS_MATCHING: return "匹配中";
            case STATUS_CONFIRMED: return "已确认";
            case STATUS_IN_PROGRESS: return "进行中";
            case STATUS_DONE: return "已完成";
            case STATUS_CANCELLED: return "已取消";
            default: return normalized;
        }
    }
}
