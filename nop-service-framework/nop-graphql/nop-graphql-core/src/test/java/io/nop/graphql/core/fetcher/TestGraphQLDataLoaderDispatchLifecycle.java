/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * P1-2.6-004（plan 2376）：DataLoader 调度生命周期。
 * <p>
 * 缺陷：executor 的 dispatchAll 在 operation 发起时即执行（GraphQLExecutor :98/:120/:179）；
 * 异步 operation body 内注册批量 loader 并 await 结果时，注册发生在 dispatch 之后且
 * 递归链已断裂（注册表为空 → dispatchAll 返回 null）→ load future 永不完成 → 静默挂死。
 * 修复：新 loader 注册触发异步 dispatch 周期（requestDispatch → worker 池 → 请求 context
 * 绑定下 dispatchAll），周期末「注册表非空续调」直至静默。
 * <p>
 * 判别形态（探针 _tmp/fcr26-probe/DataLoaderDispatchProbe.java B 用例的自由形态）：
 * body 跑在独立线程（模拟异步 operation body）、无任何手动 dispatchAll——修复前 load
 * future 永不完成（3s 超时），修复后经调度周期完成。
 */
public class TestGraphQLDataLoaderDispatchLifecycle {

    private static class BatchCallRecord {
        final List<Object> keys;

        BatchCallRecord(List<Object> keys) {
            this.keys = keys;
        }
    }

    private static class RecordingFetcher implements BiFunction<Object[], IGraphQLExecutionContext, Object> {
        final List<BatchCallRecord> calls = new CopyOnWriteArrayList<>();

        @Override
        public Object apply(Object[] args, IGraphQLExecutionContext context) {
            calls.add(new BatchCallRecord(new ArrayList<>((List<Object>) args[0])));
            return args[0];
        }
    }

    private static IDataFetchingEnvironment env(IGraphQLExecutionContext context, Object source) {
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
                return true;
            }

            @Override
            public Object getArg(String name) {
                return null;
            }
        };
    }

    private static BeanMethodBatchFetcher newFetcher(IGraphQLExecutionContext context, RecordingFetcher fetcher) {
        List<Function<IDataFetchingEnvironment, Object>> argBuilders = List.of(env -> null);
        return new BeanMethodBatchFetcher("TestObj__items", fetcher, argBuilders, 0);
    }

    /**
     * 判别用例（挂死消除）：异步 body 线程注册 loader 并 await 结果，无手动 dispatchAll。
     * 修复前：注册错过 executor 的 dispatch、递归链已断 → load future 永不完成 → 3s 超时。
     * 修复后：注册触发调度周期 → dispatch → batch load 执行 → await 返回。
     */
    @Test
    public void testAsyncBodyAwaitingLoaderCompletesWithoutManualDispatch() throws Exception {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();
        BeanMethodBatchFetcher batchFetcher = newFetcher(context, fetcher);

        // 模拟异步 operation body：独立线程内注册 loader 并 await 结果（body 完成依赖 load 完成）
        CompletableFuture<Object> bodyDone = CompletableFuture.supplyAsync(() -> {
            try {
                return ((java.util.concurrent.CompletionStage<Object>) batchFetcher
                        .get(env(context, "src-async"))).toCompletableFuture().get(3, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException | TimeoutException
                     | InterruptedException e) {
                throw new IllegalStateException("load must complete via dispatch cycle", e);
            }
        });

        Object result = bodyDone.get(3, TimeUnit.SECONDS);
        assertEquals("src-async", result, "async body must receive the loaded value");

        // batch load 确实执行过（不是 load 永挂后被超时掩盖）
        assertEquals(1, fetcher.calls.size(), "dispatch cycle must run the batch loader exactly once");
        assertEquals(List.of("src-async"), fetcher.calls.get(0).keys);
    }

    /**
     * 批保护（键全覆盖不变式）：单线程同步 burst 50 次 get 同一 loader——burst 期间调度
     * 周期可能中途抢跑拆批（已知取舍），但全部 key 必须被装载且无重复/丢失，每个 source
     * 拿到正确值。批数只断言 ≥1（「恰 1 批」受竞态约束不可确定断言）。
     */
    @Test
    public void testSyncBurstAllKeysCoveredAndValuesCorrect() throws Exception {
        IGraphQLExecutionContext context = new GraphQLExecutionContext();
        RecordingFetcher fetcher = new RecordingFetcher();
        BeanMethodBatchFetcher batchFetcher = newFetcher(context, fetcher);

        int n = 50;
        List<CompletableFuture<Object>> loads = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String source = "src-" + i;
            CompletableFuture<Object> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return ((java.util.concurrent.CompletionStage<Object>) batchFetcher
                            .get(env(context, source))).toCompletableFuture().get(3, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException | TimeoutException
                         | InterruptedException e) {
                    throw new IllegalStateException("load must complete via dispatch cycle", e);
                }
            });
            loads.add(f);
        }

        for (int i = 0; i < n; i++) {
            Object result;
            try {
                result = loads.get(i).get(3, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("load #" + i + " must complete via dispatch cycle (P1-2.6-004 liveness)");
                return;
            }
            assertEquals("src-" + i, result, "each source must receive its own loaded value");
        }

        // 键全覆盖：全部批量调用的 keys 并集恰为 n 个、无重复
        Set<Object> allKeys = new HashSet<>();
        int totalKeys = 0;
        for (BatchCallRecord call : fetcher.calls) {
            allKeys.addAll(call.keys);
            totalKeys += call.keys.size();
        }
        assertEquals(n, allKeys.size(), "all " + n + " keys must be loaded (no loss)");
        assertEquals(n, totalKeys, "no key may be loaded twice");
        assertTrue(fetcher.calls.size() >= 1, "at least one batch call must have run");
    }
}
