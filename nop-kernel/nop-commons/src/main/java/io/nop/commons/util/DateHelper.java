package io.nop.commons.util;

// copy getDayStart methods from Apache Kylin project

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiStringHelper;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 获得本月的天数 localDate.lengthOfMonth() 获得本年的天数 localDate.lengthOfYear() 判断是否闰年 localDate.isLeapYear()
 */
@io.nop.api.core.annotations.core.Locale("zh-CN")
@Description("日期和时间相关的帮助函数")
public class DateHelper {
    private static TimeZone gmt = TimeZone.getTimeZone("GMT");

    public static final LocalDate INVALID_DATE = ApiStringHelper.INVALID_DATE;

    public static final String PATTERN_MS = "ms";

    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmssSSSS");
    public static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static Map<String, DateTimeFormatter> s_formatters = new HashMap<>();

    static {
        registerFormatter("yyyy-MM-dd HH:mm:ss", DATETIME_FORMATTER);
        registerFormatter("yyyy-MM-dd", DATE_FORMATTER);
        registerFormatter("HH:mm:ss", TIME_FORMATTER);
        registerFormatter("yyMMddHHmmssSSSS", COMPACT_DATE_TIME_FORMATTER);
        registerFormatter("yyyyMMdd", COMPACT_DATE_FORMATTER);
        registerFormatter("ISO_DATE", DateTimeFormatter.ISO_DATE);
        registerFormatter("ISO_DATE_TIME", DateTimeFormatter.ISO_DATE_TIME);
        registerFormatter("ISO_LOCAL_DATE", DateTimeFormatter.ISO_LOCAL_DATE);
        registerFormatter("IOS_LOCAL_DATE_TIME", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        registerFormatter("ISO_TIME", DateTimeFormatter.ISO_TIME);
        registerFormatter("ISO_WEEK_DATE", DateTimeFormatter.ISO_WEEK_DATE);
        registerFormatter("RFC_1123_DATE_TIME", DateTimeFormatter.RFC_1123_DATE_TIME);
        registerFormatter("ISO_INSTANT", DateTimeFormatter.ISO_INSTANT);
    }

    public static void registerFormatter(String pattern, DateTimeFormatter formatter) {
        s_formatters.put(pattern, formatter);
    }

    public static final long ONE_MINUTE_TS = 60 * 1000L;
    public static final long ONE_HOUR_TS = 60 * ONE_MINUTE_TS;
    public static final long ONE_DAY_TS = 24 * ONE_HOUR_TS;

    public static long getMinuteStartTs(long ts) {
        return ts / ONE_MINUTE_TS * ONE_MINUTE_TS;
    }

    public static long getHourStartTs(long ts) {
        return ts / ONE_HOUR_TS * ONE_HOUR_TS;
    }

    public static long getDayStartTs(long ts) {
        return getDayStartWithTimeZoneTs(gmt, ts);
    }

    public static long getDayStartWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int date = calendar.get(Calendar.DATE);
        calendar.clear();
        calendar.set(year, month, date);
        return calendar.getTimeInMillis();
    }

    public static LocalDateTime currentDateTime() {
        return CoreMetrics.currentDateTime();
    }

