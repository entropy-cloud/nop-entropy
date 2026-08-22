/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLazy {

    @Test
    public void testGetCachesSupplierValue() {
        AtomicInteger count = new AtomicInteger();
        Lazy<String> lazy = Lazy.of(() -> {
            count.incrementAndGet();
            return "value";
        });

        assertTrue(lazy.get() == lazy.get());
        assertEquals("value", lazy.get());
        // supplier 只应被执行一次，后续get()应返回缓存值
        assertEquals(1, count.get());
        assertTrue(lazy.isLoaded());
    }

    @Test
    public void testGetCachesNullValue() {
        AtomicInteger count = new AtomicInteger();
        Lazy<String> lazy = Lazy.of(() -> {
            count.incrementAndGet();
            return null;
        });

        assertNull(lazy.get());
        assertNull(lazy.get());
        // supplier 返回null时也应缓存，不应重复执行
        assertEquals(1, count.get());
        assertTrue(lazy.isLoaded());
    }

    @Test
    public void testSetPreloads() {
        Lazy<String> lazy = Lazy.of(() -> "computed");
        lazy.set("preset");
        assertSame("preset", lazy.get());
    }
}
