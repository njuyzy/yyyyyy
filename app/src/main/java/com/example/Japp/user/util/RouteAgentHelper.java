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
        text.append("候选路线 · 等待确认\n\n").append(limit(result.message, 2500));
        if (result.nodes != null) for (RouteNode node : result.nodes) {
            text.append("\n").append(node.getVisitOrder()).append(". ").append(node.getName())
                    .append(" · ").append(node.getVisitTime()).append(" · ")
                    .append(node.getRecommendedDuration()).append(" 分钟");
            if (node.getNotes() != null && !node.getNotes().isEmpty())
                text.append("\n   ").append(node.getNotes());
        }
        if (result.days != null) for (RouteAgent.Day day : result.days) {
            text.append("\n第").append(day.day).append("天：约 ").append(day.totalMinutes)
                    .append(" 分钟，通勤步行约 ").append(day.walkingMeters).append(" 米，结束 ")
                    .append(day.finishTime);
        }
        text.append(String.format(Locale.CHINA, "\n\n已知费用估算：每人 ¥%.2f（非全部费用）", result.estimatedCost));
        text.append("\n未核价项目：").append(result.unknownCosts).append(" 项");
        if (result.assumptions != null && !result.assumptions.isEmpty()) {
            text.append("\n\n方案假设："); appendLines(text, result.assumptions);
        }
        if (result.warnings != null && !result.warnings.isEmpty()) {
            text.append("\n\n出行提醒："); appendLines(text, result.warnings);
        }
        if (result.removedPoiIds != null && !result.removedPoiIds.isEmpty())
            text.append("\n\n本方案将移除 ").append(result.removedPoiIds.size()).append(" 个原有地点，请确认行程后应用。");
        text.append("\n\n确认后才会替换当前路线。");
        return text.toString();
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
        if (lower.contains("embeddings") && (lower.contains("connection refused") || lower.contains("connect")))
            return "路线检索服务暂不可用，请联系管理员检查向量嵌入服务连接";
        if (error.matches("(?is).*(sk-[a-z0-9_-]+|bearer\\s|https?://|stacktrace|jdbc:).*"))
            return "路线服务执行失败，请联系管理员检查模型、检索或地图服务配置";
        return limit(error.trim(), 600);
    }

    private static void appendLines(StringBuilder text, List<String> lines) {
        if (lines != null) for (String line : lines) text.append("\n• ").append(line);
    }
    private static String limit(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
