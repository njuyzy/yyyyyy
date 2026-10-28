package com.example.Japp.network.agent;

import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonObject;
import com.example.Japp.user.util.RoutePlanText;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RouteAgentResult {
    public String kind;
    public String message;
    public List<String> questions;
    public List<String> warnings;
    public List<String> assumptions;
    public JsonObject constraints;
    public boolean feasible;
    public boolean requiresAcceptance;
    public List<RouteNode> nodes;
    public List<Leg> legs;
    public List<Day> days;
    public List<String> removedPoiIds;
    public List<String> addedPoiIds;
    public double estimatedCost;
    public int unknownCosts;

    public boolean isValidPlan() {
        if (!"plan".equals(kind) || !feasible || !requiresAcceptance || nodes == null || nodes.isEmpty()
                || nodes.size() > 20 || days == null || days.isEmpty()) return false;
        Set<String> ids = new HashSet<>();
        for (RouteNode node : nodes) {
            if (node == null || node.getPoiId() == null || !ids.add(node.getPoiId())
                    || node.getName() == null || node.getName().trim().isEmpty()
                    || node.getRecommendedDuration() < 10 || node.getLocation() == null) return false;
            try {
                String[] coordinate = node.getLocation().split(",");
                if (coordinate.length != 2) return false;
                double lon = Double.parseDouble(coordinate[0]);
                double lat = Double.parseDouble(coordinate[1]);
                if (!Double.isFinite(lon) || !Double.isFinite(lat)
                        || lon < 73 || lon > 136 || lat < 3 || lat > 54) return false;
            } catch (RuntimeException invalid) {
                return false;
            }
        }
        return true;
    }

    public boolean isValidClarification() {
        if (!"clarification".equals(kind) || questions == null || questions.isEmpty() || questions.size() > 3)
            return false;
        for (String question : questions) if (question == null || question.trim().isEmpty()) return false;
        return nodes == null || nodes.isEmpty();
    }

    public String description() {
        StringBuilder text = new StringBuilder(message == null ? "" : message);
        if (questions != null) for (String question : questions) text.append("\n\n• ").append(question);
        if (days != null) for (Day day : days) {
            text.append("\n\n第 ").append(day.day).append(" 天");
            if (day.date != null) text.append("（").append(day.date).append("）");
            text.append(" · ").append(day.totalMinutes).append(" 分钟（含通勤和休息）")
                    .append(" · 通勤步行 ").append(day.walkingMeters).append(" 米\n");
            if (day.schedule != null) for (Visit visit : day.schedule) {
                text.append(visit.arrival).append("–").append(visit.departure).append("  ")
                        .append("break".equals(visit.type) ? "休息 / 用餐" : visit.name).append("\n");
                if (visit.learningGoal != null && !visit.learningGoal.isEmpty())
                    text.append("  ").append(visit.learningGoal).append("\n");
            }
        }
        if ("plan".equals(kind)) {
            text.append("\n每人已知交通费用估算：约 ").append(Math.round(estimatedCost)).append(" 元");
            if (unknownCosts > 0) text.append("（另有 ").append(unknownCosts).append(" 项费用待核实）");
            text.append("\n门票、餐饮及其他未核价支出不包含在此数值中，不能据此判断全程免费或预算已满足。");
        }
        if (assumptions != null && !assumptions.isEmpty()) {
            text.append("\n\n规划假设：");
            for (String assumption : assumptions) text.append("\n• ").append(assumption);
        }
        if (warnings != null && !warnings.isEmpty()) {
            text.append("\n\n出发前确认：");
            for (String warning : warnings) text.append("\n• ").append(warning);
        }
        return RoutePlanText.withoutDirections(text.toString());
    }

    private String placeName(String id) {
        if (nodes != null) for (RouteNode node : nodes) if (id != null && id.equals(node.getPoiId()))
            return node.getName();
        return "出发位置";
    }

    public static final class Leg {
        public int day;
        public String origin;
        public String destination;
        public String originName;
        public String destinationName;
        public String mode;
        public int minutes;
        public String source;
        public String note;
        public List<List<Double>> polyline;
    }
    public static final class Day {
        public int day;
        public String date;
        public int totalMinutes;
        public int walkingMeters;
        public List<Visit> schedule;
    }
    public static final class Visit {
        public String type;
        public String name;
        public String arrival;
        public String departure;
        public String learningGoal;
    }
}
