package com.example.Japp.user.util;

import com.example.Japp.network.models.RouteAgent;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, conservative client-side context adaptation; not a substitute for backend intent understanding. */
public final class RouteConversationContext {
    private static final String NUMBER = "([+-]?\\d+(?:\\.\\d+)?)";
    private static final String CHANGE = "(?:改为|改成|改到|调整为|调整到|降低到|提高到|控制在|不超过|最多|上限|是|为|[:：])?\\s*";
    private static final Pattern BUDGET = Pattern.compile("预算\\s*" + CHANGE + "(?:每人\\s*)?" + NUMBER + "\\s*(?:元|块)?");
    private static final Pattern WALKING = Pattern.compile("(?:步行上限|通勤步行|步行)\\s*" + CHANGE + NUMBER + "\\s*(公里|千米|米)");
    private static final Pattern DURATION = Pattern.compile("(?:每天(?:可用时长)?|每日|总时长|行程时长|可用时长|时间改为|时间改成|只玩|只有)\\s*" + CHANGE + NUMBER + "\\s*(小时|分钟)");
    private static final Pattern CITY = Pattern.compile("(?:城市|目的地)\\s*(?:改为|改成|换成|换到|是|为|[:：])\\s*([\\p{IsHan}]{2,12}?)(?:市)?(?=[，。；,;\\s]|$)");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{4})[-年/](\\d{1,2})[-月/](\\d{1,2})日?(?!\\d)");
    private static final Pattern REFERENCE = Pattern.compile("第\\s*([一二两三四五六七八九十\\d]+)\\s*(?:个|站|处|项|景点|地点)");
    private static final Pattern MODIFICATION = Pattern.compile("删|移除|去掉|换|替换|改成|改为|改到|增加|新增|取消|不要了|不去|只保留|预算.*\\d");

    private RouteConversationContext() {}

    public static RouteAgent.Request prepare(int accountId, String memoryId, String message,
            List<RouteAgent.Turn> history, JsonObject constraints, List<RouteNode> actualNodes,
            RouteAgent.Result discussionPlan, boolean discussionApplied, String discussionSnapshot) {
        JsonObject patch = explicitPatch(message);
        JsonObject updated = merge(constraints, patch);
        boolean candidate = discussionPlan != null && RouteAgentHelper.validResult(discussionPlan)
                && !discussionApplied && RouteAgentHelper.snapshot(actualNodes).equals(discussionSnapshot);
        List<RouteNode> referenceNodes = candidate ? discussionPlan.nodes : actualNodes;
        if (patch.has("city") && constraints != null && constraints.has("city")
                && !patch.get("city").equals(constraints.get("city"))) {
            candidate = false;
            referenceNodes = Collections.emptyList();
        }
        applyPointChanges(message, referenceNodes, patch, updated);
        RouteAgent.Request request = RouteAgentHelper.request(accountId, memoryId, message, history, updated, referenceNodes);
        request.explicitConstraintPatch = patch;
        request.usesCandidateContext = candidate;
        return request;
    }

    public static JsonObject explicitPatch(String message) {
        String text = message == null ? "" : message.trim();
        JsonObject patch = new JsonObject();
        Matcher match = BUDGET.matcher(text);
        while (match.find()) if (!negated(text, match.start()))
            patch.addProperty("budget", number(match.group(1), 0, 1_000_000, "预算"));
        clearPatch(text, "取消预算限制|预算不限|不限制预算", "budget", patch);
        match = WALKING.matcher(text);
        while (match.find()) {
            if (negated(text, match.start())) continue;
            double meters = number(match.group(1), 0, 100_000, "步行上限") * ("米".equals(match.group(2)) ? 1 : 1000);
            if (meters > 100_000 || meters != Math.rint(meters)) throw new IllegalArgumentException("步行上限需为 0～100000 米的整数");
            patch.addProperty("maxWalkingMeters", (int) meters);
        }
        clearPatch(text, "取消步行上限|不限制步行距离|步行距离不限", "maxWalkingMeters", patch);
        match = DURATION.matcher(text);
        while (match.find()) {
            if (negated(text, match.start())) continue;
            double minutes = number(match.group(1), 0, 960, "每日时长") * ("小时".equals(match.group(2)) ? 60 : 1);
            if (minutes < 30 || minutes > 960 || minutes != Math.rint(minutes)) throw new IllegalArgumentException("每日时长需为 30～960 分钟");
            patch.addProperty("dailyMinutes", (int) minutes);
        }
        match = CITY.matcher(text);
        while (match.find()) if (!negated(text, match.start())) patch.addProperty("city", match.group(1));
        match = DATE.matcher(text);
        while (match.find()) {
            if (negated(text, match.start())) continue;
            try { patch.addProperty("date", LocalDate.of(Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))).toString()); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("出行日期无效，请确认年月日"); }
        }
        clockPatch(text, "(?:开始时间|每天开始|开始)\\s*" + CHANGE + "(\\d{1,2}:\\d{2})", "startTime", patch);
        clockPatch(text, "(?:结束时间|最晚结束|最晚结束时间)\\s*" + CHANGE + "(\\d{1,2}:\\d{2})", "finishByTime", patch);
        Matcher finish = Pattern.compile("(\\d{1,2}:\\d{2})\\s*前结束").matcher(text);
        while (finish.find()) putClock(finish.group(1), "finishByTime", patch);
        Matcher onlyTransit = Pattern.compile("只坐公交|只用公交|只接受公共交通").matcher(text);
        while (onlyTransit.find()) if (!negated(text, onlyTransit.start())) {
            JsonArray modes = new JsonArray(); modes.add("transit"); patch.add("modes", modes);
        }
        return patch;
    }

    public static JsonObject merge(JsonObject base, JsonObject additions) {
        JsonObject result = base == null ? new JsonObject() : base.deepCopy();
        if (additions != null) for (Map.Entry<String, JsonElement> field : additions.entrySet())
            result.add(field.getKey(), field.getValue().deepCopy());
        return result;
    }

    private static void applyPointChanges(String message, List<RouteNode> nodes, JsonObject patch, JsonObject updated) {
        if (message == null || nodes.isEmpty()) return;
        List<RouteNode> excluded = new ArrayList<>();
        for (String clause : message.split("[，。；,;!?！？]")) {
            if (clause.matches(".*(?:别|不要|不用|无需)(?:再)?(?:删|换|改|移除|取消).*")) continue;
            if (!clause.matches(".*(?:删掉|删除|移除|去掉|换掉|替换|不去|不要了).*|.*不要\\s*$")) continue;
            int index = referenceIndex(clause, nodes.size());
            if (index >= nodes.size()) throw new IllegalArgumentException("当前讨论方案只有 " + nodes.size() + " 个地点，请确认要修改哪一项");
            if (index >= 0 && index < nodes.size()) excluded.add(nodes.get(index));
        }
        if (excluded.isEmpty()) return;
        JsonArray avoid = array(updated, "avoid");
        JsonArray mustVisit = array(updated, "mustVisit");
        for (RouteNode node : excluded) {
            String id = node.getPoiId();
            if (!contains(avoid, id)) avoid.add(id);
        }
        JsonArray kept = new JsonArray();
        for (JsonElement item : mustVisit) {
            boolean removed = false;
            for (RouteNode node : excluded)
                if (item.getAsString().equals(node.getPoiId()) || item.getAsString().equals(node.getName())) removed = true;
            if (!removed) kept.add(item.deepCopy());
        }
        patch.add("avoid", avoid); updated.add("avoid", avoid.deepCopy());
        patch.add("mustVisit", kept); updated.add("mustVisit", kept.deepCopy());
    }

    /** Compact semantic memory, independent of UI copy and below the backend's 600-character turn cap. */
    public static String memorySummary(RouteAgent.Result result) {
        if (result == null) return "";
        StringBuilder text = new StringBuilder();
        if ("clarification".equals(result.kind)) {
            text.append("待用户确认：");
            if (result.questions != null) for (String question : result.questions) text.append(shorten(question, 120)).append("；");
        } else {
            text.append("已提供待确认候选方案（尚不代表已应用），地点按顺序：");
            if (result.nodes != null) for (int i = 0; i < result.nodes.size(); i++) {
                RouteNode node = result.nodes.get(i);
                text.append(i + 1).append('.').append(shorten(node.getName(), 18)).append('[')
                        .append(shorten(node.getPoiId(), 80)).append("]；");
                if (text.length() > 420) { text.append("其余见currentRoute"); break; }
            }
        }
        if (result.constraints != null) {
            for (String key : new String[]{"city", "date", "budget", "dailyMinutes", "finishByTime", "maxWalkingMeters"})
                if (result.constraints.has(key) && !result.constraints.get(key).isJsonNull())
                    text.append(key).append('=').append(result.constraints.get(key).getAsString()).append('；');
        }
        return shorten(text.toString(), 580);
    }

    /** Answer only factual questions whose answer is already present. Unknown intents still go to the backend. */
    public static String answerKnownQuestion(String message, List<RouteNode> nodes, JsonObject constraints) {
        if (message == null || MODIFICATION.matcher(message).find()) return null;
        if (message.matches(".*预算.*(?:多少|记得|是什么).*")) {
            JsonElement budget = constraints == null ? null : constraints.get("budget");
            return budget == null || budget.isJsonNull() ? "目前还没有设置每人预算上限。"
                    : "记得，目前每人预算上限是 " + budget.getAsString() + " 元；未核价费用仍需确认。";
        }
        if (!message.matches(".*(?:地址|在哪里|在哪儿|在哪|营业时间|开放时间|停留多久|参观多久|游览多久|叫什么|是哪一站).*")) return null;
        int index = referenceIndex(message, nodes.size());
        if (index < 0) return null;
        if (index >= nodes.size()) return "当前讨论的方案只有 " + nodes.size() + " 个地点，请确认你指的是哪一项。";
        RouteNode node = nodes.get(index);
        String prefix = "当前讨论方案的第 " + (index + 1) + " 个地点是「" + node.getName() + "」。";
        if (message.matches(".*(?:地址|在哪里|在哪儿|在哪).*")) {
            String address = RoutePlanText.withoutDirections(node.getAddress());
            return prefix + (blank(address) ? "当前数据未提供具体地址，需要核实后确认。" : "地址：" + address);
        }
        if (message.matches(".*(?:营业时间|开放时间).*")) {
            String hours = blank(node.getOpentimeToday()) ? node.getOpentimeWeek() : node.getOpentimeToday();
            return prefix + (blank(hours) ? "当前数据未核实开放时间，出行前需要确认。" : "当前资料中的开放时间：" + hours + "，出行日仍需复查。");
        }
        if (message.matches(".*(?:停留多久|参观多久|游览多久).*"))
            return prefix + "方案建议停留 " + node.getRecommendedDuration() + " 分钟。";
        return prefix;
    }

    private static int referenceIndex(String message, int size) {
        if (message.matches(".*最后(?:一个|一站|个|站).*")) return size == 0 ? -1 : size - 1;
        Matcher matcher = REFERENCE.matcher(message);
        if (!matcher.find()) return -1;
        int index = ordinal(matcher.group(1)) - 1;
        return matcher.find() ? -1 : index;
    }

    private static int ordinal(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        String digits = "零一二三四五六七八九";
        value = value.replace('两', '二');
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int high = ten == 0 ? 1 : digits.indexOf(value.charAt(0));
            int low = ten + 1 >= value.length() ? 0 : digits.indexOf(value.charAt(ten + 1));
            return high < 0 || low < 0 ? 0 : high * 10 + low;
        }
        return value.length() == 1 ? digits.indexOf(value.charAt(0)) : 0;
    }

    private static JsonArray array(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonArray() ? value.getAsJsonArray(key).deepCopy() : new JsonArray();
    }
    private static boolean contains(JsonArray values, String value) {
        for (JsonElement item : values) if (item.isJsonPrimitive() && item.getAsString().equals(value)) return true;
        return false;
    }
    private static void clockPatch(String text, String pattern, String key, JsonObject patch) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        while (matcher.find()) if (!negated(text, matcher.start())) putClock(matcher.group(1), key, patch);
    }
    private static void clearPatch(String text, String pattern, String key, JsonObject patch) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        while (matcher.find()) if (!negated(text, matcher.start())) patch.add(key, JsonNull.INSTANCE);
    }
    private static void putClock(String value, String key, JsonObject patch) {
        try { patch.addProperty(key, LocalTime.parse(value.length() == 4 ? "0" + value : value).toString()); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("行程时间无效，请使用 HH:mm 格式"); }
    }
    private static double number(String value, double min, double max, String label) {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number) || number < min || number > max)
            throw new IllegalArgumentException(label + "超出允许范围，请调整后重试");
        return number;
    }
    private static boolean negated(String text, int index) {
        return text.substring(Math.max(0, index - 10), index)
                .matches(".*(?:不要|别|不用|无需|不是|不想|不)(?:再|把|将|改成|改为|调整为)?\\s*$");
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String shorten(String value, int length) { return value == null ? "" : value.substring(0, Math.min(value.length(), length)); }
}
