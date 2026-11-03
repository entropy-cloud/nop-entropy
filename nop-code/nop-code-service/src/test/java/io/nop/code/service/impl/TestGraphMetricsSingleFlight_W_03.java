package io.nop.code.service.impl;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graph.api.CommunityResult;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 6.3-W-03（plan 2530 Phase 2 组 B / 设计裁定②）materialize 收口单飞 + 失败退避冷却窗
 * 回归钉（TP-003 缺保护面随本体同 diff）。测试类落 impl 包（io.nop.code.service.impl，
 * TestShardBuildCoordinator 先例）；W_03 归属 6.3 单元（C7-001 读路径自愈单飞族）。
 *
 * <p>计数/失败注入点严格位于单飞门之下（R1 Major-2 不变式）：计数载体 = 既有包内可见
 * 下游方法 {@code communityRows}（门内计算体每执行恰调用一次）——代理子类覆写其计数/
 * 注入/驻留，禁覆写 materialize 本身（门宿主，门外计数与门语义脱钩）。
 *
 * <p>红③（TP-003 前半，latch 栅栏形态——TestConcurrentIndexing 先例，断言从并发冒烟升级
 * 为门下计数）：n=4 工作线程并发读同一未物化索引（entered 栅栏置于服务读入口）；release
 * 闩仅驻门内首入者（proxy 以 CAS 判首入：首调者计数后驻留计算体内，后续调用者仅计数即
 * 正常返回）→ 其余 3 请求完成后断言门内实际执行计数 == 1（pre-fix = 4——构造性当前即红；
 * pre-fix 非首入线程经计算体后在 replaceRows (indexId,metricType,symbolId) 唯一键竞争
 * 失败、经 detectCommunities 既有 catch 兜底完成为红跑预期路径——防误判 flake）。
 *
 * <p>红④（TP-003 后半）：失败注入点同在门内（communityRows 首调后每次抛既定
 * RuntimeException）→ 读 1 物化失败（调用方既有 catch 兜底）→ 读 2（冷却窗内，窗长经包内
 * setter 设定）→ 断言计数仍 == 1（窗内不重试；pre-fix = 2——构造性当前即红，顺序驱动零闩）。
 *
 * <p>row 6 兼容钉（评审 R1 可选项采纳，rule 23 接线验证面）：预占门（物化驻留门内）→
 * ShardBuildCoordinator.finalizeIfAllCompleted 发布路径 completeShard → 断言发布无异常
 * 完成（published=true，tryLock 非阻塞试探——结构上无死锁面）+ 门下执行计数不再增长
 * （发布侧 materialize skip 可观测）+ 收尾放行门内首入者。非 C10 缺保护面钉（不改变
 * 红③④ 映射），随红③④ 同 diff 落地。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestGraphMetricsSingleFlight_W_03 extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ICodeIndexService codeIndexService;

    /** 包内 CodeCacheManager 子类：按 indexId 注入纯内存夹具（零真实索引成本）。 */
    static class FixtureCacheManager extends CodeCacheManager {
        final java.util.Map<String, CallGraph> graphs = new java.util.HashMap<>();
        final java.util.Map<String, SymbolTable> tables = new java.util.HashMap<>();

        @Override
        CallGraph getOrRebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                        BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
            CallGraph fixture = graphs.get(indexId);
            return fixture != null ? fixture : super.getOrRebuildCallGraph(indexId, daoProvider, edgeConsumer);
        }

        @Override
        SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                            java.util.function.Function<io.nop.code.dao.entity.NopCodeSymbol, CodeSymbol> converter) {
            SymbolTable fixture = tables.get(indexId);
            return fixture != null ? fixture : super.getOrRebuildSymbolTable(indexId, daoProvider, converter);
        }
    }

    /**
     * 计数代理：communityRows 为单飞门之下既有包内可见下游方法（Major-2 不变式）。
     * releaseGate 非空时首入者驻留计算体内（模拟分钟级物化窗口）；failInjection 非空时
     * 每次调用抛出（红④ 门内失败注入）。
     */
    static class RecordingMaterializer extends GraphMetricMaterializer {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicBoolean firstEntered = new AtomicBoolean(false);
        volatile String parkedThread;
        volatile CountDownLatch releaseGate;
        volatile RuntimeException failInjection;

        RecordingMaterializer(IDaoProvider daoProvider, CodeCacheManager cacheManager,
                              ITransactionTemplate transactionTemplate, IOrmTemplate ormTemplate) {
            super(daoProvider, cacheManager, transactionTemplate, ormTemplate);
        }

        @Override
        List<NopCodeGraphMetric> communityRows(String indexId, CommunityResult result, long computedAt) {
            executions.incrementAndGet();
            RuntimeException failure = failInjection;
            if (failure != null) {
                throw failure;
            }
            CountDownLatch gate = releaseGate;
            if (gate != null && firstEntered.compareAndSet(false, true)) {
                // the first committer parks inside the computation body (gate holder);
                // later callers only count and return normally
                parkedThread = Thread.currentThread().getName();
                try {
                    if (!gate.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("w03 gate release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("w03 gate holder interrupted", e);
                }
            }
            return super.communityRows(indexId, result, computedAt);
        }
    }

    static CallGraph buildChain(int n) {
        CallGraph g = new CallGraph();
        for (int i = 0; i + 1 < n; i++) {
            g.addEdge("n" + i, "n" + (i + 1));
        }
        return g;
    }

    static SymbolTable symbolTable(int count) {
        SymbolTable table = new SymbolTable();
        for (int i = 0; i < count; i++) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId("sym" + i);
            symbol.setName("S" + i);
            symbol.setQualifiedName("demo.S" + i);
            table.add(symbol);
        }
        return table;
    }

    /** seedIndex 形态播种（TestShardBuildCoordinator.seedIndex 先例——公共 API 建索引行）。 */
    private void seedIndex(String indexId) {
        codeIndexService.indexFile(indexId, "app/Seed.java",
                "package app;\npublic class Seed { public void go() {} }\n");
    }

    /**
     * 红③：n=4 并发读同一未物化索引，门内实际执行计数 == 1（pre-fix = 4——构造性红）。
     */
    @Test
    void testW_03_concurrentReadsExecuteMaterializeBodyAtMostOnce() throws Exception {
        String indexId = "w03_singleflight";
        seedIndex(indexId);
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put(indexId, buildChain(5));
        cacheManager.tables.put(indexId, symbolTable(1));
        RecordingMaterializer materializer = new RecordingMaterializer(
                daoProvider, cacheManager, transactionTemplate, ormTemplate);
        materializer.releaseGate = new CountDownLatch(1);
        CodeGraphService service = new CodeGraphService(daoProvider, cacheManager, materializer);

        int n = 4;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch entered = new CountDownLatch(n);
        CountDownLatch done = new CountDownLatch(n);
        CountDownLatch nonFirstDone = new CountDownLatch(n - 1);
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < n; i++) {
                executor.submit(() -> {
                    entered.countDown();
                    try {
                        service.detectCommunities(indexId);
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        // the parked gate holder never completes until the main thread releases;
                        // every other request (gate-skipped post-fix, racing through pre-fix)
                        // counts down on completion
                        String parked = materializer.parkedThread;
                        if (!Thread.currentThread().getName().equals(parked)) {
                            nonFirstDone.countDown();
                        }
                        done.countDown();
                    }
                });
            }

            assertTrue(entered.await(30, TimeUnit.SECONDS), "all readers must reach the read entry");
            assertTrue(nonFirstDone.await(60, TimeUnit.SECONDS),
                    "non-first requests must complete without waiting for the parked body");

            assertEquals(1, materializer.executions.get(),
                    "single-flight: the computation body must execute at most once while the "
                            + "gate holder is parked (pre-fix = n = 4 - red)");
            assertEquals(0, errors.get(), "degraded reads must complete through the existing fallback");
        } finally {
            materializer.releaseGate.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "all readers must finish after release");
            executor.shutdownNow();
        }
        codeIndexService.deleteIndex(indexId);
    }

    /**
     * 红④：门内失败注入 → 冷却窗内的第二次读不重试物化（pre-fix = 2——构造性红，顺序驱动）。
     */
    @Test
    void testW_03_failureCooldownBlocksRetryWithinWindow() {
        String indexId = "w03_backoff";
        seedIndex(indexId);
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put(indexId, buildChain(5));
        cacheManager.tables.put(indexId, symbolTable(1));
        RecordingMaterializer materializer = new RecordingMaterializer(
                daoProvider, cacheManager, transactionTemplate, ormTemplate);
        materializer.failInjection = new RuntimeException("w03-probe-materialize-failure");
        materializer.setFailureCooldownMillis(30_000);
        CodeGraphService service = new CodeGraphService(daoProvider, cacheManager, materializer);

        // read 1: body fails under the gate -> caller's existing catch chain degrades
        service.detectCommunities(indexId);
        assertEquals(1, materializer.executions.get(), "read 1 enters the body exactly once");

        // read 2 within the cooldown window: must not retry the failed body
        service.detectCommunities(indexId);
        assertEquals(1, materializer.executions.get(),
                "a read inside the failure cooldown window must not retry the materialization "
                        + "body (pre-fix every read re-enters - red)");
    }

    /**
     * row 6 兼容钉：发布路径（completeShard → finalizeIfAllCompleted → materialize）在门被
     * 预占时无异常完成（tryLock 非阻塞——结构上无死锁面），发布侧 skip 可观测。
     */
    @Test
    void testW_03_row6_publishPathCompletesWhenGateOccupied() throws Exception {
        String indexId = "w03_row6";
        seedIndex(indexId);
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put(indexId, buildChain(5));
        cacheManager.tables.put(indexId, symbolTable(1));
        RecordingMaterializer materializer = new RecordingMaterializer(
                daoProvider, cacheManager, transactionTemplate, ormTemplate);
        materializer.releaseGate = new CountDownLatch(1);

        ShardBuildCoordinator coordinator = new ShardBuildCoordinator();
        coordinator.daoProvider = daoProvider;
        coordinator.ormTemplate = ormTemplate;
        coordinator.setMetricMaterializer(materializer);

        // pre-occupy the gate: the first committer parks inside the computation body
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> occupied = executor.submit(() -> materializer.materialize(indexId));
            long deadline = System.currentTimeMillis() + 30_000;
            while (materializer.executions.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(materializer.executions.get() >= 1, "the gate holder must have entered the body");

            coordinator.beginShardBuild(indexId, List.of("shard-0", "shard-1"));
            coordinator.completeShard(indexId, "shard-0");
            ShardBuildCoordinator.ShardBuildStatus status = coordinator.completeShard(indexId, "shard-1");

            assertTrue(status.isPublished(),
                    "publish must complete without exception while the gate is occupied "
                            + "(skip-and-degrade; final consistency per 裁定② row 6)");
            assertEquals(1, materializer.executions.get(),
                    "the publish path's materialize call must skip while the gate is held "
                            + "(structured WARN; the computation stays owned by the holder)");
            assertEquals(ShardBuildCoordinator.STATUS_COMPLETED, indexStatus(indexId),
                    "the status flip itself is unaffected by the gate skip");
        } finally {
            materializer.releaseGate.countDown();
            executor.shutdownNow();
        }
        codeIndexService.deleteIndex(indexId);
    }

    private String indexStatus(String indexId) {
        IEntityDao<NopCodeIndex> dao = daoProvider.daoFor(NopCodeIndex.class);
        return dao.getEntityById(indexId).getStatus();
    }
}
