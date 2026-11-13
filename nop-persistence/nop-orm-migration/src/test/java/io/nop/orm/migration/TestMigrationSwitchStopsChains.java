/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.time.CoreMetrics;
import io.nop.orm.OrmMigrationWriteGate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归（CRA WI 2.5 C6-002/MA-03，P0）：搬迁成功收敛路径必须停止增量链作业——
 * confirmSwitch（SWITCH 收敛：翻转+路由重建完成）即停链，completeSwitch（开门前）兜底幂等停链。
 * 修复前成功路径从不调用 stopAll（全模块唯一调用点在 abort），DONE 后链线程/binlog 连接
 * 永久滞留，且写门只拦本 JVM DML，源库域外写入经存活链持续回写已晋级的线上目标表。
 *
 * <p>链生命周期本身由 {@link IncrementalJobManager} 管辖（其测试注入 seam 见
 * ITargetSinkFactory）；本测试只验证编排器成功收敛路径的停链调用契约——直接落
 * VERIFY+passed 状态行后驱动 confirm/complete，观测注入管理器的 stopAll 调用。</p>
 */
public class TestMigrationSwitchStopsChains extends AbstractMigrationE2ETestCase {

    /**
     * 记录 stopAll 调用次数的桩管理器（构造参数不参与本测试路径——只验证编排器侧调用）。
     */
    static final class RecordingIncrementalManager extends IncrementalJobManager {
        final AtomicInteger stopAllCount = new AtomicInteger();

        RecordingIncrementalManager() {
            super(null, target -> null, 1_000L);
        }

        @Override
        public void stopAll() {
            stopAllCount.incrementAndGet();
        }
    }

    private MigrationJobConfig incrementalRescaleConfig() {
        java.util.Map<String, Object> targetStrategy = Json.map(
                "strategies", Json.map("order-mod",
                        Json.map("type", "mod", "shard-count", 2,
                                "entities", List.of("mig.entity.MigOrder"))));
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(targetStrategy)
                .verify(true, 1.0, false)
                .quiesceSeconds(1)
                .incremental("mig-test-connector")
                .build();
    }

    private void insertVerifyPassedState(String jobId) {
        MigrationJobState state = new MigrationJobState();
        state.setJobId(jobId);
        state.setPhase(MigrationPhase.VERIFY);
        state.setScenario("rescale");
        state.setEntityName("mig.entity.MigOrder");
        state.setCreateTimeMillis(CoreMetrics.currentTimeMillis());
        state.setUpdateTimeMillis(state.getCreateTimeMillis());
        state.setVerifyResult("passed");
        new MigrationJobStateStore(jdbcTemplate, "").insert(state);
    }

    @Test
    public void testSwitchConvergenceStopsIncrementalChains() {
        RecordingIncrementalManager manager = new RecordingIncrementalManager();
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                incrementalRescaleConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
        orchestrator.setIncrementalManager(manager);
        insertVerifyPassedState("js1");

        assertEquals(0, manager.stopAllCount.get(), "no stop before convergence");
        OrmMigrationWriteGate.setClosed(true);
        try {
            // SWITCH 收敛（翻转 + 路由重建完成）即停链——幽灵写窗口不得跨越收敛点
            orchestrator.confirmSwitch("js1");
            assertEquals(1, manager.stopAllCount.get(),
                    "confirmSwitch convergence must stop incremental chains (C6-002/MA-03)");
            assertEquals(MigrationPhase.SWITCH, orchestrator.getState("js1").getPhase());

            // 开门前兜底停链（幂等；覆盖中断重启后直接从 SWITCH 收敛到 DONE 的路径）
            orchestrator.completeSwitch("js1");
            assertTrue(manager.stopAllCount.get() >= 1,
                    "completeSwitch must keep chains stopped before reopening the gate");
            assertEquals(MigrationPhase.DONE, orchestrator.getState("js1").getPhase());
            assertFalse(OrmMigrationWriteGate.isClosed(), "gate must reopen at DONE");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testNonIncrementalConfigNeverTouchesManager() {
        RecordingIncrementalManager manager = new RecordingIncrementalManager();
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
        orchestrator.setIncrementalManager(manager);
        insertVerifyPassedState("js2");

        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.confirmSwitch("js2");
            orchestrator.completeSwitch("js2");
            assertEquals(0, manager.stopAllCount.get(),
                    "non-incremental job has no chains: stop path must stay dormant");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }
}
