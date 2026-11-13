/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.executor.IChangeExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C1-002（plan 2554 裁定 F）：迁移引擎注册面并发化载体。
 * 行为钉：N 线程并发 registerExecutor 全部可见（裸 HashMap 缺陷态丢失更新 → 红）。
 * 结构钉：MigrationEngine 注册面为 ConcurrentHashMap；MigrationExecutor 注册表 final 化。
 */
public class TestMigrationEngineRegistry {

    private static IChangeExecutor stubExecutor() {
        return new IChangeExecutor() {
            @Override
            public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
                // no-op stub
            }
        };
    }

    private static Map<String, IChangeExecutor> changeExecutorsOf(MigrationEngine engine) throws Exception {
        Field field = MigrationEngine.class.getDeclaredField("changeExecutors");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, IChangeExecutor> map = (Map<String, IChangeExecutor>) field.get(engine);
        return map;
    }

    @Test
    public void testConcurrentRegisterExecutorAllVisible() throws Exception {
        MigrationEngine engine = new MigrationEngine();
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        engine.registerExecutor("change_" + tid + "_" + i, stubExecutor());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Map<String, IChangeExecutor> executors = changeExecutorsOf(engine);
        int defaultExecutorCount = 16; // registerDefaultExecutors 注册的缺省变更执行器数（含 DbTypeFilterExecutor）
        assertEquals(threads * perThread, executors.size() - defaultExecutorCount,
                "并发注册不得丢失更新（裸 HashMap 缺陷态在此红）");
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                assertTrue(executors.containsKey("change_" + t + "_" + i));
            }
        }
    }

    @Test
    public void testRegistriesAreConcurrentHashMaps() throws Exception {
        MigrationEngine engine = new MigrationEngine();

        Field changeExecutors = MigrationEngine.class.getDeclaredField("changeExecutors");
        changeExecutors.setAccessible(true);
        assertEquals(ConcurrentHashMap.class, changeExecutors.get(engine).getClass(),
                "changeExecutors 必须为 ConcurrentHashMap（2.5-C1-002 结构钉）");

        Field preconditionCheckers = MigrationEngine.class.getDeclaredField("preconditionCheckers");
        preconditionCheckers.setAccessible(true);
        assertEquals(ConcurrentHashMap.class, preconditionCheckers.get(engine).getClass(),
                "preconditionCheckers 必须为 ConcurrentHashMap（2.5-C1-002 结构钉）");

        Field executorField = MigrationExecutor.class.getDeclaredField("executors");
        executorField.setAccessible(true);
        assertEquals(ConcurrentHashMap.class, executorField.get(new MigrationExecutor()).getClass(),
                "MigrationExecutor 注册表必须为 ConcurrentHashMap（2.5-C1-002 结构钉）");
        assertTrue(Modifier.isFinal(executorField.getModifiers()),
                "MigrationExecutor.executors 必须 final 化（2.5-C1-002 结构钉）");
    }
}
