package com.kael.punish.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([mhdwy])", Pattern.CASE_INSENSITIVE);
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private DurationParser() {
    }

    public static long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("空时长");
        }
        Matcher matcher = DURATION.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("时长格式错误");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("时长必须大于 0");
        }
        switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "m":
                return amount * MINUTE;
            case "h":
                return amount * HOUR;
            case "d":
                return amount * DAY;
            case "w":
                return amount * 7 * DAY;
            case "y":
                return amount * YEAR;
            default:
                throw new IllegalArgumentException("不支持的时长单位");
        }
    }

    public static String formatRemaining(long millis) {
        if (millis <= 0) {
            return "已到期";
        }
        long days = millis / DAY;
        double hours = (millis % DAY) / 3_600_000.0;
        String hourText = String.format(Locale.ROOT, "%.1f 小时", hours);
        return days > 0 ? days + " 天 " + hourText : hourText;
    }

}
