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
        assertEquals("1970-01-01 08:02:03.456", ConvertHelper.toString(stamp));

        LocalDateTime dt = LocalDateTime.now();
        assertFalse(ConvertHelper.toString(dt).contains("."));
    }
}