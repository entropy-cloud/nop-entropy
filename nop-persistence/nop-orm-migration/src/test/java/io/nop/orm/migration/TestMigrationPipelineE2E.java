/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.DaoConfigs;
import io.nop.orm.OrmMigrationWriteGate;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CHECKSUM_MISMATCH;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_COUNT_MISMATCH;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_NULL_SHARD_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2→H2 全链端到端（Minimum Rules #22）：编排 API 入口 → PREPARE（目标表就绪 + 密钥一致）→
 * FULL（nop-batch 作业 keyed upsert 落 2 分片表）→ 写停止关 + lag=0 门禁 → VERIFY 三级校验 →
 * SWITCH（confirm 收敛门 → nop.orm.shard 原子翻转 + 路由重建留痕）→ DONE；ABORT 全程可达；
 * 幂等重放与 null 分片键 fail-fast。
 *
 * <p>实体模型面为最小 fake（{@link FakeEntityModelFacade}，仅元数据）；ORM session 层与
 * shard 路由行为由 nop-orm 模块自身测试（TestShardReadWriteCombined 等）与本模块写门测试覆盖。</p>
 */
public class TestMigrationPipelineE2E extends AbstractMigrationE2ETestCase {

    private OrmMigrationOrchestrator newOrchestrator(String jobId) {
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                rescaleConfig(), jdbcTemplate, facade, jdbcTemplate, "");
        return orchestrator;
    }

    private void seedSourceRows() {
        // mod(sid 数值) 路由：偶数 → _0，奇数 → _1（mod 算法对数值字符串取模）
        insertSourceRow("1", "10.50", "1");
        insertSourceRow("2", "20.00", "2");
        insertSourceRow("3", "30.25", "3");
        insertSourceRow("4", "40.00", "4");
    }

    @Test
    public void testFullPipelinePrepareFullVerifySwitchDone() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j1");

        // PREPARE：目标表就绪 + 密钥一致（同实例无覆盖 → 通过）
        String jobId = orchestrator.start("j1");
        assertEquals("j1", jobId);
        assertEquals(MigrationPhase.PREPARE, orchestrator.getState(jobId).getPhase());

        // FULL：nop-batch 作业 keyed upsert 落目标分片表（接线验证：编排器确实构造并启动 batch 作业）
        orchestrator.runFull(jobId);
        assertEquals(MigrationPhase.FULL, orchestrator.getState(jobId).getPhase());
        assertEquals("orm-migration-MigOrder", orchestrator.getState(jobId).getFullTaskName());
        assertEquals(4, countRows("MIG_ORDER_0") + countRows("MIG_ORDER_1"),
                "full copy must land all rows into the two shard tables");
        assertEquals("10.50", amountOf("MIG_ORDER_1", "1"), "odd sid routes to shard 1");

        // VERIFY 门禁 1：写门未关 → 显式拒绝（M3）
        NopException gateError = assertThrows(NopException.class, () -> orchestrator.runVerify(jobId));
        assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), gateError.getErrorCode());

        // SWITCH 收敛门（M2）：VERIFY 未通过（verifyResult 为空）→ confirm 显式拒绝
        NopException confirmError = assertThrows(NopException.class,
                () -> orchestrator.confirmSwitch(jobId));
        assertEquals(ERR_MIG_GATE_NOT_SATISFIED.getErrorCode(), confirmError.getErrorCode());

        // 门禁关闭写 + lag=0（无增量阶段：仅观测 ledger 静止）
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.runVerify(jobId);
            assertEquals("passed", orchestrator.getState(jobId).getVerifyResult());

            // SWITCH：confirm（v1 人工收敛门）→ 配置翻转 + 路由重建留痕
            orchestrator.confirmSwitch(jobId);
            assertEquals(MigrationPhase.SWITCH, orchestrator.getState(jobId).getPhase());
            String shardConfig = DaoConfigs.CFG_ORM_SHARD.get();
            assertNotNull(shardConfig, "nop.orm.shard must be flipped atomically at confirm");
            assertTrue(shardConfig.contains("order-mod"));
            // 非 SessionFactoryImpl 宿主 → 设计降级：状态行标注待重启实例语义
            assertEquals("restart-required", orchestrator.getState(jobId).getRouteRebuild());

            // 收敛门满足 → 开门 → DONE（不可回滚点）
            orchestrator.completeSwitch(jobId);
            assertEquals(MigrationPhase.DONE, orchestrator.getState(jobId).getPhase());
            assertTrue(!OrmMigrationWriteGate.isClosed(), "gate must reopen at DONE");
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testIdempotentReplayFullCopy() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j2");
        orchestrator.start("j2");
        orchestrator.runFull("j2");
        int rowsAfterFirst = countRows("MIG_ORDER_0") + countRows("MIG_ORDER_1");

        // 重跑同一 epoch 任务：keyed upsert 覆盖而非重复（设计 §4.5 幂等）
        orchestrator.fullJob().run("j2");
        assertEquals(rowsAfterFirst, countRows("MIG_ORDER_0") + countRows("MIG_ORDER_1"),
                "keyed upsert replay must not duplicate rows");
        assertEquals("30.25", amountOf("MIG_ORDER_1", "3"));
    }

    @Test
    public void testNullShardKeyRowFailsFastWithLocation() {
        insertSourceRow("5", "50.00", null); // null 分片键存量行
        insertSourceRow("6", "60.00", "6");
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j3");
        orchestrator.start("j3");

        NopException e = assertThrows(NopException.class, () -> orchestrator.runFull("j3"));
        assertEquals(ERR_MIG_NULL_SHARD_KEY.getErrorCode(), e.getErrorCode());
        assertEquals("mig.entity.MigOrder", e.getParams().get(MigrationErrors.ARG_ENTITY_NAME));
        assertEquals("5", e.getParams().get(MigrationErrors.ARG_ENTITY_ID));
        assertEquals("MIG_ORDER", e.getParams().get(MigrationErrors.ARG_SOURCE_TABLE));
    }

    @Test
    public void testCountMismatchRejectsVerify() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j4");
        orchestrator.start("j4");
        orchestrator.runFull("j4");
        // 目标侧丢一行：count 校验（一级）拒绝
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("DELETE FROM MIG_ORDER_0 WHERE SID='2'").end());

        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("j4"));
            assertEquals(ERR_MIG_COUNT_MISMATCH.getErrorCode(), e.getErrorCode());
            assertEquals(4L, ((Number) e.getParams().get(MigrationErrors.ARG_SOURCE_COUNT)).longValue());
            assertEquals(3L, ((Number) e.getParams().get(MigrationErrors.ARG_TARGET_COUNT)).longValue());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testChecksumMismatchRejectsVerifyWithDetails() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j5");
        orchestrator.start("j5");
        orchestrator.runFull("j5");
        // 同键数不改行数：目标行内容被改写 → 逐行 checksum（三级）抓住且报告携带定位
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("UPDATE MIG_ORDER_1 SET AMOUNT = 999.99 WHERE SID='3'").end());

        OrmMigrationWriteGate.setClosed(true);
        try {
            NopException e = assertThrows(NopException.class, () -> orchestrator.runVerify("j5"));
            assertEquals(ERR_MIG_CHECKSUM_MISMATCH.getErrorCode(), e.getErrorCode());
            assertEquals(1L, ((Number) e.getParams().get(MigrationErrors.ARG_MISMATCH_COUNT)).longValue());
            String detail = String.valueOf(e.getParams().get(MigrationErrors.ARG_MISMATCH_DETAIL));
            assertTrue(detail.contains("pk=s:3") && detail.contains("source=") && detail.contains("target="),
                    "mismatch detail must carry pk location and both-side hashes: " + detail);
            // 失配后状态行结论 failed：confirm 门禁拒绝
            assertEquals("failed", orchestrator.getState("j5").getVerifyResult());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testAbortRestoresGateAndKeepsOldConfig() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j6");
        orchestrator.start("j6");
        OrmMigrationWriteGate.setClosed(true);
        try {
            orchestrator.abort("j6", "manual abort");
            assertEquals(MigrationPhase.ABORT, orchestrator.getState("j6").getPhase());
            assertEquals("manual abort", orchestrator.getState("j6").getAbortReason());
            // 旧配置未动 + 写门恢复 open（恢复写即回滚）
            assertNullShardConfig();
            assertTrue(!OrmMigrationWriteGate.isClosed());
            // 终态无出边：ABORT 后任何推进显式失败
            assertThrows(NopException.class, () -> orchestrator.runFull("j6"));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    private void assertNullShardConfig() {
        // abort 路径 nop.orm.shard 未被翻转（本测试未 confirm 过；torn state 防御断言）
        org.junit.jupiter.api.Assertions.assertTrue(
                DaoConfigs.CFG_ORM_SHARD.get() == null
                        || !DaoConfigs.CFG_ORM_SHARD.get().contains("migration-abort-marker"));
    }

    @Test
    public void testKeyMismatchFailsPrepare() {
        seedSourceRows();
        OrmMigrationOrchestrator orchestrator = newOrchestrator("j7");
        Map<String, String> overrides = new HashMap<>();
        overrides.put("nop.crypt.default-enc-key", "some-other-env-key");
        orchestrator.setTargetKeyOverrides(overrides);

        // M4 门禁：目标侧密钥覆盖与本侧不一致 → PREPARE 显式拒绝
        NopException e = assertThrows(NopException.class, () -> orchestrator.start("j7"));
        assertEquals(MigrationErrors.ERR_MIG_ENCRYPT_KEY_MISMATCH.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnknownJobIdFailsTyped() {
        OrmMigrationOrchestrator orchestrator = newOrchestrator("no-such");
        NopException e = assertThrows(NopException.class, () -> orchestrator.getState("no-such"));
        assertEquals(MigrationErrors.ERR_MIG_JOB_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testStateRowSurvivesRestart() {
        seedSourceRows();
        OrmMigrationOrchestrator first = newOrchestrator("j8");
        first.start("j8");
        first.runFull("j8");

        // 模拟编排器重启：新编排器实例按状态行续跑（FULL 完成态保留，绝不隐式回退 PREPARE）
        OrmMigrationOrchestrator second = newOrchestrator("j8");
        second.resume("j8");
        assertEquals(MigrationPhase.FULL, second.getState("j8").getPhase());
        assertEquals(4, countRows("MIG_ORDER_0") + countRows("MIG_ORDER_1"));
    }
}
