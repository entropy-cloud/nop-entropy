/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import io.nop.api.core.exceptions.NopException;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestGlobalCacheRegistry {

    static class TestCacheMgmt implements ICacheManagement<String> {
        private final String name;

        TestCacheMgmt(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void remove(@Nonnull String key) {
        }

        @Override
        public void clear() {
        }

        @Override
        public CacheStats stats() {
            return null;
        }
    }

    @Test
    public void testRegisterDuplicateKeepsFirst() {
        GlobalCacheRegistry registry = new GlobalCacheRegistry();
        TestCacheMgmt first = new TestCacheMgmt("dup-cache");
        TestCacheMgmt second = new TestCacheMgmt("dup-cache");

        registry.register(first);

        // 重复注册必须抛异常
        assertThrows(NopException.class, () -> registry.register(second));
        // 注册失败时注册表必须保持原状，不能已被新缓存悄悄替换
        assertSame(first, registry.getCache("dup-cache"));
    }
}
