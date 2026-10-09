package com.example.Japp.user.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class AbandonDeadlineHelperTest {

    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String DATE = "2026-06-01";
    private static final String TIME = "10:00";

    private long startMillis() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(TZ);
        Date date = format.parse(DATE + " " + TIME);
        return date.getTime();
    }

    @Test
    public void exactlySixHoursBeforeIsAllowed() throws Exception {
        assertEquals(AbandonDeadlineHelper.Result.ALLOWED,
                AbandonDeadlineHelper.check(DATE, TIME,
                        startMillis() - AbandonDeadlineHelper.MIN_LEAD_TIME_MILLIS));
    }

    @Test
    public void backendTimeWithSecondsExactlySixHoursBeforeIsAllowed() throws Exception {
        assertEquals(AbandonDeadlineHelper.Result.ALLOWED,
                AbandonDeadlineHelper.check(DATE, "10:00:00",
                        startMillis() - AbandonDeadlineHelper.MIN_LEAD_TIME_MILLIS));
    }

    @Test
    public void earlierThanSixHoursIsAllowed() throws Exception {
        assertEquals(AbandonDeadlineHelper.Result.ALLOWED,
                AbandonDeadlineHelper.check(DATE, TIME,
                        startMillis() - AbandonDeadlineHelper.MIN_LEAD_TIME_MILLIS - 1000L));
    }

    @Test
    public void justUnderSixHoursIsTooLate() throws Exception {
        assertEquals(AbandonDeadlineHelper.Result.TOO_LATE,
                AbandonDeadlineHelper.check(DATE, TIME,
                        startMillis() - AbandonDeadlineHelper.MIN_LEAD_TIME_MILLIS + 1000L));
    }

    @Test
    public void afterStartIsTooLate() throws Exception {
        assertEquals(AbandonDeadlineHelper.Result.TOO_LATE,
                AbandonDeadlineHelper.check(DATE, TIME, startMillis() + 60_000L));
    }

    @Test
    public void missingOrMalformedTimeIsInvalid() {
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check(null, TIME, 0L));
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check(DATE, null, 0L));
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check("", "", 0L));
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check("2026/06/01", TIME, 0L));
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check(DATE, "24:00", 0L));
        assertEquals(AbandonDeadlineHelper.Result.TIME_INVALID,
                AbandonDeadlineHelper.check("2026-02-30", TIME, 0L));
    }
}
