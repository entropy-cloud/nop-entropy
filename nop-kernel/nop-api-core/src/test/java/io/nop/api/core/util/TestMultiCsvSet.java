/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMultiCsvSet {

    /**
     * 语义固化（非红验证，行为不变）：空集合是MultiCsvSet的合法输入——
     * EMPTY常量本身就是空集合，构造器实际只做null校验（修复前误用Guard.notEmpty产生语义误导）。
     */
    @Test
    public void testEmptyListAllowedNullRejected() {
        assertTrue(MultiCsvSet.EMPTY.isEmpty());
        assertEquals(0, MultiCsvSet.EMPTY.size());

        MultiCsvSet empty = new MultiCsvSet(Collections.emptyList());
        assertTrue(empty.isEmpty());
        assertNull(empty.getFirst());

        assertThrows(IllegalArgumentException.class, () -> new MultiCsvSet(null));
    }

    @Test
    public void testFromTextAndRoundTrip() {
        MultiCsvSet set = MultiCsvSet.fromText("a,b|c");
        assertEquals(2, set.size());
        Set<String> first = new LinkedHashSet<>();
        first.add("a");
        first.add("b");
        assertEquals(first, set.getFirst());
        assertEquals("a,b|c", set.toString());

        // 空输入得到EMPTY
        assertSame(MultiCsvSet.EMPTY, MultiCsvSet.fromText(""));
        assertSame(MultiCsvSet.EMPTY, MultiCsvSet.fromText(null));
    }
}
