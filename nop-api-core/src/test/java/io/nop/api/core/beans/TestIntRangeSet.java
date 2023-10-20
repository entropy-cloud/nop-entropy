/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        List<IntRangeSet> sets  = rangeSet.split(3);
        assertEquals("[1,6, 7,1|9,5, 14,5]", sets.toString());
    }
}
