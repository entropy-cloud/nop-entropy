/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * check2 处置新增：数值/复合主键不因 getEntityId 的 String 强转而 CCE。
 * 注：NopException.param 的 normalizeValue 对非 Number/Map/Collection 值做 String.valueOf，
 * 数值主键保真（Long 原样存储）、List 复合主键保真；数组形式在存储层即被字符串化（既有行为）。
 */
public class TestUnknownEntityException {

    @Test
    public void testNumericEntityIdToString() {
        UnknownEntityException ex = new UnknownEntityException("io.nop.test.MyEntity", 12345L);
        assertEquals("12345", ex.getEntityId());
    }

    @Test
    public void testCompositeEntityIdToString() {
        UnknownEntityException ex = new UnknownEntityException("io.nop.test.MyEntity",
                java.util.List.of("a", 2));
        assertEquals("[a, 2]", ex.getEntityId());
    }

    @Test
    public void testStringEntityIdUnchanged() {
        UnknownEntityException ex = new UnknownEntityException("io.nop.test.MyEntity", "id-1");
        assertEquals("id-1", ex.getEntityId());
    }
}
