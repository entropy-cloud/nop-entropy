/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core.cache;

import io.nop.commons.cache.CacheConfig;
import io.nop.nosql.core.INosqlKeyValueOperations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestNosqlCache {

    @SuppressWarnings("unchecked")
    private INosqlKeyValueOperations recordingOps(AtomicReference<String> method, AtomicReference<Object[]> args) {
        return (INosqlKeyValueOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{INosqlKeyValueOperations.class},
                (proxy, m, a) -> {
                    method.set(m.getName());
                    args.set(a);
                    Class<?> rt = m.getReturnType();
                    if (CompletionStage.class.isAssignableFrom(rt) || CompletableFuture.class.isAssignableFrom(rt))
                        return CompletableFuture.completedFuture("stub-" + m.getName());
                    if (Map.class.isAssignableFrom(rt))
                        return new HashMap<>();
                    if (rt == long.class || rt == Long.class)
                        return 0L;
                    if (rt == boolean.class || rt == Boolean.class)
                        return Boolean.FALSE;
                    return "stub-" + m.getName();
                });
    }

    @Test
    void testGetAsyncWithoutExpireAfterAccess() throws Exception {
        CacheConfig config = CacheConfig.newConfig(10); // expireAfterAccess stays null
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<Object[]> args = new AtomicReference<>();

        NosqlCache cache = new NosqlCache("test", recordingOps(method, args), config);
        Object value = cache.getAsync("k").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("getAsync", method.get(), "without expireAfterAccess the plain getAsync path must be used");
        assertNotNull(value);
    }

    @Test
    void testGetAsyncWithExpireAfterAccess() throws Exception {
        CacheConfig config = CacheConfig.newConfig(10);
        config.setExpireAfterAccess(Duration.ofMillis(30000));
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<Object[]> args = new AtomicReference<>();

        NosqlCache cache = new NosqlCache("test", recordingOps(method, args), config);
        Object value = cache.getAsync("k").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("getExAsync", method.get());
        assertEquals("k", args.get()[0]);
        assertEquals(30000L, args.get()[1]);
        assertNotNull(value);
    }
}
