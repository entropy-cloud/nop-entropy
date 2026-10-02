/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonParseOptions {

    @Test
    public void testDefaults() {
        JsonParseOptions options = JsonParseOptions.create();
        // 严格模式默认开启，其余解析辅助开关默认关闭
        assertTrue(options.isStrictMode());
        assertFalse(options.isYaml());
        assertFalse(options.isKeepLocation());
        assertFalse(options.isKeepComment());
        assertFalse(options.isIntern());
        assertFalse(options.isTraceDepends());
        assertFalse(options.isIgnoreUnknownProp());
        assertNull(options.getDefaultEncoding());
        assertNull(options.getTargetType());
    }

    @Test
    public void testFluentWithMethodsReturnSameInstance() {
        JsonParseOptions options = JsonParseOptions.create();
        assertSame(options, options.withYaml(true));
        assertSame(options, options.withStrictMode(false));
        assertSame(options, options.withTargetType(List.class));
        assertSame(options, options.withIgnoreUnknownProp(true));

        assertTrue(options.isYaml());
        assertFalse(options.isStrictMode());
        assertEquals(List.class, options.getTargetType());
        assertTrue(options.isIgnoreUnknownProp());
    }

    @Test
    public void testSetterRoundTrip() {
        JsonParseOptions options = new JsonParseOptions();
        options.setKeepLocation(true);
        options.setKeepComment(true);
        options.setIntern(true);
        options.setTraceDepends(true);
        options.setDefaultEncoding("GBK");

        assertTrue(options.isKeepLocation());
        assertTrue(options.isKeepComment());
        assertTrue(options.isIntern());
        assertTrue(options.isTraceDepends());
        assertEquals("GBK", options.getDefaultEncoding());

        // strictMode 可切换回非严格
        options.setStrictMode(false);
        assertFalse(options.isStrictMode());
    }
}
