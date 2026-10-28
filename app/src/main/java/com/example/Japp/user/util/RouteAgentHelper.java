package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RouteAgentHelper {
    private static final Gson GSON = new Gson();
    private static final Pattern RELATIVE_TIME = Pattern.compile("第([1-7])天\\s+([0-2][0-9]:[0-5][0-9])");
    private RouteAgentHelper() {}

    public static RouteAgent.Request request(int accountId, String memoryId, String message,
            List<RouteAgent.Turn> history, JsonObject constraints, List<RouteNode> nodes) {
        if (accountId <= 0) throw new IllegalArgumentException("请先登录");
        if (message == null || message.trim().isEmpty() || message.trim().length() > 4000)
            throw new IllegalArgumentException("路线需求需为 1～4000 字");
        if (memoryId == null || !memoryId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("对话标识无效，请新建对话");
        if (nodes.size() > 20) throw new IllegalArgumentException("一次最多规划 20 个地点");
        RouteAgent.Request request = new RouteAgent.Request();
        request.requestId = UUID.randomUUID().toString();
        request.accountId = accountId;
        request.memoryId = memoryId;
        request.message = message.trim();
        if (history != null) {
            for (RouteAgent.Turn turn : history) {
                if (turn != null && ("user".equals(turn.role) || "assistant".equals(turn.role))
                        && turn.content != null && !turn.content.trim().isEmpty()) {
                    request.history.add(new RouteAgent.Turn(turn.role, limit(turn.content, 8000)));
                }
            }
            while (request.history.size() > 24) request.history.remove(0);
        }
        request.constraints = constraints == null ? new JsonObject() : constraints.deepCopy();
        Set<String> ids = new HashSet<>();
        for (RouteNode node : nodes) {
            if (node == null || node.getPoiId() == null || !node.getPoiId().matches("[A-Za-z0-9_-]{1,80}"))
                throw new IllegalArgumentException("当前路线包含无法识别的地点，请重新搜索后再规划");
            if (!ids.add(node.getPoiId())) throw new IllegalArgumentException("当前路线包含重复地点，请先移除重复项");
            RouteAgent.Stop stop = new RouteAgent.Stop();
            stop.poiId = node.getPoiId();
            stop.name = limit(node.getName(), 160);
            stop.notes = limit(node.getNotes(), 2000);
            stop.recommendedDuration = Math.max(0, Math.min(960, node.getRecommendedDuration()));
            request.currentRoute.add(stop);
        }
        return request;
    }

    public static String snapshot(List<RouteNode> nodes) { return GSON.toJson(nodes); }

    /** A malformed completion must not enter history, render a map, or replace the current route. */
    public static boolean validResult(RouteAgent.Result result) {
        if (result == null) return false;
        if ("clarification".equals(result.kind)) {
            if (result.questions == null || result.questions.isEmpty() || result.questions.size() > 3
                    || (result.nodes != null && !result.nodes.isEmpty())
                    || (result.legs != null && !result.legs.isEmpty())) return false;
            for (String question : result.questions)
                if (question == null || question.trim().isEmpty()) return false;
            return true;
        }
        if (!"plan".equals(result.kind) || !result.feasible || !result.requiresAcceptance
                || result.issues == null || !result.issues.isEmpty()
                || result.nodes == null || result.nodes.isEmpty() || result.nodes.size() > 20
                || result.days == null || result.days.isEmpty() || result.days.size() > 7
                || !Double.isFinite(result.estimatedCost) || result.estimatedCost < 0
                || result.unknownCosts < 0) return false;
        Set<String> ids = new HashSet<>();
        for (RouteNode node : result.nodes) {
            if (node == null || node.getPoiId() == null || !node.getPoiId().matches("[A-Za-z0-9_-]{1,80}")
                    || !ids.add(node.getPoiId()) || node.getName() == null || node.getName().trim().isEmpty()
                    || node.getRecommendedDuration() < 10 || node.getRecommendedDuration() > 480
                    || node.getLocation() == null || node.getVisitTime() == null) return false;
            try {
                String[] point = node.getLocation().split(",", -1);
                if (point.length != 2 || !validCoordinate(Double.parseDouble(point[0]), Double.parseDouble(point[1])))
                    return false;
                // This date is only a syntax-check anchor; it is never applied to the user's route.
                if (visitTimeToIso(node.getVisitTime(), LocalDate.of(2000, 1, 1)) == null) return false;
            } catch (RuntimeException invalid) { return false; }
        }
        Set<Integer> dayNumbers = new HashSet<>();
        for (RouteAgent.Day day : result.days) {
            if (day == null || day.day < 1 || day.day > 7 || !dayNumbers.add(day.day)
                    || day.totalMinutes < 0 || (day.walkingMeters != null && day.walkingMeters < 0)) return false;
            if (day.schedule != null && day.schedule.contains(null)) return false;
        }
        return result.legs == null || !result.legs.contains(null);
    }

    public static boolean validCoordinate(double longitude, double latitude) {
        return Double.isFinite(longitude) && Double.isFinite(latitude)
                && longitude >= 73 && longitude <= 136 && latitude >= 3 && latitude <= 54;
    }

    /** Reject the whole bad segment instead of connecting across missing or invalid geometry. */
    public static boolean validPolyline(List<List<Double>> points) {
        if (points == null || points.size() < 2) return false;
        for (List<Double> point : points)
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null
                    || !validCoordinate(point.get(0), point.get(1))) return false;
        return true;
    }

    public static String progress(RouteAgent.Run run) {
        StringBuilder text = new StringBuilder(run.stage == null || run.stage.isEmpty()
                ? "路线助手正在处理…" : run.stage);
        if (run.events != null) {
            // events is a full snapshot: replace the visible summary, never append it again.
            int start = Math.max(0, run.events.size() - 5);
            for (int i = start; i < run.events.size(); i++) {
                RouteAgent.Event event = run.events.get(i);
                if (event == null || event.label == null) continue;
                String state = "completed".equals(event.status) ? "完成" :
                        ("failed".equals(event.status) ? "本步骤未完成" :
                        ("needs_revision".equals(event.status) ? "调整中" : "进行中"));
                text.append("\n• ").append(event.label).append(" · ").append(state);
            }
        }
        return text.toString();
    }

    public static String describe(RouteAgent.Result result) {
        StringBuilder text = new StringBuilder();
        if ("clarification".equals(result.kind)) {
            text.append("需要补充信息\n\n").append(limit(result.message, 500));
            appendLines(text, result.questions);
            return text.toString().trim();
        }
        text.append("候选路线 · 等待确认\n\n").append(RoutePlanText.withoutDirections(limit(result.message, 2500)));
        if (result.nodes != null) for (RouteNode node : result.nodes) {
            text.append("\n").append(node.getVisitOrder()).append(". ").append(node.getName())
                    .append(" · ").append(node.getVisitTime()).append(" · ")
                    .append(node.getRecommendedDuration()).append(" 分钟");
            String notes = RoutePlanText.withoutDirections(node.getNotes());
            if (!notes.isEmpty()) text.append("\n   ").append(notes);
        }
        if (result.days != null) for (RouteAgent.Day day : result.days) {
            text.append("\n第").append(day.day).append("天：约 ").append(day.totalMinutes)
                    .append(" 分钟，通勤步行")
                    .append(day.walkingMeters == null ? "待核实" : "约 " + day.walkingMeters + " 米");
            if (day.finishTime != null) text.append("，结束 ").append(day.finishTime);
            if (day.schedule != null) for (RouteAgent.Visit visit : day.schedule) {
                if (visit == null) continue;
                text.append("\n  ").append(limit(visit.arrival, 20)).append("–")
                        .append(limit(visit.departure, 20)).append(" ")
                        .append("break".equals(visit.type) ? "休息 / 用餐" : limit(visit.name, 160));
                String goal = RoutePlanText.withoutDirections(visit.learningGoal);
                if (!goal.isEmpty()) text.append("\n    ").append(goal);
            }
        }
        // Keep legs for timing, validation and map rendering, not conversational navigation output.
        text.append(String.format(Locale.CHINA, "\n\n已知交通费用估算：每人 ¥%.2f（非全部费用）", result.estimatedCost));
        text.append("\n未核价项目：").append(result.unknownCosts).append(" 项");
        text.append("\n门票、餐饮及其他未核价支出不包含在内，不能据此承诺免费或全程预算满足。");
        if (result.assumptions != null && !result.assumptions.isEmpty()) {
            text.append("\n\n方案假设："); appendLines(text, result.assumptions);
        }
        Set<String> warnings = new java.util.LinkedHashSet<>();
        if (result.warnings != null) warnings.addAll(result.warnings);
        if (result.legs != null) for (RouteAgent.Leg leg : result.legs) {
            if (leg == null) continue;
            String advisory = RoutePlanText.advisory(leg.note);
            if (!advisory.isEmpty()) warnings.add(advisory);
        }
        if (!warnings.isEmpty()) {
            text.append("\n\n出行提醒：");
            for (String warning : warnings) if (warning != null) text.append("\n• ").append(warning);
        }
        if (result.removedPoiIds != null && !result.removedPoiIds.isEmpty())
            text.append("\n\n相对本轮参考方案，减少了 ").append(result.removedPoiIds.size()).append(" 个地点。");
        text.append("\n\n确认后才会替换当前路线。");
        return RoutePlanText.withoutDirections(text.toString());
    }

    public static String visitTimeToIso(String value, LocalDate firstDay) {
        if (value == null || value.trim().isEmpty()) return null;
        String time = value.trim();
        Matcher relative = RELATIVE_TIME.matcher(time);
        if (relative.matches()) {
            if (firstDay == null) throw new IllegalArgumentException("请先选择行程第一天日期");
            time = firstDay.plusDays(Integer.parseInt(relative.group(1)) - 1)
                    + "T" + relative.group(2);
        } else {
            time = time.replace(' ', 'T');
        }
        try {
            return LocalDateTime.parse(time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("行程时间格式无效，请重新规划或调整日期");
        }
    }

    public static String error(int status, String raw) {
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            if (object.has("error") && object.get("error").isJsonPrimitive())
                return safeError(object.get("error").getAsString());
            if (object.has("detail") && object.get("detail").isJsonArray()) {
                StringBuilder message = new StringBuilder("请求参数有误");
                for (JsonElement detail : object.getAsJsonArray("detail")) {
                    JsonObject field = detail.getAsJsonObject();
                    if (field.has("msg")) message.append("\n").append(field.get("loc"))
                            .append("：").append(field.get("msg").getAsString());
                }
                return limit(message.toString(), 600);
            }
        } catch (RuntimeException ignored) { }
        switch (status) {
            case 401: return "登录已失效，请重新登录";
            case 403: return "账号与登录身份不一致，请重新登录";
            case 404: return "规划任务不存在或已过期，请重新发起规划";
            case 409: return "当前会话已有规划任务或请求内容冲突，请等待任务完成后重试";
            case 422: return "规划参数不符合要求，请检查地点和需求";
            case 429: return "规划请求较多，请稍后重试";
            default: return "路线服务暂时不可用（" + status + "），请稍后重试";
        }
    }

    public static String safeError(String error) {
        if (error == null || error.trim().isEmpty()) return "本轮规划失败，请稍后重试";
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("arrearage") || lower.contains("overdue-payment"))
            return "模型供应商账户不可用，请联系管理员检查欠费或服务权限；不是你的登录失效";
        if (lower.contains("embeddings") && (lower.contains("connection refused") || lower.contains("connect")))
            return "路线检索服务暂不可用，请联系管理员检查向量嵌入服务连接";
        if (error.matches("(?is).*(sk-[a-z0-9_-]+|bearer\\s|https?://|stacktrace|jdbc:).*"))
            return "路线服务执行失败，请联系管理员检查模型、检索或地图服务配置";
        return limit(error.trim(), 600);
    }

    public static String transportError(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof javax.net.ssl.SSLException)
                return "路线服务的 HTTPS 证书或安全连接异常，请联系管理员；未绕过证书校验";
            if (error instanceof java.net.ConnectException || error instanceof java.net.UnknownHostException)
                return "无法连接路线服务，请检查后端地址、端口和部署状态；重试将恢复原请求";
            if (error instanceof java.net.SocketTimeoutException)
                return "路线服务响应超时，点击重试可恢复原任务，不会重复生成";
            if (error instanceof com.google.gson.JsonParseException
                    || error instanceof com.google.gson.stream.MalformedJsonException)
                return "路线接口返回的内容不是有效 JSON，请检查后端或网关路由；当前路线未变";
        }
        return "连接中断，点击重试可查询原任务或安全重发原请求";
    }

    private static void appendLines(StringBuilder text, List<String> lines) {
        if (lines != null) for (String line : lines) text.append("\n• ").append(line);
    }
    private static String limit(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
