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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}