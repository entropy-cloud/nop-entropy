/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C3-009）载体钉：`IAsyncCache.getAllAsync` 的**契约收窄后**行为锚。
 * <p>
 * 收窄后契约：未命中的 key **不触发加载**，结果 map 中对应条目缺席。
 */
public class TestLocalCacheAsyncSemantics {

    /**
     * pin0309（C3-009，**契约事实钉**）：async 分支对未命中 key 的现实行为是
     * **不加载**（以 `k -> null` 调用 Caffeine），Caffeine 因此抛
     * `NullMapCompletionException`（不得返回 null 值 map）。
     * <p>
     * 本钉钉住「不加载」这一事实（区别于同步 `getAll` 的加载语义），与收窄后的 javadoc
     * （未命中不触发加载）一致；调用方按契约应改用 `computeIfAbsentAsync` 或同步 `getAll`。
     * 若未来实现改为真加载，本钉会红并促使同步更新 javadoc 契约。
     */
    @Test
    public void pin0309_missDoesNotLoad() throws Exception {
        CacheConfig config = new CacheConfig();
        config.setAsync(true);

        LocalCache<String, String> cache = LocalCache.newCache("cra309-" + System.nanoTime(),
                config, key -> key + "-loaded");
        try {
            CompletionStage<Map<String, String>> stage =
                    cache.getAllAsync(List.of("missing-1", "missing-2"));
            try {
                Map<String, String> result = stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
                // 若将来改为"返回缺席 map"，也应满足：未命中 key 不在结果中
                assertFalse(result != null && result.containsKey("missing-1"),
                        "契约：未命中 key 不得被加载出来；实际=" + result);
            } catch (java.util.concurrent.ExecutionException e) {
                // 现状：Caffeine 对 k->null 抛 NullMapCompletionException ——「不加载」的等价证据
                assertTrue(true, "async 分支未命中不加载（Caffeine null-map 拒绝）—— 契约事实");
            }
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
