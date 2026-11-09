/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-010）载体钉：`CacheConfig.executor` 出口。
 */
public class TestLocalCacheExecutorConfig {

    /**
     * pin0310：设了 executor 后，Caffeine builder 真的接收它（Executor 身份断言 + 缓存功能不受损）。
     * <p>
     * 说明：Caffeine 的 {@code executor} 用于**异步维护任务**（refresh、清理、异步加载调度），
     * 不用于同步 loader 调用，故不能以「loader 计数」判别。本钉用：
     * ① 配置可达（getter 往返）；② 传入后缓存仍正常工作（证明 builder.executor 未破坏装配）；
     * ③ 用「被包装的 executor」在 async 路径触发一次（refresh）以确认接线。
     */
    @Test
    public void pin0310_configuredExecutorIsWiredIntoBuilder() throws Exception {
        AtomicInteger executorCalls = new AtomicInteger();
        Executor counting = r -> {
            executorCalls.incrementAndGet();
            r.run();
        };

        CacheConfig config = new CacheConfig();
        config.setExecutor(counting);
        assertNotNull(config.getExecutor(), "executor 出口必须可设");
        assertEquals(counting, config.getExecutor(), "getter 必须原样返回");

        LocalCache<String, String> cache = LocalCache.newCache("cra310-" + System.nanoTime(),
                config, key -> key + "-v");
        try {
            assertEquals("k1-v", cache.get("k1"), "缓存必须能正常加载（executor 装配不破坏功能）");
            cache.refresh("k1");   // async 维护路径 → 应经配置的 executor
            Thread.sleep(200);
            assertTrue(executorCalls.get() > 0,
                    "refresh 等 async 维护任务必须经配置的 executor，实际调用次数=" + executorCalls.get());
        } finally {
            cache.destroy();
        }
    }

    /** pin0310b：缺省 null（保持既有 commonPool 行为）。 */
    @Test
    public void pin0310b_defaultExecutorIsNull() {
        assertNull(new CacheConfig().getExecutor(),
                "缺省必须为 null（沿用 Caffeine commonPool，不改变既有并发形态）");
    }
}
