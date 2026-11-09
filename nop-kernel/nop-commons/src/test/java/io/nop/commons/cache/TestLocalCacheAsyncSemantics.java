/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-009）载体钉：`IAsyncCache.getAllAsync` 的**契约收窄后**行为锚。
 * <p>
 * 收窄后契约：未命中的 key **不触发加载**，结果 map 中对应条目缺席。
 */
public class TestLocalCacheAsyncSemantics {

    /**
     * pin0309（C3-009，**判别性契约钉**）：async 分支遇未命中 key 时**不加载、且整体失败**
     * （Caffeine 拒绝 `k -> null` → `NullMapCompletionException`）。
     * <p>
     * 判别面（三者同时断言，任一变化即红）：
     * <ol>
     *   <li>调用以 {@code ExecutionException} 失败（不是返回缺席 map）；</li>
     *   <li>cause 是 Caffeine 的 {@code NullMapCompletionException}（不加载的直接证据）；</li>
     *   <li><b>loader 调用计数 == 0</b>（证明"未命中未触发加载"——这是契约收窄的核心）。</li>
     * </ol>
     */
    @Test
    public void pin0309_missDoesNotLoad() throws Exception {
        CacheConfig config = new CacheConfig();
        config.setAsync(true);

        java.util.concurrent.atomic.AtomicInteger loaderCalls = new java.util.concurrent.atomic.AtomicInteger();
        LocalCache<String, String> cache = LocalCache.newCache("cra309-" + System.nanoTime(),
                config, key -> {
                    loaderCalls.incrementAndGet();
                    return key + "-loaded";
                });
        try {
            CompletionStage<Map<String, String>> stage =
                    cache.getAllAsync(List.of("missing-1", "missing-2"));

            java.util.concurrent.ExecutionException ex = assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS),
                    "未命中 key 必须使整体调用失败（不得返回缺席 map）：契约按 live 实况收窄");

            String causeName = ex.getCause() == null ? "" : ex.getCause().getClass().getName();
            assertTrue(causeName.contains("NullMapCompletionException"),
                    "失败原因必须是 Caffeine 的 NullMapCompletionException（k->null 拒绝），实际=" + causeName);

            assertEquals(0, loaderCalls.get(),
                    "核心契约：未命中**不得触发加载**，实际 loader 调用=" + loaderCalls.get());
        } finally {
            cache.destroy();
        }
    }

    /** pin0309b：已缓存条目必须出现在结果中（收窄不破坏既有命中语义）。 */
    @Test
    public void pin0309b_cachedEntryStillReturned() throws Exception {
        CacheConfig config = new CacheConfig();
        config.setAsync(true);
        LocalCache<String, String> cache = LocalCache.newCache("cra309b-" + System.nanoTime(),
                config, key -> key + "-loaded");
        try {
            cache.put("hit", "hit-value");
            Map<String, String> result = cache.getAllAsync(List.of("hit"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue("hit-value".equals(result.get("hit")),
                    "已缓存条目必须返回，实际=" + result);
        } finally {
            cache.destroy();
        }
    }
}
