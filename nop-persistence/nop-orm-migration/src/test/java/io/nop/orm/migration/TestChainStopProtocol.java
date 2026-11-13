/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.orm.OrmMigrationWriteGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红④（C6-003/C10-003/C10-010，裁定④）+ 红⑥（C7-001/C10-004，裁定⑥，硬前置裁定⑨）+
 * 红⑨（C3-008/C10-005，裁定⑨，rule 23 双入口接线）+ 红⑪（C7-002/C10-015，裁定⑪ fix-forward
 * 受控自环 + 谓词协同）。
 *
 * <p>新增断言即新钉：缺陷态红 → 修复后绿（红绿快照落 {@code _tmp/red-green-2518-*.log}）。
 * 既有钉（TestMigrationSwitchStopsChains 双钉 / TestIncrementalLagProbe 双钉 / TestMigrationPhase
 * 六钉 / 2507 双钉）零改写保持绿。</p>
 */
public class TestChainStopProtocol extends AbstractMigrationE2ETestCase {

    private static final String CONNECTOR = "stop-protocol-cdc";
    private volatile java.util.concurrent.atomic.AtomicBoolean unconfirmedRelease;

    @AfterEach
    void cleanup() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR + "-0");
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR + "-1");
        OrmMigrationWriteGate.setClosed(false);
    }

    // ------------------------------------------------------------------
    // 红④：停止协议（裁定④）
    // ------------------------------------------------------------------

    /** 不响应 cancel 的桩 source（仅测试 stop 原语的 join/interrupt 语义）。 */
    static final class StubCdcSource extends io.nop.stream.connector.debezium.DebeziumCdcSourceFunction {
        StubCdcSource() {
            super(new io.nop.message.debezium.DebeziumConfig());
        }

        @Override
        public void cancel() {
            // no-op：让线程存活状态完全由测试侧 latch 控制
        }
    }

    @Test
    public void testStopProtocol_BoundedJoinInterruptAndExitConfirmed() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                // 可中断等待：interrupt 后线程退出（常规路径）
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "chain-under-stop");
        thread.setDaemon(true);
        thread.start();
        IncrementalJobManager.RunningChainJob job = new IncrementalJobManager.RunningChainJob(
                "jn-confirm", new StubCdcSource(), CONNECTOR, thread);
        assertTrue(job.isAlive());

        job.stop(200);
        assertFalse(job.isAlive(), "interrupt must terminate the interruptible chain thread");
        assertTrue(job.isExitConfirmed(), "exit confirmation must hold after graceful interrupt exit");
    }

    @Test
    public void testStopProtocol_UnconfirmedExitMarksHandleAndRejectsReentry() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
        Thread thread = new Thread(() -> {
            // 不可中断等待（park + 吞中断标志——模拟 Debezium engine 半终止态）
            while (!released.get()) {
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                Thread.interrupted();
            }
        }, "chain-half-dead");
        unconfirmedRelease = released;
        thread.setDaemon(true);
        thread.start();
        IncrementalJobManager.RunningChainJob job = new IncrementalJobManager.RunningChainJob(
                "jn-unconfirmed", new StubCdcSource(), CONNECTOR, thread);

        job.stop(100);
        assertTrue(job.isAlive(), "uninterruptible chain thread must still be alive after grace join");
        assertFalse(job.isExitConfirmed(), "unconfirmed exit must be marked on the handle (M4)");

        // 裁定④⑪协同：重入 runIncremental 前对未确认退出链 gateFailed 拒绝（防半终止链双写）
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("ru1", job);
        insertIncrementalState("ru1");
        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runIncremental("ru1"));
            assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), e.getErrorCode());
            assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                            .contains("nop.orm.migration.chain-not-confirmed"),
                    "reentry must be rejected with chain-not-confirmed gate, got: "
                            + e.getParams().get(MigrationErrors.ARG_GATE));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
            unconfirmedRelease.set(true);
            java.util.concurrent.locks.LockSupport.unpark(thread);
        }
    }

    // ------------------------------------------------------------------
    // 红⑥：死链 fail 门禁（裁定⑥）+ 重启路径（M2）
    // ------------------------------------------------------------------

    @Test
    public void testChainFailureGate_RunVerifyRejectsDeadChain() {
        IncrementalJobManager.RunningChainJob deadChain = newChain("jn-dead", "chain-gate-cdc");
        deadChain.failure.set(new RuntimeException("binlog connection lost"));

        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("cg1", deadChain);
        insertIncrementalState("cg1");
        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("cg1"));
            assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), e.getErrorCode());
            assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                            .startsWith("incremental-chain-failed:"),
                    "dead chain must be consumed by the VERIFY gate (getFailure zero-caller reversal), got: "
                            + e.getParams().get(MigrationErrors.ARG_GATE));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testChainFailureGate_ManagerNullIncrementalPhaseRejectsReentry() {
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("cg2", null);
        insertIncrementalState("cg2");
        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("cg2"));
            assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                            .contains("incremental-manager-missing-restart-reentry"),
                    "restart path (manager==null, phase==INCREMENTAL) must reject VERIFY reentry, got: "
                            + e.getParams().get(MigrationErrors.ARG_GATE));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testChainFailureGate_ManagerNullNonIncrementalPhaseSkipsExplicitly() {
        // manager==null 且 phase 非 INCREMENTAL（无增量形态 FULL→VERIFY）：留痕跳过（非静默、
        // 非拒绝）——后续门禁（写门复核）继续执行并按既有语义失败
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("cg3", null);
        MigrationJobState state = insertStateWithPhase("cg3", MigrationPhase.FULL);
        assertTrue(state.getPositionP0() == null);
        OrmMigrationWriteGate.setClosed(false);
        NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("cg3"));
        assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                        .startsWith("write-gate must be closed"),
                "non-incremental path must skip the chain gate explicitly and hit the next gate, got: "
                        + e.getParams().get(MigrationErrors.ARG_GATE));
    }

    // ------------------------------------------------------------------
    // 红⑨：connector 键派生单点（裁定⑨）+ rule 23 双入口接线
    // ------------------------------------------------------------------

    @Test
    public void testConnectorKeyDerivation_SinglePointFunction() {
        assertEquals(List.of("mig-base"),
                IncrementalJobManager.deriveConnectorNames("mig-base", 1));
        assertEquals(Arrays.asList("mig-base-0", "mig-base-1"),
                IncrementalJobManager.deriveConnectorNames("mig-base", 2));
        assertEquals(Arrays.asList("mig-base-0", "mig-base-1", "mig-base-2"),
                IncrementalJobManager.deriveConnectorNames("mig-base", 3));
        assertTrue(IncrementalJobManager.deriveConnectorNames("mig-base", 0).isEmpty());
    }

    @Test
    public void testPerChainFingerprintObservable_UnderDerivedName() {
        // 缺陷态红判据：M=2 下按原始 base 名探测 → offsets 指纹恒空 → lag 门禁平凡通过；
        // 修复后：按链注册名（base-i）探测 → 指纹非空且前进可观测
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR + "-0");
        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(ByteBuffer.wrap("p".getBytes()), ByteBuffer.wrap("seeded".getBytes()));
        store.setOffsets(offsets);

        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2");
        String derivedFingerprint = probe.snapshot(CONNECTOR + "-0").offsetsFingerprint;
        String baseNameFingerprint = probe.snapshot("mig-unregistered-base").offsetsFingerprint;
        assertNotEquals("", derivedFingerprint,
                "fingerprint under the chain-registered (derived) name must be non-empty");
        assertEquals("", baseNameFingerprint,
                "fingerprint under the raw base name stays empty (the structural defect being fixed)");
        assertTrue(probe.awaitLagZeroChains(Arrays.asList(CONNECTOR + "-0", CONNECTOR + "-1"), 1),
                "static offsets + static ledger across both chains → lag=0");
    }

    @Test
    public void testRunVerifyConsumesPerChainConnectorNames() throws Exception {
        // rule 23 接线验证：runVerify 真实消费链句柄携带的派生名（"…-0"），而非原始 base 名——
        // 静默窗内推进 "…-0" 的 offset → 聚合判据必须抓住（base 名指纹恒空时将平凡通过）
        String derivedName = CONNECTOR + "-0";
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(derivedName);
        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(ByteBuffer.wrap("p".getBytes()), ByteBuffer.wrap("v0".getBytes()));
        store.setOffsets(offsets);

        IncrementalJobManager.RunningChainJob chain = newChain("jn-live", derivedName);
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("kv1", chain);
        MigrationJobConfig config = incrementalConfig();
        org.junit.jupiter.api.Assertions.assertEquals(CONNECTOR, config.getIncrementalConnectorName(),
                "config carries the raw base name; the chain carries the derived name");
        insertIncrementalState("kv1");
        OrmMigrationWriteGate.setClosed(true);
        Thread advancer = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<ByteBuffer, ByteBuffer> next = new LinkedHashMap<>();
            next.put(ByteBuffer.wrap("p".getBytes()), ByteBuffer.wrap("advanced".getBytes()));
            store.setOffsets(next);
        });
        advancer.setDaemon(true);
        advancer.start();
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("kv1"));
            assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                            .contains("lag != 0"),
                    "per-chain derived-name probe must observe the offset advance, got: "
                            + e.getParams().get(MigrationErrors.ARG_GATE));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
            advancer.join(5000);
        }
    }

    // ------------------------------------------------------------------
    // 红⑪：fix-forward 受控自环（裁定⑪）
    // ------------------------------------------------------------------

    @Test
    public void testFixForwardSelfLoop_ReentryAllowedWithinBound() {
        IncrementalJobManager.RunningChainJob healthy = newChain("jn-healthy", "self-loop-cdc");
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("ff1", healthy);
        insertIncrementalState("ff1");
        // INCREMENTAL 相位显式重入 = fix-forward 受控自环（上界 3）
        for (int i = 1; i <= 3; i++) {
            orchestrator.runIncremental("ff1");
            assertEquals(MigrationPhase.INCREMENTAL, orchestrator.getState("ff1").getPhase());
        }
        // 第 4 次重入超上界 → gateFailed 类型化拒绝
        NopException e = assertThrows(NopException.class, () -> orchestrator.runIncremental("ff1"));
        assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                        .contains("nop.orm.migration.fix-forward-retry-exhausted"),
                "self-loop retry beyond the bound must be rejected, got: "
                        + e.getParams().get(MigrationErrors.ARG_GATE));
    }

    @Test
    public void testSelfLoopWrite_FencingCooperativeWithPredicate() {
        // 裁定⑪ × 裁定⑤ 协同：自环写经期望相位谓词——竞写把相位推离自环相位时自环写显式失败
        IncrementalJobManager.RunningChainJob healthy = newChain("jn-ff", "self-loop-cdc");
        OrmMigrationOrchestrator orchestrator = selfLoopOrchestrator("ff2", healthy);
        insertIncrementalState("ff2");
        orchestrator.runIncremental("ff2");

        // 外部竞写：状态行相位被推离 INCREMENTAL
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("UPDATE nop_orm_migration_job SET phase='ABORT' WHERE job_id='ff2'").end());

        // 第二次自环重入：expectedPhase=INCREMENTAL 但行相位已是 ABORT → fencing 类型化失败
        assertThrows(NopException.class, () -> orchestrator.runIncremental("ff2"));
        assertEquals("ABORT", storePhase("ff2"), "self-loop write must not overwrite the competing state");
    }

    // ------------------------------------------------------------------
    // rule 22 端到端链一：编排全链（裁定④⑤⑥⑨⑪ + MA-04×MA-03 收敛点）
    // ------------------------------------------------------------------

    @Test
    public void testOrchestrationChainE2E_DeadChainGateToFixForwardToFencedSwitch() {
        // ① runIncremental（键派生清单登记）→ 链 failure 注入 → ② 死链 fail 门禁拒绝（裁定⑥）
        // → ③ fix-forward 受控自环重入（裁定⑪）→ ④ confirmSwitch 收敛停链（MA-03 收敛点，
        // stopAll 真实承载）→ ⑤ completeSwitch 版本谓词拒绝越权写（裁定⑤ fencing）
        String jobId = "e2e1";
        IncrementalJobManager.RunningChainJob chain = newChain("jn-e2e", CONNECTOR + "-0");
        RecordingStopAllManager manager = new RecordingStopAllManager(chain);
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                incrementalConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
        orchestrator.setIncrementalManager(manager);
        insertIncrementalState(jobId);

        // ① runIncremental 消费派生 connector 名清单（键派生清单登记：CONNECTOR-0）
        orchestrator.runIncremental(jobId);
        assertEquals(MigrationPhase.INCREMENTAL, orchestrator.getState(jobId).getPhase());

        // ② 链 failure 注入 → 死链 fail 门禁拒绝（getFailure 零调用方现状反转）
        chain.failure.set(new RuntimeException("e2e dead chain"));
        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException deadGate = assertThrows(NopException.class, () -> orchestrator.runVerify(jobId));
            assertTrue(String.valueOf(deadGate.getParams().get(MigrationErrors.ARG_GATE))
                    .startsWith("incremental-chain-failed:"));

            // ③ fix-forward：failure 清除后受控自环重入成功
            chain.failure.set(null);
            orchestrator.runIncremental(jobId);
            assertEquals(MigrationPhase.INCREMENTAL, orchestrator.getState(jobId).getPhase());

            // 推进 VERIFY+passed → confirmSwitch 收敛停链（stopAll 真实承载 + 计数证据）
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .sql("UPDATE nop_orm_migration_job SET phase='VERIFY', verify_result='passed'"
                            + " WHERE job_id='" + jobId + "'").end());
            orchestrator.confirmSwitch(jobId);
            assertTrue(manager.stopAllCount.get() >= 1,
                    "confirmSwitch convergence must stop chains via the real stopAll primitive (bounded join)");

            // ⑤ completeSwitch 收敛到 DONE（SWITCH 相位谓词写；越权写拒绝面由
            // TestMigrationJobStateStoreFencing 以确定性竞争窗覆盖）
            orchestrator.completeSwitch(jobId);
            assertEquals(MigrationPhase.DONE, orchestrator.getState(jobId).getPhase());
            assertFalse(OrmMigrationWriteGate.isClosed(), "gate must reopen at DONE");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    /** 记录 stopAll 调用并透传快照的桩管理器（④收敛点接线证据）。 */
    static final class RecordingStopAllManager extends IncrementalJobManager {
        final java.util.concurrent.atomic.AtomicInteger stopAllCount =
                new java.util.concurrent.atomic.AtomicInteger();
        private final List<RunningChainJob> chains;

        RecordingStopAllManager(RunningChainJob... chains) {
            super(null, target -> null, 1_000L);
            this.chains = Arrays.asList(chains);
        }

        @Override
        public List<RunningChainJob> snapshotRunningJobs() {
            return chains;
        }

        @Override
        public List<RunningChainJob> startFallbackChains(String jobId, MigrationJobConfig config,
                                                         SourcePosition positionP0) {
            return chains;
        }

        @Override
        public void stopAll() {
            stopAllCount.incrementAndGet();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private MigrationJobConfig incrementalConfig() {
        java.util.Map<String, Object> targetStrategy = AbstractMigrationE2ETestCase.Json.map(
                "strategies", AbstractMigrationE2ETestCase.Json.map("order-mod",
                        AbstractMigrationE2ETestCase.Json.map("type", "mod", "shard-count", 2,
                                "entities", List.of("mig.entity.MigOrder"))));
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(targetStrategy)
                .verify(true, 1.0, false)
                .quiesceSeconds(1)
                .incremental(CONNECTOR)
                .build();
    }

    private OrmMigrationOrchestrator selfLoopOrchestrator(String jobId,
                                                          IncrementalJobManager.RunningChainJob chain) {
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                incrementalConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
        if (chain != null)
            orchestrator.setIncrementalManager(new StubIncrementalManager(chain));
        // chain == null：不注入管理器 = 编排器重启后字段为空的语义（restart-reentry 路径）
        return orchestrator;
    }

    /** 桩管理器：返回预置链快照，startFallbackChains 为空操作（本测试只驱动编排器门禁面）。 */
    static final class StubIncrementalManager extends IncrementalJobManager {
        private final List<RunningChainJob> chains;

        StubIncrementalManager(RunningChainJob... chains) {
            super(null, target -> null, 1_000L);
            this.chains = Arrays.asList(chains);
        }

        @Override
        public List<RunningChainJob> snapshotRunningJobs() {
            return chains;
        }

        @Override
        public List<RunningChainJob> startFallbackChains(String jobId, MigrationJobConfig config,
                                                         SourcePosition positionP0) {
            return chains;
        }

        @Override
        public void stopAll() {
            // no-op stub
        }
    }

    private IncrementalJobManager.RunningChainJob newChain(String jobName, String connectorName) {
        Thread thread = new Thread(() -> {
        }, jobName);
        return new IncrementalJobManager.RunningChainJob(jobName, new StubCdcSource(), connectorName,
                thread);
    }

    private void insertIncrementalState(String jobId) {
        insertStateWithPhase(jobId, MigrationPhase.INCREMENTAL);
    }

    private MigrationJobState insertStateWithPhase(String jobId, MigrationPhase phase) {
        MigrationJobState state = new MigrationJobState();
        state.setJobId(jobId);
        state.setPhase(phase);
        state.setScenario("rescale");
        state.setEntityName("mig.entity.MigOrder");
        state.setCreateTimeMillis(io.nop.api.core.time.CoreMetrics.currentTimeMillis());
        state.setUpdateTimeMillis(state.getCreateTimeMillis());
        if (phase == MigrationPhase.INCREMENTAL)
            state.setPositionP0(SourcePosition.mysql("log.bin", 1, "").toJson());
        new MigrationJobStateStore(jdbcTemplate, "").insert(state);
        return state;
    }

    private String storePhase(String jobId) {
        MigrationJobState state = new MigrationJobStateStore(jdbcTemplate, "").load(jobId);
        return state == null ? null : state.getPhase().name();
    }
}