    public static LocalDate currentDate() {
        return CoreMetrics.today();
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long getWeekStartTs(long ts) {
        return getWeekStartWithTimeZoneTs(gmt, ts);
    }

    public static long getWeekStartWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getDayStartWithTimeZoneTs(timeZone, ts));
        calendar.add(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek() - calendar.get(Calendar.DAY_OF_WEEK));
        return calendar.getTimeInMillis();
    }

    public static long getMonthStartTs(long ts) {
        return getMonthStartWithTimeZoneTs(gmt, ts);
    }

    public static long getMonthStartWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        calendar.clear();
        calendar.set(year, month, 1);
        return calendar.getTimeInMillis();
    }

    public static long getQuarterStartTs(long ts) {
        return getQuarterStartWithTimeZoneTs(gmt, ts);
    }

    public static long getQuarterStartWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        calendar.clear();
        calendar.set(year, month / 3 * 3, 1);
        return calendar.getTimeInMillis();
    }

    public static long getYearStartTs(long ts) {
        return getYearStartWithTimeZoneTs(gmt, ts);
    }

    public static long getYearStartWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        calendar.clear();
        calendar.set(year, 0, 1);
        return calendar.getTimeInMillis();
    }

    public static long getWeekEndTs(long ts) {
        return getWeekEndWithTimeZoneTs(gmt, ts);
    }

    public static long getWeekEndWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getWeekStartWithTimeZoneTs(timeZone, ts));
        calendar.add(Calendar.DAY_OF_WEEK, 7);
        return calendar.getTimeInMillis();
    }

    public static long getMonthEndTs(long ts) {
        return getMonthEndWithTimeZoneTs(gmt, ts);
    }

    public static long getMonthEndWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getDayStartWithTimeZoneTs(timeZone, ts));
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 24); //NOSONAR
        return calendar.getTimeInMillis();
    }

    public static long getQuarterEndTs(long ts) {
        return getQuarterEndWithTimeZoneTs(gmt, ts);
    }

    public static long getQuarterEndWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getQuarterStartWithTimeZoneTs(timeZone, ts));
        calendar.add(Calendar.MONTH, 3);
        return calendar.getTimeInMillis();
    }

    public static long getYearEndTs(long ts) {
        return getYearEndWithTimeZoneTs(gmt, ts);
    }

    public static long getYearEndWithTimeZoneTs(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getYearStartWithTimeZoneTs(timeZone, ts));
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTimeInMillis();
    }

    public static String formatJavaDate(Date date, String pattern) {
        if (date == null)
            return null;
        if (PATTERN_MS.equals(pattern))
            return Long.toString(date.getTime());

        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(ConvertHelper.toLocalDateTime(date, NopException::new));
    }

    public static String formatDate(LocalDate date, String pattern) {
        if (date == null)
            return null;

        if (PATTERN_MS.equals(pattern))
            return Long.toString(ConvertHelper.localDateToMillis(date));

        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(date);
    }

    public static String formatTime(LocalTime time, String pattern) {
        if (time == null)
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(time);
    }

    public static String formatDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null)
            return null;
        if (PATTERN_MS.equals(pattern))
            return Long.toString(ConvertHelper.localDateTimeToMillis(dateTime));
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(dateTime);
    }

    public static String formatTimestamp(Timestamp date, String pattern) {
        if (date == null)
            return null;
        if (PATTERN_MS.equals(pattern))
            return Long.toString(date.getTime());

        return new SimpleDateFormat(pattern).format(date);
    }

    public static String formatTimestampNoMillis(Timestamp date) {
        if (date == null)
            return null;
        String str = date.toString();
        return str.substring(0, 19);
    }

    static DateTimeFormatter buildFormatter(String pattern) {
        DateTimeFormatter formatter = s_formatters.get(pattern);
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter;
    }

    public static LocalDate parseDate(String s) {
        return ConvertHelper.stringToLocalDate(s, NopException::new);
    }

    public static LocalDateTime parseDataTime(String s) {
        return ConvertHelper.stringToLocalDateTime(s, NopException::new);
    }

    public static LocalTime parseTime(String s) {
        return ConvertHelper.toLocalTime(s, NopException::new);
    }

    public static LocalDate parseDate(String s, String pattern) {
        if (StringHelper.isEmpty(s))
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return LocalDate.parse(s, formatter);
    }

    public static LocalDate safeParseDate(String s, String ...patterns) {
        if (StringHelper.isEmpty(s))
            return null;
        for(String pattern : patterns) {
            try {
                DateTimeFormatter formatter = buildFormatter(pattern);
                return LocalDate.parse(s, formatter);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    public static LocalDateTime parseDateTime(String s, String pattern) {
        if (StringHelper.isEmpty(s))
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return LocalDateTime.parse(s, formatter);
    }

    public static Duration parseDuration(String s) {
        return ConvertHelper.stringToDuration(s, NopException::new);
    }

    public static LocalDate firstDayOfMonth(LocalDate date) {
        return LocalDate.of(date.getYear(), date.getMonth(), 1);
    }

    public static LocalDate firstDayOfNextMonth(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfNextMonth());
    }

    public static LocalDate lastDayOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }

    public static LocalDate firstDayOfYear(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfYear());
    }

    public static LocalDate firstDayOfNextYear(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfNextYear());
    }

    public static LocalDate lastDayOfYear(LocalDate date) {
        return date.with(TemporalAdjusters.lastDayOfYear());
    }

    public static LocalDate firstInMonth(LocalDate date, int dayOfWeek) {
        if (dayOfWeek <= 0 || dayOfWeek > 7)
            throw new IllegalArgumentException("invalid dayOfWeek:" + dayOfWeek);

        return date.with(TemporalAdjusters.firstInMonth(toDayOfWeek(dayOfWeek)));
    }

    public static LocalDateTime millisToDateTime(Long timestamp) {
        return ConvertHelper.millisToLocalDateTime(timestamp);
    }

    public static LocalDate millisToDate(Long timestamp) {
        return ConvertHelper.millisToLocalDate(timestamp);
    }

    public static Long dateToMillis(LocalDate day) {
        return ConvertHelper.localDateToMillis(day);
    }

    public static long dateMillis(int year, int month, int day) {
        return dateToMillis(LocalDate.of(year, month, day));
    }

    public static Long dateTimeToMillis(LocalDateTime localDateTime) {
        return ConvertHelper.localDateTimeToMillis(localDateTime);
    }

    public static Timestamp dateTimeToTimestamp(LocalDateTime localDateTime) {
        return ConvertHelper.localDateTimeToTimestamp(localDateTime);
    }

    public static Long dateDiff(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null)
            return null;

        return d1.toEpochDay() - d2.toEpochDay();
    }

    public static Integer yearDiff(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null)
            return null;
        return d1.getYear() - d2.getYear();
    }

    /**
     * 返回 d1 - d2
     */
    public static Integer monthDiff(LocalDate d1, LocalDate d2) {
        if (d1 == null || d2 == null)
            return null;
        Period period = Period.between(d2.withDayOfMonth(1), d1.withDayOfMonth(1));
        int monthDifference = period.getYears() * 12 + period.getMonths();
        return monthDifference;
    }


    private static DayOfWeek toDayOfWeek(int dayOfWeek) {
        return DayOfWeek.values()[dayOfWeek - 1];
    }

    public static LocalDate toDayOfWeek(LocalDate date, int dayOfWeek) {
        if (dayOfWeek <= 0 || dayOfWeek > 7)
            throw new IllegalArgumentException("invalid dayOfWeek:" + dayOfWeek);

        return date.plusDays(-date.getDayOfWeek().getValue() + (long) dayOfWeek);
    }

    public static LocalDateTime toZone(LocalDateTime dateTime, ZoneId zone) {
        ZonedDateTime zt = ZonedDateTime.of(dateTime, ZoneId.systemDefault());
        return zt.withZoneSameInstant(zone).toLocalDateTime();
    }

    public static LocalDateTime toUTC(LocalDateTime dateTime) {
        return toZone(dateTime, ZoneOffset.UTC);
    }

    public static LocalDateTime fromUTC(LocalDateTime dateTime) {
        ZonedDateTime zt = ZonedDateTime.of(dateTime, ZoneOffset.UTC);
        return zt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static ZoneId zone(int hours, int minutes) {
        return ZoneOffset.ofHoursMinutes(hours, minutes);
    }

    public static boolean isMonthDay(@Name("date") LocalDate date, @Name("monthDay") MonthDay monthDay) {
        if (date == null)
            return false;

        return date.getMonthValue() == monthDay.getMonthValue() && date.getDayOfMonth() == monthDay.getDayOfMonth();
    }

    // static long toMillis(String s, long factor) {
    // if (s.indexOf('.') < 0)
    // return ConvertHelper.stringToLong(s) * factor;
    // return (long) (ConvertHelper.stringToDouble(s) * factor);
    // }
}
