/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static io.nop.api.core.beans.IntRangeBean.intRange;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIntRangeBean {
    @Test
    public void testRange() {
        IntRangeBean range = IntRangeBean.of(32768 / 2, 32768 / 2);
        IntRangeBean sub = range.partitionRange(2, 3);
        System.out.println(sub);
        assertEquals(27307, sub.getBegin());
        assertEquals(32768, sub.getEnd());

        sub = range.partitionRange(1, 3);
        System.out.println(sub);
        assertEquals(21846, sub.getBegin());
        assertEquals(27307, sub.getEnd());

        sub = range.partitionRange(0, 3);
        System.out.println(sub);
        assertEquals(16384, sub.getBegin());
        assertEquals(21846, sub.getEnd());

        assertEquals(16384, range.getBegin());

        range = IntRangeBean.of(0, 32768 / 2);
        assertEquals(0, range.getBegin());
        assertEquals(16384, range.getEnd());

        range = IntRangeBean.of(0, 3);
        assertEquals(intRange(0, 1), range.partitionRange(0, 5));
        assertEquals(intRange(1, 1), range.partitionRange(1, 5));
        assertEquals(intRange(2, 1), range.partitionRange(2, 5));
        assertEquals(intRange(3, 0), range.partitionRange(3, 5));
        assertEquals(intRange(3, 0), range.partitionRange(4, 5));

        range = IntRangeBean.of(0, 5);
        assertEquals(intRange(0, 2), range.partitionRange(0, 3));
        assertEquals(intRange(2, 2), range.partitionRange(1, 3));
        assertEquals(intRange(4, 1), range.partitionRange(2, 3));

        range = IntRangeBean.of(3, 0);
        assertEquals(intRange(3, 0), range.partitionRange(0, 2));
    }
}
