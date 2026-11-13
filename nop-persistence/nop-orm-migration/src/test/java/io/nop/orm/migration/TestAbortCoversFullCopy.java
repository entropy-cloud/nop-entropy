/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.dao.DaoConfigs;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 2.5-C7-006（plan 2554 裁定 K）载体：abort 覆盖 FULL 期——doAbort 对在飞全量批任务置
 * 停表标志 + cancel 在飞 chunk context（cancel(STOP) → BatchTask chunk 循环抛
 * BatchCancelException），后续源表在表间缝隙被跳过；增量链停止面维持既有行为。
 */
public class TestAbortCoversFullCopy extends AbstractMigrationE2ETestCase {

    private static final String OLD_SHARD_CONFIG = "{\"strategies\":{\"order-old\":"
            + "{\"type\":\"mod\",\"shard-count\":2,\"shard-names\":[\"0\",\"1\"],"
            + "\"entities\":[\"mig.entity.MigOrder\"]}},"
            + "\"default-strategy\":\"order-old\"}";

    /** 源侧行数（足够大以维持 abort 汇合的在飞窗；本地 H2 复制吞吐远高于此窗口需求）。 */
    private static final int ROWS_PER_TABLE = 3000;

    private MigrationJobConfig slowFullConfig() {
        java.util.Map<String, Object> targetStrategy = AbstractMigrationE2ETestCase.Json.map(
                "strategies", AbstractMigrationE2ETestCase.Json.map("order-mod",
                        AbstractMigrationE2ETestCase.Json.map("type", "mod", "shard-count", 2,
                                "entities", List.of("mig.entity.MigOrder"))));
        return MigrationJobConfig.newBuilder("rescale", "mig.entity.MigOrder")
                .sourceQuerySpaces(List.of(""))
                .targetStrategy(targetStrategy)
                .verify(false, 0, false)
                .throttle(0, 200, 1)
                .build();
    }

    private void seedTwoSourceTables() {
        // 源 _0 行（sid a*）全部路由到目标 _1（shard_key=1）；源 _1 行（sid b*）全部路由到
        // 目标 _0（shard_key=0）——读写物理表分离，abort 后「源 _1 从未被复制」由目标 _0
        // 中 sid b* 计数确定；首表在飞窗由目标 _1 中 sid a* 出现判定
        for (int i = 0; i < ROWS_PER_TABLE; i++)
            insertInto("MIG_ORDER_0", "a" + i, String.valueOf(i) + ".00", "1");
        for (int i = 0; i < ROWS_PER_TABLE; i++)
            insertInto("MIG_ORDER_1", "b" + i, String.valueOf(i) + ".00", "0");
    }

    private void insertInto(String table, String sid, String amount, String shardKey) {
        String sql = "INSERT INTO " + table + " (SID, AMOUNT, SHARD_KEY) VALUES (?,?,?)";
        try (var conn = dataSource.getConnection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, sid);
            ps.setString(2, amount);
            ps.setString(3, shardKey);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int countTargetRows(String table, String sidPrefix) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE SID LIKE '" + sidPrefix + "%'";
        try (var conn = dataSource.getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void insertFullPhaseState(String jobId) {
        MigrationJobState state = new MigrationJobState();
        state.setJobId(jobId);
        state.setPhase(MigrationPhase.FULL);
        state.setScenario("rescale");
        state.setEntityName("mig.entity.MigOrder");
        state.setCreateTimeMillis(io.nop.api.core.time.CoreMetrics.currentTimeMillis());
        state.setUpdateTimeMillis(state.getCreateTimeMillis());
        new MigrationJobStateStore(jdbcTemplate, "").insert(state);
    }

    @Test
    public void testAbortDuringFullStopsInflightCopyAndSkipsRemainingTables() throws Exception {
        seedTwoSourceTables();
        // 旧策略（2 分片源表 MIG_ORDER_0/1）生效后构建编排器（mapping 消费当前配置）
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, OLD_SHARD_CONFIG);
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                slowFullConfig(), jdbcTemplate, facade, jdbcTemplate, "");
        insertFullPhaseState("abt-full");

        FullCopyBatchJob job = orchestrator.fullJob();
        assertEquals(2, orchestrator.mapping().getSourceTables().size());

        FutureTask<Long> copy = new FutureTask<>(() -> job.run("abt-full"));
        Thread worker = new Thread(copy, "full-copy-under-abort");
        worker.setDaemon(true);
        worker.start();

        // 首表（源 MIG_ORDER_0 → 目标 _1，sid a*）在飞：等待首批落表（源表 sid b* 不受 LIKE
        // 前缀污染；目标 _1 的 sid a* 行只可能来自复制写入）
        long deadline = System.currentTimeMillis() + 30_000L;
        while (countTargetRows("MIG_ORDER_1", "a") == 0) {
            if (System.currentTimeMillis() > deadline)
                fail("full copy did not start writing within the deadline");
            Thread.sleep(10);
        }

        // FULL 期 abort：置停表标志 + cancel 在飞 chunk context（裁定 K）
        orchestrator.abort("abt-full", "abort-during-full");

        try {
            copy.get(120, TimeUnit.SECONDS);
            // 容忍窗：abort 恰落在两表之间 → 停表标志跳过后续表，run 正常返回
        } catch (java.util.concurrent.ExecutionException e) {
            // 在飞表被 cancel → BatchCancelException（或编排层包装）在复制线程传播
            Throwable cause = e.getCause();
            assertTrue(cause instanceof BatchCancelException || cause instanceof NopException,
                    "inflight copy must terminate via the batch cancel primitive, got: " + cause);
        }

        // 停止面生效证据：后续源表被跳过（目标 _0 零行 sid b* = 源 _1 从未复制）——
        // 无论 abort 汇合在在飞表（cancel 路径，首表行数 < 全量）还是两表缝隙（停表标志路径，
        // 首表行数 = 全量），剩余表必须零落表
        assertEquals(0, countTargetRows("MIG_ORDER_0", "b"),
                "tables after the inflight one must be skipped after abort (stop-requested flag)");
        int inflightRows = countTargetRows("MIG_ORDER_1", "a");
        assertTrue(inflightRows > 0 && inflightRows <= ROWS_PER_TABLE,
                "first-table rows must be observable, got " + inflightRows);
        assertTrue(job.isStopRequested(), "stop-requested flag must be observable on the job");
        assertEquals(MigrationPhase.ABORT, orchestrator.getState("abt-full").getPhase());
        assertEquals("abort-during-full", orchestrator.getState("abt-full").getAbortReason());
    }

    @Test
    public void testRequestStopIsIdempotentWithoutInflightContext() {
        // 空窗幂等：无在飞 context 时 requestStop 仅置标志（不 NPE、不重复 cancel）
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, OLD_SHARD_CONFIG);
        try {
            OrmMigrationOrchestrator orchestrator = new OrmMigrationOrchestrator(
                    slowFullConfig(), jdbcTemplate, entityFacade, jdbcTemplate, "");
            FullCopyBatchJob job = orchestrator.fullJob();
            job.requestStop();
            job.requestStop();
            assertTrue(job.isStopRequested());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
    }
}
