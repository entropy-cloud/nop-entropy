/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDictBean {

    private static DictBean buildDict() {
        List<DictOptionBean> options = new ArrayList<>();
        DictOptionBean a = new DictOptionBean();
        a.setValue(1);
        a.setLabel("A");
        DictOptionBean b = new DictOptionBean();
        b.setValue(2);
        b.setLabel("B");
        options.add(a);
        options.add(b);

        DictBean dict = new DictBean();
        dict.setName("test-dict");
        dict.setOptions(options);
        return dict;
    }

    @Test
    public void testGetOptionByValueAndLabel() {
        DictBean dict = buildDict();
        assertEquals("A", dict.getLabelByValue(1));
        assertEquals("B", dict.getLabelByValue(2));
        assertNull(dict.getLabelByValue(3));

        assertEquals(1, dict.getValueByLabel("A"));
        assertEquals(2, dict.getValueByLabel("B"));
        assertNull(dict.getValueByLabel("C"));

        // setOptions后缓存失效重建
        DictOptionBean c = new DictOptionBean();
        c.setValue(3);
        c.setLabel("C");
        List<DictOptionBean> options = new ArrayList<>(dict.getOptions());
        options.add(c);
        dict.setOptions(options);
        assertEquals("C", dict.getLabelByValue(3));
    }

    /**
     * 回归防护：字典实例常被缓存共享并跨线程读取，懒初始化的valueMap/labelMap
     * 并发首查不能读到未完全构建的Map。（不安全发布竞态无法确定性红验证，此处为压力回归网）
     */
    @Test
    public void testConcurrentLookupOnFrozenDict() throws Exception {
        DictBean dict = buildDict();
        dict.freeze(true);

        int threads = 8;
        int iterations = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int n = 0; n < iterations; n++) {
                        DictOptionBean byValue = dict.getOptionByValue(n % 2 + 1);
                        if (byValue == null || byValue.getValue() == null)
                            throw new IllegalStateException("option lookup by value failed");
                        DictOptionBean byLabel = dict.getOptionByLabel(n % 2 == 0 ? "A" : "B");
                        if (byLabel == null)
                            throw new IllegalStateException("option lookup by label failed");
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        // 冻结后的共享字典查询结果稳定
        assertSame(dict.getOptionByValue(1), dict.getOptionByValue(1));
    }
}
