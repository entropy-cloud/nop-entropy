/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static io.nop.api.core.beans.LongRangeBean.longRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLongRangeBean {
    @Test
    public void testSerialize() {
        LongRangeBean bean = LongRangeBean.of(100, 10);

        String str = bean.toString();
        LongRangeBean bean2 = LongRangeBean.parse(str);
        assertEquals(bean, bean2);

        assertEquals(110, bean.getEnd());
        assertTrue(bean.containsValue(100));
        assertTrue(bean.containsValue(109));
        assertFalse(bean.containsValue(110));

        assertTrue(bean.contains(bean2));
    }

    @Test
    public void testIntersect() {
        LongRangeBean bean = LongRangeBean.of(100, 10);
        LongRangeBean bean2 = LongRangeBean.of(101, 100);
        assertEquals("101,9", bean.intersect(bean2).toString());
        assertTrue(bean.overlaps(bean2));

        LongRangeBean bean3 = LongRangeBean.of(101, 3);
        assertEquals("101,3", bean.intersect(bean3).toString());
        assertTrue(bean.overlaps(bean3));

        LongRangeBean bean4 = LongRangeBean.of(10, 1);
        assertTrue(!bean4.overlaps(bean));
        assertEquals("11,0", bean.intersect(bean4).toString());
    }

    @Test
    public void testRange() {
        LongRangeBean range = LongRangeBean.of(32768 / 2, 32768 / 2);
        LongRangeBean sub = range.subRange(2, 3);
        System.out.println(sub);
        assertEquals(27307, sub.getBegin());
        assertEquals(32768, sub.getEnd());

        sub = range.subRange(1, 3);
        System.out.println(sub);
        assertEquals(21846, sub.getBegin());
        assertEquals(27307, sub.getEnd());

        sub = range.subRange(0, 3);
        System.out.println(sub);
        assertEquals(16384, sub.getBegin());
        assertEquals(21846, sub.getEnd());

        assertEquals(16384, range.getBegin());

        range = LongRangeBean.of(0, 32768 / 2);
        assertEquals(0, range.getBegin());
        assertEquals(16384, range.getEnd());

        range = LongRangeBean.of(0, 3);
        assertEquals(longRange(0, 1), range.subRange(0, 5));
        assertEquals(longRange(1, 1), range.subRange(1, 5));
        assertEquals(longRange(2, 1), range.subRange(2, 5));
        assertEquals(longRange(3, 0), range.subRange(3, 5));
        assertEquals(longRange(3, 0), range.subRange(4, 5));

        range = LongRangeBean.of(0, 5);
        assertEquals(longRange(0, 2), range.subRange(0, 3));
        assertEquals(longRange(2, 2), range.subRange(1, 3));
        assertEquals(longRange(4, 1), range.subRange(2, 3));
    }
}