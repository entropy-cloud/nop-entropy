/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.utils.JsDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsDate extends BaseTestCase {

    private IClock originalClock;
    private TimeZone originalTimeZone;

    @BeforeEach
    public void setUp() {
        originalClock = CoreMetrics.defaultClock();
        // Pin timezone so getTimezoneOffset() is deterministic across CI runners
        // (GitHub Actions runners default to UTC, which makes the offset == 0
        // and breaks the non-zero assertion in testJsDateJavascriptMethods).
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @AfterEach
    public void tearDown() {
        CoreMetrics.registerClock(originalClock);
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void testMockClockControlsJsDate() {
        long fixed = 1705290600000L; // 2024-01-15 10:30:00 UTC+8
        CoreMetrics.registerClock(new IClock() {
            @Override
            public long currentTimeMillis() {
                return fixed;
            }

            @Override
            public long nanoTime() {
                return fixed * 1_000_000L;
            }

            @Override
            public LocalDate currentDate() {
                return LocalDate.of(2024, 1, 15);
            }

            @Override
            public LocalDateTime currentDateTime() {
                return LocalDateTime.of(2024, 1, 15, 10, 30);
            }
        });

        JsDate d = new JsDate();
        assertEquals(fixed, d.getTime());
        assertEquals(fixed, JsDate.now());
        assertEquals(fixed, CoreMetrics.currentTimeMillis());
    }

    @Test
    public void testJsDateConstructors() {
        JsDate d = new JsDate(1000L);
        assertEquals(1000L, d.getTime());

        JsDate fromStr = new JsDate("2024-01-15 10:30:00");
        assertEquals(2024, fromStr.getFullYear());
        assertEquals(0, fromStr.getMonth());
        assertEquals(15, fromStr.getDate());

        JsDate fromYmd = new JsDate(2024, 5, 20);
        assertEquals(2024, fromYmd.getFullYear());
        assertEquals(5, fromYmd.getMonth());
        assertEquals(20, fromYmd.getDate());

        JsDate now = new JsDate();
        assertTrue(now.getTime() > 0);
    }

    @Test
    public void testJsDateJavascriptMethods() {
        JsDate d = new JsDate(1000L);
        assertTrue(d.toISOString().startsWith("1970-01-01T00:00:01"));
        assertTrue(d instanceof java.util.Date);

        int offset = d.getTimezoneOffset();
        assertTrue(offset != 0);

        long utc = JsDate.UTC(2024, 0, 1);
        assertTrue(utc > 0);
    }
}