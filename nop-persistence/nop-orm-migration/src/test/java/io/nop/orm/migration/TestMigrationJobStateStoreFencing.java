/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.orm.OrmMigrationWriteGate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import java.util.List;

import static io.nop.orm.migration.MigrationErrors.ARG_ACTUAL_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_EXPECTED_PHASE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_JOB_NOT_FOUND;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_JOB_STATE_FENCING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红⑤（C7-003/C10-011，裁定⑤）：状态行期望相位谓词 + 跨进程 fencing。
 * 缺陷态红判据：全列覆盖 UPDATE 无谓词——abort×confirmSwitch 并发时败者静默覆盖胜者终态
 * （VERIFY→SWITCH 写覆盖 ABORT 行，终态背离）；修复后：updated==0 经二次 load 消歧 →
 * 行缺失 = JOB_NOT_FOUND、相位偏离 = fencing 类型化拒绝（含期望/实际相位参数）。
 */
public class TestMigrationJobStateStoreFencing extends AbstractMigrationE2ETestCase {

    private OrmMigrationOrchestrator newOrchestrator(MigrationJobConfig config, String jobId) {
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                config, jdbcTemplate, entityFacade, jdbcTemplate, "");
        orchestrator.setIncrementalManager(new StubGateManager());
        return orchestrator;
    }

    private MigrationJobConfig rescaleIncrementalConfig() {
        java.util.Map<String, Object> targetStrategy = AbstractMigrationE2ETestCase.Json.map(
                "strategies", AbstractMigrationE2ETestCase.Json.map("order-mod",
                        AbstractMigrationE2ETestCase.Json.map("type", "mod", "shard-count", 2,
                                "entities", List.of("mig.entity.MigOrder"))));
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(targetStrategy)
                .verify(true, 1.0, false)
                .quiesceSeconds(1)
                .incremental("fencing-cdc")
                .build();
    }

    private void insertVerifyPassedState(String jobId) {
        MigrationJobState state = new MigrationJobState();
        state.setJobId(jobId);
        state.setPhase(MigrationPhase.VERIFY);
        state.setScenario("rescale");
        state.setEntityName("mig.entity.MigOrder");
        state.setVerifyResult("passed");
        state.setCreateTimeMillis(CoreMetrics.currentTimeMillis());
        state.setUpdateTimeMillis(state.getCreateTimeMillis());
        new MigrationJobStateStore(jdbcTemplate, "").insert(state);
    }

    private String storePhase(String jobId) {
        MigrationJobState state = new MigrationJobStateStore(jdbcTemplate, "").load(jobId);
        return state == null ? null : state.getPhase().name();
    }

    @Test
    public void testFencing_CompetingPhaseWriteRejected() throws Exception {
        // 场景 = abort×completeSwitch 背离终态的确定性等价：completeSwitch 持有 SWITCH 相位
        // 快照进入单飞域（阻塞在 stopAll），外部并发写（abort 落账 ABORT）先改行相位；
        // 恢复后的收尾写带期望相位谓词 → updated==0 → 二次 load 消歧 → fencing 类型化拒绝
        OrmMigrationOrchestrator completeSide = newOrchestrator(rescaleIncrementalConfig(), "fx1");
        OrmMigrationOrchestrator abortSide = newOrchestrator(rescaleIncrementalConfig(), "fx1");
        insertVerifyPassedState("fx1");
        OrmMigrationWriteGate.setClosed(true);
        // 先把行推进到 SWITCH（直接落状态行，跳过 confirm 的翻转面）
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("UPDATE nop_orm_migration_job SET phase='SWITCH' WHERE job_id='fx1'").end());

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        completeSide.setIncrementalManager(new BlockingStopManager(entered, release));
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread completer = new Thread(() -> {
            try {
                completeSide.completeSwitch("fx1");
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        completer.start();
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        try {
            // 竞争写（abort 语义）：completeSwitch 阻塞在收敛点期间，行相位被推离 SWITCH
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .sql("UPDATE nop_orm_migration_job SET phase='ABORT', abort_reason='competing abort'"
                            + " WHERE job_id='fx1'").end());
        } finally {
            release.countDown();
            completer.join(10000);
            OrmMigrationWriteGate.setClosed(false);
        }
        // 缺陷态：completeSwitch 全列覆盖写把 ABORT 行静默覆盖回 DONE（终态背离 + 幽灵开门）
        // 修复后：期望相位谓词命中 → updated==0 → 二次 load 消歧 → fencing 类型化拒绝
        assertTrue(failure.get() instanceof NopException,
                "the losing write must fail typed, got: " + failure.get());
        NopException e = (NopException) failure.get();
        assertEquals(ERR_MIG_JOB_STATE_FENCING.getErrorCode(), e.getErrorCode());
        assertEquals("SWITCH", e.getParams().get(ARG_EXPECTED_PHASE));
        assertEquals("ABORT", e.getParams().get(ARG_ACTUAL_PHASE));
        assertEquals("ABORT", storePhase("fx1"),
                "the winning competing state must not be overwritten by the losing writer");
        // abort 语义自身仍可达（竞争未被吞）：以独立入口显式落账
        abortSide.abort("fx1", "competing abort");
        assertEquals("ABORT", storePhase("fx1"));
    }

    @Test
    public void testFencing_RowMissingAfterPredicateFailure() {
        OrmMigrationOrchestrator confirmSide = newOrchestrator(rescaleIncrementalConfig(), "fx2");
        insertVerifyPassedState("fx2");
        OrmMigrationWriteGate.setClosed(true);
        try {
            // 行在谓词窗口内被删除（外部清理/竞态）→ 二次 load 消歧 → JOB_NOT_FOUND 语义
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .sql("DELETE FROM nop_orm_migration_job WHERE job_id='fx2'").end());
            NopException e = assertThrows(NopException.class,
                    () -> confirmSide.confirmSwitch("fx2"));
            assertEquals(ERR_MIG_JOB_NOT_FOUND.getErrorCode(), e.getErrorCode());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testFencing_SamePhaseWritesStillPass() {
        // 同相位窗口内的合法写（verifyResult 落账等）不受谓词影响
        OrmMigrationOrchestrator orchestrator = newOrchestrator(rescaleConfig(), "fx3");
        insertVerifyPassedState("fx3");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.confirmSwitch("fx3");
            assertEquals(MigrationPhase.SWITCH, orchestrator.getState("fx3").getPhase());
            orchestrator.completeSwitch("fx3");
            assertEquals(MigrationPhase.DONE, orchestrator.getState("fx3").getPhase());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testSingleFlight_ConcurrentEntryRejected() throws Exception {
        OrmMigrationOrchestrator orchestrator = newOrchestrator(rescaleIncrementalConfig(), "fx4");
        insertVerifyPassedState("fx4");
        OrmMigrationWriteGate.setClosed(true);
        // 确定性并发：胜者阻塞在 stopAll（confirmSwitch 收敛点），单飞域被占住
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        orchestrator.setIncrementalManager(new BlockingStopManager(winnerEntered, release));
        Thread winner = new Thread(() -> orchestrator.confirmSwitch("fx4"));
        winner.start();
        assertTrue(winnerEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        try {
            // 同 jobId 两入口并发：败者类型化拒绝（running-jobs-reentry），胜者正常收敛
            NopException e = assertThrows(NopException.class,
                    () -> orchestrator.completeSwitch("fx4"));
            assertTrue(String.valueOf(e.getParams().get(MigrationErrors.ARG_GATE))
                            .contains("nop.orm.migration.running-jobs-reentry"),
                    "the losing entry must be rejected by the per-jobId single-flight, got: "
                            + e.getParams().get(MigrationErrors.ARG_GATE));
        } finally {
            release.countDown();
            winner.join(10000);
            OrmMigrationWriteGate.setClosed(false);
        }
        assertEquals("SWITCH", storePhase("fx4"));
    }

    /** 空转桩管理器（隔离增量面）。 */
    static final class StubGateManager extends IncrementalJobManager {
        StubGateManager() {
            super(null, target -> null, 1_000L);
        }

        @Override
        public void stopAll() {
            // no-op stub
        }
    }

    /** stopAll 阻塞桩：确定性占住单飞域。 */
    static final class BlockingStopManager extends IncrementalJobManager {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        BlockingStopManager(CountDownLatch entered, CountDownLatch release) {
            super(null, target -> null, 1_000L);
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void stopAll() {
            entered.countDown();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
