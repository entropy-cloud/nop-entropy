/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import io.nop.commons.util.CollectionHelper;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class TestCache {
    /**
     * Map的速度大概是Cache的两倍 n = 100,000, map=357ms, cache=642ms
     */
    @Test
    public void testSpeed() {
        ICache<String, Object> cache = LocalCache.newCache("a", new CacheConfig().weakKeys());
        Map<String, Object> map = CollectionHelper.newConcurrentWeakMap();

        int n = 100000;

        run("map", () -> {
            for (int i = 0; i < n; i++) {
                String key = String.valueOf(i);
                map.put(key, "bbbb");
                map.get(key);
            }
        });

        run("cache", () -> {
            for (int i = 0; i < n; i++) {
                String key = String.valueOf(i);
                cache.put(key, "bbbb");
                cache.get(key);
            }
        });

    }

    void run(String title, Runnable task) {
        long beginTime = System.currentTimeMillis();
        task.run();
        long endTime = System.currentTimeMillis();
        System.out.println(title + "=" + (endTime - beginTime) + "ms");
    }
}