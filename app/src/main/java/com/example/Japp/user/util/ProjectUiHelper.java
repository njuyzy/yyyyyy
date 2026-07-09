package com.example.Japp.user.util;

import android.widget.TextView;

import android.text.TextUtils;

import com.example.Japp.R;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.RouteNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /**
     * 筛选条件：用户已设置的属性之间为「或」关系——路线命中任意一项即保留。
     * 多标签之间同样为「或」。
     */
    public static final class ProjectFilterCriteria {
        public String keyword;
        public String regionAdcode;
        public Set<String> tags = new HashSet<>();
        public String status;
        public String dateFrom;
        public String dateTo;
        public Boolean hasLeader;
        /** 作为普通筛选项参与 OR（如领队端「仅可接」） */
        public Boolean joinableOnly;

        public boolean hasUserFilters() {
            return !TextUtils.isEmpty(keyword)
                    || !TextUtils.isEmpty(regionAdcode)
                    || (tags != null && !tags.isEmpty())
                    || !TextUtils.isEmpty(status)
                    || !TextUtils.isEmpty(dateFrom)
                    || !TextUtils.isEmpty(dateTo)
                    || hasLeader != null
                    || joinableOnly != null;
        }
    }

    public static List<Project> filterProjectsByAnyMatch(List<Project> projects,
                                                         ProjectFilterCriteria criteria) {
        if (projects == null) {
            return new ArrayList<>();
        }
        if (criteria == null || !criteria.hasUserFilters()) {
            return new ArrayList<>(projects);
        }
        List<Project> matched = new ArrayList<>();
        for (Project project : projects) {
            if (matchesAnyUserCriteria(project, criteria)) {
                matched.add(project);
            }
        }
        return matched;
    }

    public static boolean matchesAnyUserCriteria(Project project, ProjectFilterCriteria criteria) {
        if (project == null || criteria == null || !criteria.hasUserFilters()) {
            return project != null;
        }

        if (!TextUtils.isEmpty(criteria.keyword) && matchesKeyword(project, criteria.keyword)) {
            return true;
        }
        if (!TextUtils.isEmpty(criteria.regionAdcode)
                && matchesRegion(project.getRegionAdcode(), criteria.regionAdcode)) {
            return true;
        }
        if (criteria.tags != null && !criteria.tags.isEmpty()
                && matchesAnyTag(project.getTag(), criteria.tags)) {
            return true;
        }
        if (!TextUtils.isEmpty(criteria.status)
                && normalizeStatus(criteria.status).equals(normalizeStatus(project.getStatus()))) {
            return true;
        }
        if ((!TextUtils.isEmpty(criteria.dateFrom) || !TextUtils.isEmpty(criteria.dateTo))
                && matchesDateRange(project.getDepartureDate(), criteria.dateFrom, criteria.dateTo)) {
            return true;
        }
        if (criteria.hasLeader != null && matchesHasLeader(project, criteria.hasLeader)) {
            return true;
        }
        if (criteria.joinableOnly != null) {
            boolean joinable = isJoinable(project);
            if (Boolean.TRUE.equals(criteria.joinableOnly) && joinable) {
                return true;
            }
            if (Boolean.FALSE.equals(criteria.joinableOnly) && !joinable) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesKeyword(Project project, String keyword) {
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return false;
        }
        String title = project.getTitle() != null ? project.getTitle().toLowerCase(Locale.ROOT) : "";
        String tag = project.getTag() != null ? project.getTag().toLowerCase(Locale.ROOT) : "";
        return title.contains(needle) || tag.contains(needle);
    }

    private static boolean matchesRegion(String projectAdcode, String filterAdcode) {
        if (TextUtils.isEmpty(projectAdcode) || TextUtils.isEmpty(filterAdcode)) {
            return false;
        }
        String project = projectAdcode.trim();
        String filter = filterAdcode.trim();
        if (project.equals(filter)) {
            return true;
        }
        // 选省时匹配该省下城市；选市时也可被省码前缀命中
        int prefixLen = Math.min(filter.length(), project.length());
        if (prefixLen >= 2) {
            int compareLen = filter.length() >= 4 ? 4 : 2;
            compareLen = Math.min(compareLen, prefixLen);
            return project.regionMatches(0, filter, 0, compareLen);
        }
        return false;
    }

    private static boolean matchesAnyTag(String projectTag, Set<String> selectedTags) {
        if (TextUtils.isEmpty(projectTag) || selectedTags == null || selectedTags.isEmpty()) {
            return false;
        }
        String normalizedProject = projectTag.trim();
        for (String selected : selectedTags) {
            if (selected != null && selected.trim().equalsIgnoreCase(normalizedProject)) {
                return true;
            }
        }
        // 项目 tag 可能是逗号分隔多标签
        String[] parts = normalizedProject.split("[,，、|/]");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            for (String selected : selectedTags) {
                if (selected != null && selected.trim().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesDateRange(String departureDate, String from, String to) {
        if (TextUtils.isEmpty(departureDate)) {
            return false;
        }
        String date = departureDate.trim();
        if (date.length() >= 10) {
            date = date.substring(0, 10);
        }
        if (!TextUtils.isEmpty(from) && date.compareTo(from.trim()) < 0) {
            return false;
        }
        if (!TextUtils.isEmpty(to) && date.compareTo(to.trim()) > 0) {
            return false;
        }
        return !TextUtils.isEmpty(from) || !TextUtils.isEmpty(to);
    }

    private static boolean matchesHasLeader(Project project, boolean wantHasLeader) {
        boolean hasLeader = project.getLeaderAccountId() != null && project.getLeaderAccountId() > 0;
        return wantHasLeader == hasLeader;
    }

    public static boolean isJoinable(Project project) {
        if (project == null) {
            return false;
        }
        String status = normalizeStatus(project.getStatus());
        if (!STATUS_OPEN.equals(status) && !STATUS_MATCHING.equals(status)) {
            return false;
        }
        return project.getMaxMembers() <= 0 || project.getCurrentMembers() < project.getMaxMembers();
    }
}
