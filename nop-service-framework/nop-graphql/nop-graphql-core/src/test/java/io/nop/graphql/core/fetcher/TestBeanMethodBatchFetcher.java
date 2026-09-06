/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import java.util.concurrent.CompletionException;
import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLExecutionContext;
import org.dataloader.DataLoaderFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BeanMethodBatchFetcher的两个并发/参数语义修复：
 * 1. 非source参数不同的调用各自成批装载，不再静默沿用首次调用捕获的参数；
 * 2. 并发分支同时首次注册同名loader时后到方复用先注册实例，不再抛ERR_GRAPHQL_DUPLICATED_LOADER。
 */
public class TestBeanMethodBatchFetcher {

    private static class BatchCallRecord {
        final Object[] args;
        final List<Object> keys;

        BatchCallRecord(Object[] args, List<Object> keys) {
            this.args = args;
            this.keys = keys;
        }
    }

    private static class RecordingFetcher implements BiFunction<Object[], IGraphQLExecutionContext, Object> {
        final List<BatchCallRecord> calls = new CopyOnWriteArrayList<>();

        @Override
        public Object apply(Object[] args, IGraphQLExecutionContext context) {
            calls.add(new BatchCallRecord(args.clone(), new ArrayList<>((List<Object>) args[0])));
            return args[0];
        }
    }

    static IDataFetchingEnvironment env(IGraphQLExecutionContext context, Object source, Object filter) {
        return new IDataFetchingEnvironment() {
            @Override
            public String getOperationName() {
                return "testOp";
            }

            @Override
            public Object getRoot() {
                return null;
            }

            @Override
            public Object getSource() {
                return source;
            }

            @Override
            public Object getOpRequest() {
                return null;
            }

            @Override
            public IDataFetchingEnvironment copy() {
                return this;
            }

            @Override
            public io.nop.graphql.core.ast.GraphQLFieldSelection getSelection() {
                return null;
            }

            @Override
            public io.nop.api.core.beans.FieldSelectionBean getSelectionBean() {
                return null;
            }

            @Override
            public IGraphQLExecutionContext getGraphQLExecutionContext() {
                return context;
            }

            @Override
            public boolean isAsync() {
                return false;
            }

            @Override
            public Object getArg(String name) {
                return filter;
            }
        };
    }

    /**
     * 同名loader字段在两个分支携带不同参数：修复前第二分支参数被静默丢弃，
     * 整批按第一分支参数计算（单批、filterA）；修复后各自成批、参数各得其所。
     */
    @Test
    public void testDifferentArgsGetSeparateBatches() {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();

        // 两个argBuilder：index 0为source占位，index 1为filter参数
        List<Function<IDataFetchingEnvironment, Object>> argBuilders = List.of(
                env -> null,
                env -> env.getArg("filter"));
        BeanMethodBatchFetcher batchFetcher = new BeanMethodBatchFetcher(
                "TestObj__items", fetcher, argBuilders, 0);

        batchFetcher.get(env(context, "src-1", "filterA"));
        batchFetcher.get(env(context, "src-2", "filterB"));

        FutureHelper.syncGet(context.dispatchAll());

        assertEquals(2, fetcher.calls.size(), "different args must be loaded in separate batches");
        BatchCallRecord callA = fetcher.calls.get(0);
        BatchCallRecord callB = fetcher.calls.get(1);
        assertEquals(List.of("src-1"), callA.keys);
        assertEquals("filterA", callA.args[1]);
        assertEquals(List.of("src-2"), callB.keys);
        assertEquals("filterB", callB.args[1]);
    }

    /**
     * 无额外参数时loaderName保持原值（兼容既有注册名语义）。
     */
    @Test
    public void testNoExtraArgsKeepsOriginalLoaderName() {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();
        List<Function<IDataFetchingEnvironment, Object>> argBuilders = List.of(env -> null);
        BeanMethodBatchFetcher batchFetcher = new BeanMethodBatchFetcher(
                "TestObj__items", fetcher, argBuilders, 0);

        batchFetcher.get(env(context, "src-1", null));
        assertNotNull(context.getDataLoader("TestObj__items"),
                "loader without extra args must be registered under the original loaderName");
    }

    /**
     * 并发分支同时首次命中同一loader（barrier在arg求值处汇合，确保双方都进入"注册前"窗口）：
     * 修复前双方都执行getDataLoader-null检查并先后registerDataLoader，
     * 后到方抛ERR_GRAPHQL_DUPLICATED_LOADER使整个请求失败；修复后先注册者胜出，双方共享同一实例。
     */
    @Test
    public void testConcurrentFirstRegistrationSharesLoader() throws Exception {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Function<IDataFetchingEnvironment, Object>> argBuilders = List.of(
                env -> null,
                env -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    return env.getArg("filter");
                });
        BeanMethodBatchFetcher batchFetcher = new BeanMethodBatchFetcher(
                "TestObj__items", fetcher, argBuilders, 0);

        List<Throwable> errors = new CopyOnWriteArrayList<>();
        Thread t1 = new Thread(() -> {
            try {
                batchFetcher.get(env(context, "src-1", "f"));
            } catch (Throwable e) {
                errors.add(e);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                batchFetcher.get(env(context, "src-2", "f"));
            } catch (Throwable e) {
                errors.add(e);
            }
        });
        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        assertEquals(List.of(), errors, "concurrent first registration must not fail the request");

        FutureHelper.syncGet(context.dispatchAll());
        assertEquals(1, fetcher.calls.size(), "both sources must share the single registered loader");
        List<Object> keys = fetcher.calls.get(0).keys;
        assertEquals(2, keys.size());
        assertTrue(keys.contains("src-1") && keys.contains("src-2"));
    }

    /**
     * 显式重复注册不同实例仍然是编程错误（保留registerDataLoader的守卫语义）。
     */
    @Test
    public void testSequentialDuplicateRegistrationStillRejected() {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        context.registerDataLoader("dup", DataLoaderFactory.newDataLoader(keys -> null));
        assertThrows(Exception.class, () -> context.registerDataLoader("dup",
                DataLoaderFactory.newDataLoader(keys -> null)));
    }

    /**
     * deepHashCode碰撞（"Aa"与"BB"的hashCode均为2112）的不同参数不得合并为一批：
     * 修复前仅以deepHashCode构造key，碰撞的两组参数共享同一loader，batchLoader闭包捕获
     * 首次注册的args数组，第二组参数被静默丢弃——整批按首组参数计算（错误数据）。
     */
    @Test
    public void testDeepHashCodeCollisionDoesNotMergeDifferentArgs() {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();

        List<Function<IDataFetchingEnvironment, Object>> argBuilders = List.of(
                env -> null,
                env -> env.getArg("filter"));
        BeanMethodBatchFetcher batchFetcher = new BeanMethodBatchFetcher(
                "TestObj__items", fetcher, argBuilders, 0);

        // "Aa".hashCode() == "BB".hashCode() == 2112
        assertEquals("Aa".hashCode(), "BB".hashCode(), "precondition: known hash collision pair");

        batchFetcher.get(env(context, "src-1", "Aa"));
        batchFetcher.get(env(context, "src-2", "BB"));

        FutureHelper.syncGet(context.dispatchAll());

        assertEquals(2, fetcher.calls.size(), "colliding-hash args must still be loaded in separate batches");
        assertEquals("Aa", fetcher.calls.get(0).args[1]);
        assertEquals("BB", fetcher.calls.get(1).args[1]);
    }
}
