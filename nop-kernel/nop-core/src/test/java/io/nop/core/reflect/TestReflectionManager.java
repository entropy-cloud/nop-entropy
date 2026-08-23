/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.convert.ITypeConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestReflectionManager {

    /**
     * 专用于本测试的类型，避免污染全局注册表
     */
    public static class MyConverterType {
    }

    @Test
    public void testUnregisterTypeConverter() {
        ReflectionManager mgr = ReflectionManager.instance();
        Class<?> type = MyConverterType.class;
        // 不能用IdentityTypeConverter.INSTANCE，它同时也是缺省兜底转换器
        ITypeConverter converter = (value, errorFactory) -> value;
        try {
            mgr.registerTypeConverter(type, converter);
            assertSame(converter, mgr.getConverterForJavaType(type));
        } finally {
            mgr.unregisterTypeConverter(type, converter);
        }
        // 注销后不应再命中已注销的转换器
        assertNotSame(converter, mgr.getConverterForJavaType(type));
    }

    /**
     * 反射模型较重的类，用于放大并发构建窗口
     */
    public static class ColdModel {
        public int f1(int a) { return a; }
        public int f2(int a) { return a; }
        public int f3(int a) { return a; }
        public int f4(int a) { return a; }
        public int f5(int a) { return a; }
        public int f6(int a) { return a; }
        public int f7(int a) { return a; }
        public int f8(int a) { return a; }
        public int f9(int a) { return a; }
        public int f10(int a) { return a; }
        public String g1(String a) { return a; }
        public String g2(String a) { return a; }
        public String g3(String a) { return a; }
        public String g4(String a) { return a; }
        public String g5(String a) { return a; }
        public String g6(String a) { return a; }
        public String g7(String a) { return a; }
        public String g8(String a) { return a; }
    }

    @Test
    public void testGetFromCacheConcurrentBuildOnlyOnce() throws Exception {
        ReflectionManager mgr = ReflectionManager.instance();
        // 预热无关：getFromCache 直接读取 introspectCache
        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<IClassModel>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return mgr.getFromCache(ColdModel.class);
                }));
            }
            IClassModel first = futures.get(0).get();
            for (Future<IClassModel> future : futures) {
                // 并发 miss 时不应重复构建出不同的 ClassModel 实例
                assertSame(first, future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
