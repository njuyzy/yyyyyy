package com.example.Japp.user.util;

import androidx.annotation.Nullable;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 领队放弃接单的时间校验。
 *
 * <p>业务规则：只能在项目开始时间至少 6 小时前放弃；恰好提前 6 小时允许，不足 6 小时禁止。
 * 客户端仅用于即时反馈，最终一致性由后端保证（见领队端需求规格 F-3.7-04）。</p>
 */
public final class AbandonDeadlineHelper {

    public static final long MIN_LEAD_TIME_MILLIS = 6L * 60L * 60L * 1000L;

    public static final String ALERT_TIME_INVALID = "订单时间异常，请联系客服处理";
    public static final String ALERT_TOO_LATE = "订单开始前 6 小时内不可放弃";

    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
    };
    private static final TimeZone BUSINESS_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    public enum Result { ALLOWED, TIME_INVALID, TOO_LATE }

    private AbandonDeadlineHelper() {}

    public static Result check(@Nullable String departureDate, @Nullable String departureTime) {
        return check(departureDate, departureTime, System.currentTimeMillis());
    }

    /** 可注入时钟的重载，便于单元测试边界。 */
    public static Result check(@Nullable String departureDate, @Nullable String departureTime,
                               long nowMillis) {
        Long start = parseStartTime(departureDate, departureTime);
        if (start == null) {
            return Result.TIME_INVALID;
        }
        return nowMillis <= start - MIN_LEAD_TIME_MILLIS ? Result.ALLOWED : Result.TOO_LATE;
    }

    @Nullable
    private static Long parseStartTime(@Nullable String departureDate, @Nullable String departureTime) {
        if (departureDate == null || departureTime == null) {
            return null;
        }
        String date = departureDate.trim();
        String time = departureTime.trim();
        if (date.isEmpty() || time.isEmpty()) {
            return null;
        }
        String combined = date + " " + time;
        for (String pattern : PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
            format.setLenient(false);
            format.setTimeZone(BUSINESS_TIME_ZONE);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(combined, position);
            // 必须完整匹配，避免合法时间后附带额外内容仍被当作有效时间。
            if (parsed != null && position.getErrorIndex() < 0
                    && position.getIndex() == combined.length()) {
                return parsed.getTime();
            }
        }
        return null;
    }
}
