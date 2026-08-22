/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestIntRangeSet {
    @Test
    public void testCompact() {
        String ranges = "1,6|7,1|9,3";

        IntRangeSet rangeSet = IntRangeSet.parse(ranges);
        rangeSet = rangeSet.compact();
        assertEquals("1,7|9,3", rangeSet.toString());
    }

    @Test
    public void testSplit() {
        String ranges = "1,6|7,1|9,6";

        IntRangeSet rangeSet = IntRangeSet.parse(ranges);
        List<IntRangeSet> sets  = rangeSet.split(3);
        assertEquals("[1,5, 6,1|7,1|9,2, 11,4]", sets.toString());
    }

    @Test
    public void testSplit2() {
        String ranges = "1,6|7,1|9,10";

        IntRangeSet rangeSet = IntRangeSet.parse(ranges);
        List<IntRangeSet> sets = rangeSet.split(3);
        assertEquals("[1,6, 7,1|9,5, 14,5]", sets.toString());
    }

    @Test
    public void testCompactEmpty() {
        // 空集合是合法构造，compact不应抛数组越界
        IntRangeSet empty = IntRangeSet.rangeSet(Collections.emptyList());
        assertSame(empty, empty.compact());
        assertEquals("", empty.compact().toString());
    }

    @Test
    public void testGetFirstBeginLastEndEmpty() {
        // 空集合上取首末边界应抛出带错误码的NopException，而不是裸数组越界
        IntRangeSet empty = IntRangeSet.rangeSet(Collections.emptyList());
        assertThrows(NopException.class, empty::getFirstBegin);
        assertThrows(NopException.class, empty::getLastEnd);
    }
}
