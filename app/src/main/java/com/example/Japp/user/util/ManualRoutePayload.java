package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convert display times only at the manual-route API boundary; never mutate the user's draft. */
public final class ManualRoutePayload {
    private static final Gson GSON = new Gson();
    private static final Pattern DAY = Pattern.compile("^第\\s*(\\d+)\\s*天\\s+(\\d{2}:\\d{2}(?::\\d{2})?)$");
    private static final DateTimeFormatter SPACE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm[:ss]")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private ManualRoutePayload() {}

    public static List<RouteNode> prepare(List<RouteNode> nodes, String departureDate) {
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("路线没有可发布的地点");
        List<RouteNode> result = new ArrayList<>(nodes.size());
        for (RouteNode node : nodes) {
            if (node == null) throw new IllegalArgumentException("路线包含无效地点，请重新选择后发布");
            RouteNode copy = GSON.fromJson(GSON.toJson(node), RouteNode.class);
            copy.setVisitTime(toIso(node.getVisitTime(), departureDate));
            result.add(copy);
        }
        return result;
    }

    public static String toIso(String value, String departureDate) {
        if (value == null || value.trim().isEmpty()) return null;
        String time = value.trim();
        try {
            Matcher day = DAY.matcher(time);
            LocalDateTime parsed;
            if (day.matches()) {
                int index = Integer.parseInt(day.group(1));
                if (index < 1 || index > 7) throw new IllegalArgumentException();
                parsed = LocalDate.parse(departureDate).plusDays(index - 1)
                        .atTime(LocalTime.parse(day.group(2)));
            } else if (time.matches("\\d{2}:\\d{2}(?::\\d{2})?")) {
                parsed = LocalDate.parse(departureDate).atTime(LocalTime.parse(time));
            } else if (time.contains("T")) {
                parsed = LocalDateTime.parse(time);
            } else parsed = LocalDateTime.parse(time, SPACE_TIME);
            return OUTPUT.format(parsed);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("路线游览时间无法识别，请调整路线后发布");
        }
    }
}
