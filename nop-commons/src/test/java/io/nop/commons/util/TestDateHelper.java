/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import io.nop.api.core.convert.ConvertHelper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestDateHelper {

    void checkDuration(String target, String source) {
        assertEquals(target, DateHelper.parseDuration(source).toString());
    }

    @Test
    public void testDuration() {
        String duration = "1.5d";
        checkDuration("PT36H", "1.5d");
        checkDuration("PT30M", "0.5h");
        checkDuration("PT1M30S", "1.5m");
        checkDuration("PT1M40.5S", "100.5s");
        checkDuration("PT0.2S", "200ms");
    }

    @Test
    public void testStartOfMonth() {
        LocalDate now = LocalDate.now();
        LocalDate d = DateHelper.firstDayOfMonth(now);
        assertEquals(d.getMonth(), now.getMonth());
        assertEquals(d.getYear(), now.getYear());
        assertEquals(d.getDayOfMonth(), 1);

        d = DateHelper.lastDayOfMonth(now);
        assertEquals(d.getMonth(), now.getMonth());
        assertEquals(d.getYear(), now.getYear());
        assertEquals(d.plusDays(1).getDayOfMonth(), 1);
    }

    @Test
    public void testDayOfWeek() {
        LocalDate now = LocalDate.now();
        for (int i = 1; i <= 7; i++) {
            assertEquals(i, DateHelper.toDayOfWeek(now, i).getDayOfWeek().getValue());
        }
    }

    @Test
    public void testZone() {
        LocalDateTime dt = DateHelper.toZone(LocalDateTime.now(), ZoneOffset.UTC);
        System.out.println(dt);
    }

    @Test
    public void testMonthEnd() {
        LocalDateTime time = LocalDateTime.of(2002, 3, 4, 1, 1, 0);
        long ts = DateHelper.getMonthEndWithTimeZoneTs(TimeZone.getDefault(), DateHelper.dateTimeToMillis(time));

        String str = new Timestamp(ts).toString();
        System.out.println(str);
        assertEquals("2002-04-01 00:00:00.0", str);
    }

    @Test
    public void testToString() {
        LocalDateTime dateTime = LocalDateTime.now();
        System.out.println(dateTime);

        String str = ConvertHelper.toString(dateTime);
        System.out.println(str);
        assertEquals(str, DateHelper.formatDateTime(dateTime, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void testSafeParseDate() {
        String[] patterns = new String[]{"yyyyMMdd", "yyyy-MM-dd", "yyyy/MM/dd", "yyMMdd"};
        assertEquals(DateHelper.parseDate("2024-01-02"), DateHelper.safeParseDate("20240102", patterns));
        assertEquals(DateHelper.parseDate("2024-01-02"), DateHelper.safeParseDate("2024-01-02", patterns));
        assertEquals(DateHelper.parseDate("2024-01-02"), DateHelper.safeParseDate("2024/01/02", patterns));
        assertEquals(DateHelper.parseDate("2024-01-02"), DateHelper.safeParseDate("240102", patterns));
        assertNull(DateHelper.safeParseDate("2024_01_02", patterns));
    }
}
