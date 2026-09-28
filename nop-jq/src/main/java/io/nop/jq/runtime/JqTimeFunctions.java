package io.nop.jq.runtime;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * jq time functions. jq represents a "broken down time" as
 * [year, month(0-11), day, hour, minute, second, weekday(0=Sunday), dayOfYear]
 * (struct tm semantics) and works in UTC for gmtime/strftime/mktime.
 */
public final class JqTimeFunctions {
    private static final String DATETIME_INPUTS_ERROR =
            "%s requires parsed datetime inputs";

    private JqTimeFunctions() {
    }

    /** epoch seconds -> broken-down time in UTC. */
    public static JqValue gmtime(double epochSeconds) {
        ZonedDateTime t = Instant.ofEpochMilli((long) Math.floor(epochSeconds * 1000))
                .atZone(ZoneOffset.UTC);
        return brokenDown(t);
    }

    /** epoch seconds -> broken-down time in the local zone. */
    public static JqValue localtime(double epochSeconds) {
        ZonedDateTime t = Instant.ofEpochMilli((long) Math.floor(epochSeconds * 1000))
                .atZone(java.time.ZoneId.systemDefault());
        return brokenDown(t);
    }

    private static JqValue brokenDown(ZonedDateTime t) {
        List<JqValue> parts = new ArrayList<>(8);
        parts.add(JqNumber.of(t.getYear()));
        parts.add(JqNumber.of(t.getMonthValue() - 1));
        parts.add(JqNumber.of(t.getDayOfMonth()));
        parts.add(JqNumber.of(t.getHour()));
        parts.add(JqNumber.of(t.getMinute()));
        // seconds as double keeps sub-second precision like jq
        parts.add(JqNumber.of(t.getSecond() + t.getNano() / 1e9));
        parts.add(JqNumber.of(t.getDayOfWeek().getValue() % 7)); // Monday=1..Sunday=7 -> tm_wday Sunday=0
        parts.add(JqNumber.of(t.getDayOfYear() - 1));
        return new JqArray(parts);
    }

