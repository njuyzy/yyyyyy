package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteNode;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ProjectUiHelper {

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
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "OPEN": return "招募中";
            case "MATCHING": return "匹配中";
            case "CONFIRMED": return "已确认";
            case "IN_PROGRESS": return "进行中";
            case "DONE": return "已完成";
            case "CANCELLED": return "已取消";
            default: return status;
        }
    }
}
