/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * plan 2318 roadmap WI5：展示层脱敏工具函数（脱敏展示字段的派生辅助）。
 */
public class TestMaskHelper {

    @Test
    public void testKeepFirstNLastM() {
        assertEquals("15", MaskHelper.keepFirstNLastM("12345", 1, 1).replace("*", ""));
        assertEquals("1***5", MaskHelper.keepFirstNLastM("12345", 1, 1));
        assertEquals("138****5678", MaskHelper.keepFirstNLastM("13812345678", 3, 4));
        // n+m 覆盖全长：保留语义原样返回
        assertEquals("123", MaskHelper.keepFirstNLastM("123", 2, 1));
        assertEquals("123", MaskHelper.keepFirstNLastM("123", 1, 2));
    }

    @Test
    public void testMaskFirstNLastM() {
        // 遮盖前 1 后 1，保留中间
        assertEquals("*234*", MaskHelper.maskFirstNLastM("12345", 1, 1));
        // n+m 覆盖全长：遮盖语义全遮
        assertEquals("***", MaskHelper.maskFirstNLastM("123", 2, 1));
    }

    @Test
    public void testMaskFromXToY() {
        assertEquals("12***67", MaskHelper.maskFromXToY("1234567", 2, 5));
        // 越界截断
        // from 越过末尾（0 起下标 7 == len）为空区间，原样返回
        assertEquals("1234567", MaskHelper.maskFromXToY("1234567", 7, 99));
        // 空区间原样
        assertEquals("123", MaskHelper.maskFromXToY("123", 2, 2));
    }

    @Test
    public void testMaskAllAndFixed() {
        assertEquals("*****", MaskHelper.maskAll("12345"));
        // 固定长度不泄露原长度
        assertEquals("******", MaskHelper.maskFixed("12345", 6));
        assertEquals("*", MaskHelper.maskFixed("1", 0));
    }

    @Test
    public void testNullAndEmpty() {
        assertNull(MaskHelper.keepFirstNLastM(null, 1, 1));
        assertEquals("", MaskHelper.maskAll(""));
        assertEquals("", MaskHelper.maskFromXToY("", 0, 3));
    }
}
