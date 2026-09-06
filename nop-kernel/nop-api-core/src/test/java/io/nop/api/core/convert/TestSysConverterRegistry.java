/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.convert;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSysConverterRegistry {

    /**
     * 回归防护：注册表暴露运行期register/remove API，底层必须使用并发Map，
     * 热路径读取与注册/注销并发时不能丢条目。（并发丢条目无法确定性红验证，此处为压力回归网）
     */
    @Test
    public void testConcurrentRegisterAndRead() throws Exception {
        SysConverterRegistry registry = new SysConverterRegistry();
        int writers = 4;
        int perWriter = 200;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);

            for (int w = 0; w < writers; w++) {
                final int writer = w;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int i = 0; i < perWriter; i++) {
                        registry.registerNamedConverter("conv-" + writer + "-" + i,
                                new TargetTypeConverter(String.class, (o, e) -> o));
                        registry.getConverterByName("conv-" + writer + "-" + i);
                    }
                }));
            }

            Future<?> reader = pool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    registry.getConverterByType(String.class);
                }
            });

            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            reader.cancel(true);

            // 所有注册条目都必须可读
            int count = 0;
            for (int w = 0; w < writers; w++) {
                for (int i = 0; i < perWriter; i++) {
                    assertNotNull(registry.getConverterByName("conv-" + w + "-" + i),
                            "registered converter must be readable: conv-" + w + "-" + i);
                    count++;
                }
            }
            assertTrue(count == writers * perWriter);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testUnregisterTypeConverter() {
        SysConverterRegistry registry = new SysConverterRegistry();
        ITypeConverter converter = (o, e) -> o;
        registry.registerConverter("tmpConv", TestSysConverterRegistry.class, converter);
        assertSame(converter, registry.getConverterByType(TestSysConverterRegistry.class));

        registry.unregisterTypeConverter("tmpConv", TestSysConverterRegistry.class, converter);
        assertNull(registry.getConverterByType(TestSysConverterRegistry.class));
        assertNull(registry.getConverterByName("tmpConv"));
    }

    /**
     * 回归：registerNamedConverter的null校验对象必须是converter。
     * 修复前null converter不被Guard拦截，落到ConcurrentHashMap.put抛裸NPE。
     */
    @Test
    public void testRegisterNamedConverterNullConverter() {
        SysConverterRegistry registry = new SysConverterRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerNamedConverter("conv", null),
                "null converter must be rejected by Guard instead of NPE from map.put");
    }
}
