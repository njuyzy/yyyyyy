package com.example.Japp.user.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Display-only policy: keep itinerary information, not turn-by-turn travel instructions. */
public final class RoutePlanText {
    private static final Pattern DIRECTIONS_HEADING = Pattern.compile(
            "^(?:#{1,6}\\s*)?(?:交通安排|交通路线|具体走法|具体路线走法|导航步骤|导航指引|路线指引|步行参考|Directions|Navigation)\\s*[：:]?.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RETAINED_HEADING = Pattern.compile(
            "^(?:#{1,6}\\s*)?(?:已知.*费用|每人.*费用|未核价|门票|方案假设|规划假设|出行提醒|出发前确认|注意事项|确认后|本方案将移除|第\\s*\\d+\\s*天[：:（(]).*"
    );
    private static final Pattern DIRECTIONS = Pattern.compile(
            "(?:从这里|从此处).*(?:步行|乘坐|搭乘|驾车|前往|下一个|下一站)"
                    + "|(?:步行|骑行|驾车|开车|打车).*(?:前往|到达|抵达|下一个|下一站|走到|经过|至|到)"
                    + "|(?:乘坐|搭乘|换乘|转乘|坐地铁|坐公交|乘地铁|乘公交).*(?:地铁|公交|巴士|号线|路|站|到|至)"
                    + "|(?:向|往|朝)[东西南北左右前后].*(?:走|行|转|米|公里)"
                    + "|(?:左转|右转|掉头|直行|下车后|出站后|沿.{1,30}(?:步行|前行|行驶)|在.{1,30}(?:下车|上车|换乘))"
                    + "|(?:公交站|地铁站|车站).{0,20}(?:下车|上车|出站)"
                    + "|(?<!通勤)(?:步行|骑行|驾车)约?\\s*\\d+\\s*(?:分钟|米|公里)"
                    + "|\\b(?:turn (?:left|right)|walk (?:to|along|for|towards?)|take (?:the bus|bus|the metro|the subway)|transfer to|head (?:north|south|east|west))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADVISORY = Pattern.compile(
            "估算|参考|未核实|待核实|不保证|无法保证|未知|不能|不可|不要|禁止|封闭|关闭|施工|预约|门票|超出|超过|上限|风险|注意|警示|慎行|不建议|安全");

    private RoutePlanText() {}

    public static String withoutDirections(String value) {
        if (value == null || value.isEmpty()) return "";
        // POI addresses sometimes append walking directions in parentheses; keep the actual street address.
        Matcher parenthetical = Pattern.compile("[（(]([^（）()\\r\\n]*)[）)]").matcher(value);
        StringBuffer cleaned = new StringBuffer();
        while (parenthetical.find()) {
            String inside = parenthetical.group(1);
            boolean navigation = DIRECTIONS.matcher(inside).find() && !ADVISORY.matcher(inside).find();
            parenthetical.appendReplacement(cleaned, Matcher.quoteReplacement(navigation ? "" : parenthetical.group()));
        }
        parenthetical.appendTail(cleaned);
        value = cleaned.toString();
        StringBuilder result = new StringBuilder();
        boolean directionsSection = false;
        for (String line : value.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (DIRECTIONS_HEADING.matcher(trimmed).matches()) {
                directionsSection = true;
                continue;
            }
            if (directionsSection) {
                if (trimmed.isEmpty() || RETAINED_HEADING.matcher(trimmed).matches()) directionsSection = false;
                else continue;
            }
            StringBuilder kept = new StringBuilder();
            for (String sentence : line.split("(?<=[。！？!?；;，,])")) {
                if (!DIRECTIONS.matcher(sentence).find() || ADVISORY.matcher(sentence).find()) kept.append(sentence);
            }
            String visible = kept.toString().trim();
            if (!visible.isEmpty()) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') result.append('\n');
                result.append(visible);
            } else if (trimmed.isEmpty() && result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                result.append("\n\n");
            }
        }
        return result.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    public static String advisory(String value) {
        return value != null && ADVISORY.matcher(value).find() ? withoutDirections(value) : "";
    }
}
