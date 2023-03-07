package io.nop.commons.util;

// copy getDayStart methods from Apache Kylin project

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
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

    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmssSSSS");
    public static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

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

    public static long getMinuteStart(long ts) {
        return ts / ONE_MINUTE_TS * ONE_MINUTE_TS;
    }

    public static long getHourStart(long ts) {
        return ts / ONE_HOUR_TS * ONE_HOUR_TS;
    }

    public static long getDayStart(long ts) {
        return getDayStartWithTimeZone(gmt, ts);
    }

    public static long getDayStartWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int date = calendar.get(Calendar.DATE);
        calendar.clear();
        calendar.set(year, month, date);
        return calendar.getTimeInMillis();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate currentLocalDate() {
        return LocalDate.now();
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long getWeekStart(long ts) {
        return getWeekStartWithTimeZone(gmt, ts);
    }

    public static long getWeekStartWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getDayStartWithTimeZone(timeZone, ts));
        calendar.add(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek() - calendar.get(Calendar.DAY_OF_WEEK));
        return calendar.getTimeInMillis();
    }

    public static long getMonthStart(long ts) {
        return getMonthStartWithTimeZone(gmt, ts);
    }

    public static long getMonthStartWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        calendar.clear();
        calendar.set(year, month, 1);
        return calendar.getTimeInMillis();
    }

    public static long getQuarterStart(long ts) {
        return getQuarterStartWithTimeZone(gmt, ts);
    }

    public static long getQuarterStartWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        calendar.clear();
        calendar.set(year, month / 3 * 3, 1);
        return calendar.getTimeInMillis();
    }

    public static long getYearStart(long ts) {
        return getYearStartWithTimeZone(gmt, ts);
    }

    public static long getYearStartWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        int year = calendar.get(Calendar.YEAR);
        calendar.clear();
        calendar.set(year, 0, 1);
        return calendar.getTimeInMillis();
    }

    public static long getWeekEnd(long ts) {
        return getWeekEndWithTimeZone(gmt, ts);
    }

    public static long getWeekEndWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getWeekStartWithTimeZone(timeZone, ts));
        calendar.add(Calendar.DAY_OF_WEEK, 7);
        return calendar.getTimeInMillis();
    }

    public static long getMonthEnd(long ts) {
        return getMonthEndWithTimeZone(gmt, ts);
    }

    public static long getMonthEndWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(ts);
        calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONDAY), calendar.get(Calendar.DAY_OF_MONTH), 0,
                0, 0);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 24);
        return calendar.getTimeInMillis();
    }

    public static long getQuarterEnd(long ts) {
        return getQuarterEndWithTimeZone(gmt, ts);
    }

    public static long getQuarterEndWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getQuarterStartWithTimeZone(timeZone, ts));
        calendar.add(Calendar.MONTH, 3);
        return calendar.getTimeInMillis();
    }

    public static long getYearEnd(long ts) {
        return getYearEndWithTimeZone(gmt, ts);
    }

    public static long getYearEndWithTimeZone(TimeZone timeZone, long ts) {
        Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
        calendar.setTimeInMillis(getYearStartWithTimeZone(timeZone, ts));
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTimeInMillis();
    }

    public static String formatDate(Date date, String pattern) {
        if (date == null)
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(ConvertHelper.toLocalDateTime(date, NopException::new));
    }

    public static String formatLocalDate(LocalDate date, String pattern) {
        if (date == null)
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(date);
    }

    public static String formatLocalTime(LocalTime time, String pattern) {
        if (time == null)
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(time);
    }

    public static String formatLocalDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null)
            return null;
        DateTimeFormatter formatter = buildFormatter(pattern);
        return formatter.format(dateTime);
    }

    public static String formatTimestamp(Timestamp date, String pattern) {
        if (date == null)
            return null;
        return new SimpleDateFormat(pattern).format(date);
    }

    static DateTimeFormatter buildFormatter(String pattern) {
        DateTimeFormatter formatter = s_formatters.get(pattern);
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter;
    }

    public static LocalDate parseLocalDate(String s) {
        return ConvertHelper.stringToLocalDate(s, NopException::new);
    }

    public static LocalDateTime parseLocalDataTime(String s) {
        return ConvertHelper.stringToLocalDateTime(s, NopException::new);
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

    public static LocalDateTime millisToLocalDateTime(Long timestamp) {
        return ConvertHelper.millisToLocalDateTime(timestamp);
    }

    public static LocalDate millisToLocalDate(Long timestamp) {
        return ConvertHelper.millisToLocalDate(timestamp);
    }

    public static Long localDateToMillis(LocalDate day) {
        return ConvertHelper.localDateToMillis(day);
    }

    public static long localDateMillis(int year, int month, int day) {
        return localDateToMillis(LocalDate.of(year, month, day));
    }

    public static Long localDateTimeToMillis(LocalDateTime localDateTime) {
        return ConvertHelper.localDateTimeToMillis(localDateTime);
    }

    public static Timestamp localDateTimeToTimestamp(LocalDateTime localDateTime) {
        return ConvertHelper.localDateTimeToTimestamp(localDateTime);
    }

    public static long diffDays(LocalDate d1, LocalDate d2) {
        return d1.toEpochDay() - d2.toEpochDay();
    }

    private static DayOfWeek toDayOfWeek(int dayOfWeek) {
        return DayOfWeek.values()[dayOfWeek - 1];
    }

    public static LocalDate toDayOfWeek(LocalDate date, int dayOfWeek) {
        if (dayOfWeek <= 0 || dayOfWeek > 7)
            throw new IllegalArgumentException("invalid dayOfWeek:" + dayOfWeek);

        return date.plusDays(-date.getDayOfWeek().getValue() + dayOfWeek);
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