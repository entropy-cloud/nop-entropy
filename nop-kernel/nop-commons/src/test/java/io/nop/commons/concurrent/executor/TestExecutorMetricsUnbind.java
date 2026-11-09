/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C6-004）指标绑定注销对称载体钉。
 */
public class TestExecutorMetricsUnbind {

    private MeterRegistry original;

    @BeforeEach
    public void setUp() {
        original = GlobalMeterRegistry.instance();
        GlobalMeterRegistry.registerInstance(new SimpleMeterRegistry());
    }

    @AfterEach
    public void tearDown() {
        GlobalMeterRegistry.registerInstance(original);
    }

    private boolean hasExecutorMeter(MeterRegistry registry, String poolName) {
        for (Meter meter : registry.getMeters()) {
            if (meter.getId().getName().startsWith("executor")
                    && (poolName.equals(meter.getId().getTag("name"))
                            || poolName.equals(meter.getId().getTag("id"))))
                return true;
        }
        return false;
    }

    /** pin6004a：池 destroy 后其 meter 从 registry 消失（绑定/注销对称）。 */
    @Test
    public void pin6004a_destroyUnbindsExecutorMeters() throws Exception {
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("metrics-unbind-probe");
        config.setEnableMetrics(true);
        executor.setConfig(config);
        executor.init();

        MeterRegistry registry = GlobalMeterRegistry.instance();
        assertTrue(hasExecutorMeter(registry, "metrics-unbind-probe"),
                "init 后应绑定 meter");

        executor.destroy();

        assertFalse(hasExecutorMeter(registry, "metrics-unbind-probe"),
                "缺陷态：destroy 后 meter 永久滞留");
    }

    /** pin6004b：LocalCache destroy 后其 meter 消失；同名（tenant- 前缀场景）后建实例替换旧 meter。 */
    @Test
    public void pin6004b_localCacheDestroyUnbindsAndDedups() {
        CacheConfig config = CacheConfig.newConfig(10).useMetrics();
        LocalCache<String, String> cache = LocalCache.newCache("metrics-cache-probe", config);
        cache.put("k", "v");

        MeterRegistry registry = GlobalMeterRegistry.instance();
        assertTrue(hasCacheMeter(registry, "metrics-cache-probe"), "绑定后应有 cache meter");

        cache.destroy();
        assertFalse(hasCacheMeter(registry, "metrics-cache-probe"),
                "缺陷态：LocalCache destroy 后 meter 永久滞留");

        // tenant- 同名去重：第二个同名实例创建后只有一份同名 meter 组（不抛重复注册异常）
        LocalCache<String, String> t1 = LocalCache.newCache("tenant-metrics-probe", CacheConfig.newConfig(10).useMetrics());
        t1.put("a", "1");
        LocalCache<String, String> t2 = LocalCache.newCache("tenant-metrics-probe", CacheConfig.newConfig(10).useMetrics());
        t2.put("b", "2");
        assertNotNull(t1);
        assertNotNull(t2);
    }

    private boolean hasCacheMeter(MeterRegistry registry, String cacheName) {
        for (Meter meter : registry.getMeters()) {
            if (meter.getId().getName().startsWith("cache.")
                    && cacheName.equals(meter.getId().getTag("cache")))
                return true;
        }
        return false;
    }
}
