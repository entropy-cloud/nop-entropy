/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLogLevel {

    @Test
    public void testLevelOrderingMatchesJVMLogLevels() {
        assertTrue(LogLevel.TRACE.getLevel() < LogLevel.DEBUG.getLevel());
        assertTrue(LogLevel.DEBUG.getLevel() < LogLevel.INFO.getLevel());
        assertTrue(LogLevel.INFO.getLevel() < LogLevel.WARN.getLevel());
        assertTrue(LogLevel.WARN.getLevel() < LogLevel.ERROR.getLevel());
        // OFF 级别值最大
        assertTrue(LogLevel.ERROR.getLevel() < LogLevel.OFF.getLevel());
        assertEquals(1, LogLevel.TRACE.getLevel());
        assertEquals(Integer.MAX_VALUE, LogLevel.OFF.getLevel());
    }

    @Test
    public void testFromTextIsCaseInsensitive() {
        assertEquals(LogLevel.TRACE, LogLevel.fromText("trace"));
        assertEquals(LogLevel.DEBUG, LogLevel.fromText("DEBUG"));
        assertEquals(LogLevel.INFO, LogLevel.fromText("Info"));
        assertEquals(LogLevel.WARN, LogLevel.fromText("WARN"));
        assertEquals(LogLevel.ERROR, LogLevel.fromText("error"));
        assertEquals(LogLevel.OFF, LogLevel.fromText("off"));
    }

    @Test
    public void testFromTextReturnsNullForNullEmptyOrUnknown() {
        assertNull(LogLevel.fromText(null));
        assertNull(LogLevel.fromText(""));
        assertNull(LogLevel.fromText("VERBOSE"));
        assertNull(LogLevel.fromText("info "));
    }
}
