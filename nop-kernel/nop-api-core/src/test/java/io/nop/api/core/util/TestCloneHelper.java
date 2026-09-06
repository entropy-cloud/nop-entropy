/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestCloneHelper {

    /**
     * 回归：deepMerge(ret, m1, m2)的javadoc承诺不修改m1/m2。
     * 修复前m1的嵌套Map会经由ret被m2的合并就地改写。
     */
    @Test
    public void testDeepMergeThreeArgsDoesNotModifySources() {
        Map<String, Object> m1Nested = new HashMap<>();
        m1Nested.put("b", 1);
        Map<String, Object> m1 = new HashMap<>();
        m1.put("a", m1Nested);
        m1.put("onlyM1", "x");

        Map<String, Object> m2Nested = new HashMap<>();
        m2Nested.put("c", 2);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("a", m2Nested);
        m2.put("onlyM2", "y");

        Map<String, Object> ret = new HashMap<>();
        CloneHelper.deepMerge(ret, m1, m2);

        // ret包含两侧合并结果
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) ret.get("a");
        assertEquals(2, merged.size());
        assertEquals(1, merged.get("b"));
        assertEquals(2, merged.get("c"));
        assertEquals("x", ret.get("onlyM1"));
        assertEquals("y", ret.get("onlyM2"));

        // m1/m2保持不变
        assertEquals(1, m1Nested.size());
        assertSame(m1Nested, m1.get("a"));
        assertEquals(1, m2Nested.size());
        assertSame(m2Nested, m2.get("a"));
    }

    /**
     * null与空Map入参不抛异常。
     */
    @Test
    public void testDeepMergeThreeArgsNullAndEmpty() {
        Map<String, Object> ret = new HashMap<>();
        CloneHelper.deepMerge(ret, null, null);
        assertEquals(0, ret.size());

        Map<String, Object> m2 = new HashMap<>();
        m2.put("a", 1);
        CloneHelper.deepMerge(ret, CloneHelper.EMPTY_MAP, m2);
        assertEquals(1, ret.size());
    }
}
