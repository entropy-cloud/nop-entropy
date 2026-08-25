/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static io.nop.api.core.ApiErrors.ERR_CONVERT_TO_TYPE_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConvertHelper {
    @Test
    public void testTimestamp() {
        long millis = System.currentTimeMillis();
        Timestamp ts = ConvertHelper.convertTo(Timestamp.class, millis, NopException::new);
        System.out.println(ts);
        System.out.println("instant=" + Instant.now());
        assertEquals(millis, ts.getTime());

        Long value = ts.getTime();
        LocalDateTime dt = ConvertHelper.convertTo(LocalDateTime.class, ts, NopException::new);
        System.out.println(dt);
        Assertions.assertEquals(dt, ConvertHelper.convertTo(LocalDateTime.class, dt.toString(), NopException::new));

        Assertions.assertEquals(dt, ConvertHelper.convertTo(LocalDateTime.class, ts.toString(), NopException::new));
        System.out.println(new Timestamp(ConvertHelper.localDateTimeToMillis(dt)));

        assertEquals(value, ConvertHelper.localDateTimeToMillis(dt));
        Assertions.assertEquals(value, ConvertHelper.convertTo(Long.class, ts, NopException::new));

        LocalDate date = ConvertHelper.convertTo(LocalDate.class, ts, NopException::new);
        Assertions.assertEquals(date, ConvertHelper.convertTo(LocalDate.class, ts.toString().substring(0, 10), NopException::new));

        dt = ConvertHelper.convertTo(LocalDateTime.class, "2020-11-13 08:22", NopException::new);
        System.out.println(dt);

        Assertions.assertEquals(dt, ConvertHelper.convertTo(LocalDateTime.class, dt.toString(), NopException::new));
    }

    @Test
    public void testConvertObject() {
        Assertions.assertEquals(this, ConvertHelper.convertTo(TestConvertHelper.class, this, NopException::new));
    }

    @Test
    public void testNumber() {
        double d = 3.2;
        Assertions.assertEquals(3, ConvertHelper.convertTo(Integer.class, d, NopException::new));
        Assertions.assertEquals(3.0, ConvertHelper.convertTo(Double.class, 3.0, NopException::new));
        Assertions.assertEquals(3L, ConvertHelper.convertTo(Long.class, d, NopException::new));
        Assertions.assertEquals((short) 3, ConvertHelper.convertTo(Short.class, d, NopException::new));
        Assertions.assertEquals(3.2f, ConvertHelper.convertTo(Float.class, d, NopException::new));
        Assertions.assertEquals(new BigDecimal("3.2"), ConvertHelper.convertTo(BigDecimal.class, d, NopException::new));
        Assertions.assertEquals(true, ConvertHelper.convertTo(Boolean.class, 3, NopException::new));
    }

    @Test
    public void testCsvSet() {
        Set<String> set = ConvertHelper.toCsvSet(",a,b ,c ,", NopException::new);
        assertEquals(Arrays.asList("a", "b", "c"), new ArrayList<>(set));
    }

    /**
     * 回归："G"/"M"/"K"这类纯单位后缀输入必须得到带错误码的转换失败，而不是裸NPE。
     */
    @Test
    public void testStringToLongUnitSuffixOnly() {
        for (String input : new String[]{"G", "M", "K"}) {
            NopException ex = assertThrows(NopException.class,
                    () -> ConvertHelper.stringToLong(input, NopException::new),
                    "input '" + input + "' must fail with NopException");
            assertEquals(ERR_CONVERT_TO_TYPE_FAIL.getErrorCode(), ex.getErrorCode());
        }

        assertEquals(1024L, ConvertHelper.stringToLong("1K", NopException::new));
        assertEquals(2L * 1024 * 1024, ConvertHelper.stringToLong("2M", NopException::new));
        assertEquals(1024L * 1024 * 1024, ConvertHelper.stringToLong("1G", NopException::new));
        assertEquals(1536L, ConvertHelper.stringToLong("1.5K", NopException::new));
    }

    /**
     * 回归：e/E并存的畸形科学计数法输入必须得到带错误码的转换失败，
     * 而不是StringIndexOutOfBoundsException或未捕获的NumberFormatException。
     */
    @Test
    public void testStringToNumberMalformedExponent() {
        for (String input : new String[]{"1.2e3E4", "1e1E1", "1E1e1"}) {
            NopException ex = assertThrows(NopException.class,
                    () -> ConvertHelper.stringToNumber(input, NopException::new),
                    "input '" + input + "' must fail with NopException");
            assertEquals(ERR_CONVERT_TO_TYPE_FAIL.getErrorCode(), ex.getErrorCode());
        }

        // 正常科学计数法不受影响
        assertEquals(1200.0, ConvertHelper.stringToNumber("1.2e3", NopException::new));
        assertEquals(1200.0, ConvertHelper.stringToNumber("1.2E3", NopException::new));
    }

    /**
     * 回归：month-day字符串必须先校验数字组成再做范围比较，
     * "12-0a"不能抛裸NumberFormatException，非补零的"2-15"应能正常解析。
     */
    @Test
    public void testStringToMonthDay() {
        assertEquals(MonthDay.of(12, 31), ConvertHelper.stringToMonthDay("12-31", NopException::new));
        // 非补零形式字典序会误拒，按数值比较应接受
        assertEquals(MonthDay.of(2, 15), ConvertHelper.stringToMonthDay("2-15", NopException::new));

        for (String input : new String[]{"12-0a", "12-1x", "0a-12", "13-01", "00-01", "01-00", "01-32"}) {
            NopException ex = assertThrows(NopException.class,
                    () -> ConvertHelper.stringToMonthDay(input, NopException::new),
                    "input '" + input + "' must fail with NopException");
            assertEquals(ERR_CONVERT_TO_TYPE_FAIL.getErrorCode(), ex.getErrorCode());
        }
    }

    @Test
    public void testPrimitive() {
        Assertions.assertEquals(0, ConvertHelper.convertTo(int.class, null, NopException::new));
        Assertions.assertEquals(false, ConvertHelper.convertTo(boolean.class, null, NopException::new));
        Assertions.assertEquals(0L, ConvertHelper.convertTo(long.class, null, NopException::new));

        Assertions.assertEquals(true, ConvertHelper.convertTo(boolean.class, true, NopException::new));

        Assertions.assertEquals(3.0, ConvertHelper.convertTo(double.class, "3.0", NopException::new));
    }

    @Test
    public void testNano() {
        long days = TimeUnit.NANOSECONDS.toDays(Long.MAX_VALUE);
        System.out.println(days + "," + (days / 365));
    }

    @Test
    public void testFloatToString() {
        System.out.println(1.2f);
        System.out.println((double) 1.2f);
    }

    @Test
    public void testTimestampToString() {
        Timestamp stamp = new Timestamp(123456);
        assertTrue(ConvertHelper.toString(stamp).endsWith("02:03.456"));

        LocalDateTime dt = LocalDateTime.now();
        assertFalse(ConvertHelper.toString(dt).contains("."));
    }

    @Test
    public void testEpochString() {
        // 13位毫秒时间戳保持按毫秒
        long millis = 1500000000000L;
        assertEquals(ConvertHelper.millisToLocalDate(millis),
                ConvertHelper.toLocalDate(String.valueOf(millis)));
        assertEquals(ConvertHelper.millisToLocalDateTime(millis),
                ConvertHelper.toLocalDateTime(String.valueOf(millis)));

        // 10位秒级时间戳按秒处理(等价于秒*1000毫秒)
        long secs = 1500000000L;
        assertEquals(ConvertHelper.millisToLocalDate(secs * 1000L),
                ConvertHelper.toLocalDate(String.valueOf(secs)));
        assertEquals(ConvertHelper.millisToLocalDateTime(secs * 1000L),
                ConvertHelper.toLocalDateTime(String.valueOf(secs)));

        // 10位不再被误当毫秒(否则会等于 millisToLocalDate(secs))
        Assertions.assertNotEquals(ConvertHelper.millisToLocalDate(secs),
                ConvertHelper.toLocalDate(String.valueOf(secs)));

        // 空串 -> null
        Assertions.assertNull(ConvertHelper.toLocalDate(""));
        Assertions.assertNull(ConvertHelper.toLocalDateTime(""));
    }

    @Test
    public void testMonthDayToString() {
        // MM-dd格式：day段必须是dayOfMonth，不能误用monthValue
        assertEquals("12-25", ConvertHelper.monthDayToString(java.time.MonthDay.of(12, 25)));
        assertEquals("01-01", ConvertHelper.monthDayToString(java.time.MonthDay.of(1, 1)));
        assertEquals("06-05", ConvertHelper.monthDayToString(java.time.MonthDay.of(6, 5)));
    }

    @Test
    public void testMonthDayRoundTrip() {
        java.time.MonthDay monthDay = java.time.MonthDay.of(12, 25);
        assertEquals(monthDay,
                ConvertHelper.toMonthDay(ConvertHelper.monthDayToString(monthDay), NopException::new));
    }

    @Test
    public void testLocalDateTimeMillisRoundTripDst() {
        TimeZone oldTz = TimeZone.getDefault();
        try {
            // 夏令时时区：raw offset与实际偏移夏季相差1小时
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));

            LocalDateTime summer = LocalDateTime.of(2026, 7, 15, 12, 0);
            Long millis = ConvertHelper.localDateTimeToMillis(summer);
            // 正反向转换必须对称（反向经Timestamp.toLocalDateTime使用含DST的实际偏移）
            assertEquals(summer, ConvertHelper.millisToLocalDateTime(millis),
                    "summer DST round trip must be symmetric");
            assertEquals(summer, new Timestamp(millis).toLocalDateTime());

            // 冬令时（无DST）同样对称
            LocalDateTime winter = LocalDateTime.of(2026, 1, 15, 12, 0);
            assertEquals(winter, ConvertHelper.millisToLocalDateTime(ConvertHelper.localDateTimeToMillis(winter)));
        } finally {
            TimeZone.setDefault(oldTz);
        }
    }

    @Test
    public void testToFalsyNaN() {
        // javadoc规定按JavaScript语义：NaN为假值
        assertTrue(ConvertHelper.toFalsy(Double.NaN), "NaN must be falsy");
        assertTrue(ConvertHelper.toFalsy(Float.NaN));
        assertTrue(ConvertHelper.toFalsy(0.0d));
        assertTrue(ConvertHelper.toFalsy(null));
        assertTrue(ConvertHelper.toFalsy(""));
        assertFalse(ConvertHelper.toFalsy(1.0d));
        assertFalse(ConvertHelper.toFalsy("a"));
        assertFalse(ConvertHelper.toFalsy(Boolean.TRUE));
    }
}