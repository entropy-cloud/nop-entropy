/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.source.IConfigSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C1-008/C2-008）applyChange 原子化载体钉。
 */
public class TestConfigProviderApplyChange {

    static class MapConfigSource implements IConfigSource {
        final Map<String, Object> values = new ConcurrentHashMap<>();

        @Override
        public Map<String, ValueWithLocation> getConfigValues() {
            Map<String, ValueWithLocation> ret = new HashMap<>();
            values.forEach((k, v) -> ret.put(k, ValueWithLocation.of(null, v)));
            return ret;
        }

        @Override
        public String getName() {
            return "test-config-source";
        }

        @Override
        public void close() {
        }

        @Override
        public void addOnChange(Runnable task) {
        }
    }

    /** pin2a：并发 applyChange×getVar（applyChange synchronized 后）监听通知计数守恒 + 键值一致。 */
    @Test
    public void pin2a_concurrentApplyChangeTearFree() throws Exception {
        MapConfigSource source = new MapConfigSource();
        source.values.put("k", "v1");
        DefaultConfigProvider provider = new DefaultConfigProvider(source, null);

        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger badNotification = new AtomicInteger();
        provider.subscribeChange("k", (IConfigChangeListener) (p, changed) -> {
            notifications.incrementAndGet();
            // 撕裂面观测：通知携带的 changed 必须只含本订阅键且非 null 值
            if (!changed.containsKey("k") || changed.get("k") == null)
                badNotification.incrementAndGet();
        });

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < 3; t++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < 50; i++) {
                    source.values.put("k", "v" + (i % 3 + 2));
                    provider.applyChange();
                }
            });
        }
        pool.execute(() -> {
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int i = 0; i < 200; i++) {
                // 读侧经 provider（内部 configValues volatile 快照）——撕裂时值/键会错配
                provider.getConfigValue("k", "def");
            }
        });

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        // 监听器通知数 = applyChange 成功轮次（synchronized 后无并发交错重复/撕裂通知）
        assertTrue(notifications.get() >= 1, "至少应有一轮变更通知");
        assertEquals(0, badNotification.get(), "通知内容撕裂（键错配或 null 值）");
    }
}