    /** broken-down time -> epoch seconds. */
    public static double mktime(JqValue value) {
        List<JqValue> parts = requireBrokenDown("mktime", value);
        int year = ((JqNumber) parts.get(0)).intValue();
        int month = ((JqNumber) parts.get(1)).intValue() + 1;
        int day = ((JqNumber) parts.get(2)).intValue();
        int hour = ((JqNumber) parts.get(3)).intValue();
        int minute = ((JqNumber) parts.get(4)).intValue();
        int second = ((JqNumber) parts.get(5)).intValue();
        ZonedDateTime t = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC);
        return t.toEpochSecond();
    }

    public static JqValue strftime(String format, JqValue time, String funcName) {
        ZonedDateTime t = toZonedDateTime(funcName, time);
        return JqString.of(formatWith(format, t));
    }

    public static JqValue strptime(JqValue text, String format) {
        if (!(text instanceof JqString s)) {
            throw new JqRuntimeException("strptime requires a string");
        }
        String input = s.value();
        // Minimal strptime: support the common directives used in practice.
        int year = 1900, month = 1, day = 1, hour = 0, minute = 0, second = 0;
        int fi = 0, ii = 0;
        while (fi < format.length()) {
            char fc = format.charAt(fi);
            if (fc == '%') {
                if (fi + 1 >= format.length())
                    break;
                char directive = format.charAt(fi + 1);
                fi += 2;
                switch (directive) {
                    case 'Y' -> {
                        int[] r = scanDigits(input, ii, 4);
                        year = r[0];
                        ii = r[1];
                    }
                    case 'm' -> {
                        int[] r = scanDigits(input, ii, 2);
                        month = r[0];
                        ii = r[1];
                    }
                    case 'd' -> {
                        int[] r = scanDigits(input, ii, 2);
                        day = r[0];
                        ii = r[1];
                    }
                    case 'H' -> {
                        int[] r = scanDigits(input, ii, 2);
                        hour = r[0];
                        ii = r[1];
                    }
                    case 'M' -> {
                        int[] r = scanDigits(input, ii, 2);
                        minute = r[0];
                        ii = r[1];
                    }
                    case 'S' -> {
                        int[] r = scanDigits(input, ii, 2);
                        second = r[0];
                        ii = r[1];
                    }
                    case 'Z', 'z' -> {
                        // timezone: consume up to next non-letter sequence conservatively
                        while (ii < input.length() && Character.isLetter(input.charAt(ii)))
                            ii++;
                    }
                    case 'j', 'e', 'a', 'A', 'b', 'B', 'p', 'T' -> {
                        // scan a token
                        while (ii < input.length() && !Character.isDigit(input.charAt(ii)))
                            ii++;
                        if (directive == 'T') {
                            // skip HH:MM:SS via re-scan
                        }
                    }
                    case 'n', 't', ' ' -> {
                        while (ii < input.length() && Character.isWhitespace(input.charAt(ii)))
                            ii++;
                    }
                    case '%' -> {
                        if (ii < input.length() && input.charAt(ii) == '%')
                            ii++;
                    }
                    default -> {
                        // unknown directive: consume one char
                        if (ii < input.length())
                            ii++;
                    }
                }
            } else {
                if (ii < input.length() && input.charAt(ii) == fc)
                    ii++;
                fi++;
            }
        }
        List<JqValue> parts = new ArrayList<>(8);
        parts.add(JqNumber.of(year));
        parts.add(JqNumber.of(month - 1));
        parts.add(JqNumber.of(day));
        parts.add(JqNumber.of(hour));
        parts.add(JqNumber.of(minute));
        parts.add(JqNumber.of(second));
        parts.add(JqNumber.of(0));
        parts.add(JqNumber.of(0));
        return new JqArray(parts);
    }

    private static int[] scanDigits(String input, int from, int max) {
        int end = from;
        while (end < input.length() && Character.isDigit(input.charAt(end))
                && end - from < max) {
            end++;
        }
        return new int[]{Integer.parseInt(input.substring(from, end)), end};
    }

    private static ZonedDateTime toZonedDateTime(String funcName, JqValue time) {
        if (time instanceof JqNumber num) {
            return Instant.ofEpochMilli((long) Math.floor(num.doubleValue() * 1000))
                    .atZone(ZoneOffset.UTC);
        }
        if (time instanceof JqArray) {
            List<JqValue> parts = requireBrokenDown(funcName, time);
            ZonedDateTime t = ZonedDateTime.of(
                    ((JqNumber) parts.get(0)).intValue(),
                    ((JqNumber) parts.get(1)).intValue() + 1,
                    ((JqNumber) parts.get(2)).intValue(),
                    ((JqNumber) parts.get(3)).intValue(),
                    ((JqNumber) parts.get(4)).intValue(),
                    ((JqNumber) parts.get(5)).intValue(), 0, ZoneOffset.UTC);
            return t;
        }
        throw new JqRuntimeException(String.format(DATETIME_INPUTS_ERROR, funcName));
    }

    private static List<JqValue> requireBrokenDown(String funcName, JqValue value) {
        if (!(value instanceof JqArray arr) || arr.size() < 6) {
            throw new JqRuntimeException(String.format(DATETIME_INPUTS_ERROR, funcName));
        }
        for (JqValue part : arr.items()) {
            if (!(part instanceof JqNumber)) {
                throw new JqRuntimeException(String.format(DATETIME_INPUTS_ERROR, funcName));
            }
        }
        return arr.items();
    }

    private static String formatWith(String format, ZonedDateTime t) {
        StringBuilder sb = new StringBuilder();
        Locale locale = Locale.ROOT;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c != '%' || i + 1 >= format.length()) {
                sb.append(c);
                continue;
            }
            char directive = format.charAt(++i);
            switch (directive) {
                case 'Y' -> sb.append(String.format(locale, "%04d", t.getYear()));
                case 'm' -> sb.append(String.format(locale, "%02d", t.getMonthValue()));
                case 'd' -> sb.append(String.format(locale, "%02d", t.getDayOfMonth()));
                case 'e' -> sb.append(String.format(locale, "%2d", t.getDayOfMonth()));
                case 'H' -> sb.append(String.format(locale, "%02d", t.getHour()));
                case 'M' -> sb.append(String.format(locale, "%02d", t.getMinute()));
                case 'S' -> sb.append(String.format(locale, "%02d", t.getSecond()));
                case 'T' -> sb.append(String.format(locale, "%02d:%02d:%02d",
                        t.getHour(), t.getMinute(), t.getSecond()));
                case 'j' -> sb.append(String.format(locale, "%03d", t.getDayOfYear()));
                case 'a' -> sb.append(t.getDayOfWeek().toString().substring(0, 3));
                case 'A' -> sb.append(fullWeekday(t));
                case 'b' -> sb.append(t.getMonth().toString().substring(0, 3));
                case 'B' -> sb.append(fullMonth(t));
                case 'Z' -> sb.append(t.getZone().getId());
                case 'z' -> sb.append(t.getOffset().getId().equals("Z") ? "+0000"
                        : t.getOffset().getId().replace(":", ""));
                case 's' -> sb.append(t.toEpochSecond());
                case 'u' -> sb.append(t.getDayOfWeek().getValue());
                case 'w' -> sb.append((t.getDayOfWeek().getValue() + 1) % 7);
                case '%' -> sb.append('%');
                default -> {
                    sb.append('%').append(directive);
                }
            }
        }
        return sb.toString();
    }

    private static final String[] WEEKDAYS = {"Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"};
    private static final String[] MONTHS = {"January", "February", "March", "April", "May",
            "June", "July", "August", "September", "October", "November", "December"};

    private static String fullWeekday(ZonedDateTime t) {
        return WEEKDAYS[t.getDayOfWeek().getValue() % 7];
    }

    private static String fullMonth(ZonedDateTime t) {
        return MONTHS[t.getMonthValue() - 1];
    }

    /** todate: ISO8601 in UTC from epoch seconds. */
    public static JqValue todate(double epochSeconds) {
        return todateIso(epochSeconds);
    }

    /** ISO instant formatting used by todate/strftime default. */
    public static JqValue todateIso(double epochSeconds) {
        ZonedDateTime t = Instant.ofEpochMilli((long) Math.floor(epochSeconds * 1000))
                .atZone(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:%02dZ",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond()));
        return JqString.of(sb.toString());
    }

    /** fromdate ISO8601 -> epoch seconds. */
    public static double fromdate(String text) {
        try {
            String normalized = text.trim();
            if (normalized.endsWith("Z"))
                normalized = normalized.substring(0, normalized.length() - 1) + "+00:00";
            return java.time.OffsetDateTime.parse(normalized).toEpochSecond();
        } catch (Exception e) {
            throw new JqRuntimeException("date \"" + text + "\" does not match format \""
                    + "%Y-%m-%dT%H:%M:%SZ" + "\"");
        }
    }
}
