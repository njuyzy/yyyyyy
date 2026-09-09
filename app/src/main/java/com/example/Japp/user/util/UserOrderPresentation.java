package com.example.Japp.user.util;

import com.example.Japp.network.models.Project;
import java.util.Locale;

/** User-facing wording only; backend states and leader screens retain their original semantics. */
public final class UserOrderPresentation {
    private UserOrderPresentation() {}

    public static String phase(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        switch (value) {
            case "OPEN": case "MATCHING": return "OPEN";
            case "CONFIRMED": case "IN_PROGRESS": return "CONFIRMED";
            case "DONE": case "COMPLETED": return "DONE";
            case "CANCELLED": return "CANCELLED";
            default: return "";
        }
    }

    public static String statusLabel(String status) {
        switch (phase(status)) {
            case "OPEN": return "招募中";
            case "CONFIRMED": return "已确认";
            case "DONE": return "已完成";
            case "CANCELLED": return "已取消";
            default: return "";
        }
    }

    public static boolean isCancelled(Project project) {
        return project != null && "CANCELLED".equals(phase(project.getStatus()));
    }

    public static String clean(String text) {
        if (text == null || "null".equalsIgnoreCase(text.trim())) return "";
        return text.trim();
    }

    public static String city(String value) {
        String text = clean(value);
        return text.matches("\\d+") ? "" : text;
    }

    public static String departure(Project project) {
        String date = clean(project.getDepartureDate());
        String time = clean(project.getDepartureTime());
        if (date.contains("T") || date.contains(" ")) {
            String[] parts = date.split("[T ]", 2);
            date = parts[0];
            if (time.isEmpty() && parts.length > 1) time = parts[1];
        }
        if (time.contains("T")) time = time.substring(time.indexOf('T') + 1);
        if (time.matches("\\d{2}:\\d{2}.*")) time = time.substring(0, 5);
        return date.isEmpty() ? "出发时间待确认" : date + (time.isEmpty() ? " · 时间待确认" : "  " + time);
    }

    public static String capacity(Project project) {
        return Math.max(0, project.getCurrentMembers()) + " / "
                + (project.getMaxMembers() > 0 ? project.getMaxMembers() + " 人" : "不限人数");
    }

    public static String remaining(Project project) {
        if (isCancelled(project)) return "本次行程已取消";
        if ("DONE".equals(phase(project.getStatus()))) return "行程已结束";
        if (project.getMaxMembers() <= 0) return "人数上限不限";
        int count = Math.max(0, project.getMaxMembers() - project.getCurrentMembers());
        return count == 0 ? "名额已满" : "还可加入 " + count + " 人";
    }
}
