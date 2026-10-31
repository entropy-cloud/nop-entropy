/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.ChangeEvent;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import org.junit.jupiter.api.Test;

import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CHAIN_JOB_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红⑦（C3-001/C10-002，裁定⑦）：部分启动补偿 + jobName qs 键派生 + runningJobs putIfAbsent
 * fail-fast。缺陷态红判据：中途链启动失败 → 已启动链滞留为孤儿（不可停、双写同目标表）；
 * 同 jobName 二次启动静默顶替旧句柄；jobName 丢 querySpace 撞键。修复后：回收 + 类型化拒绝。
 */
public class TestStartFallbackChainsCompensation extends AbstractMigrationE2ETestCase {

    private static final String CHAIN_CONFIG_JSON = "{"
            + "\"scenario\":\"rescale\","
            + "\"entity\":\"mig.entity.MigOrder\","
            + "\"source-query-spaces\":[\"\"],"
            + "\"target-strategy\":{\"strategies\":{\"order-mod\":{\"type\":\"mod\",\"shard-count\":2,"
            + "\"entities\":[\"mig.entity.MigOrder\"]}}},"
            + "\"incremental\":{\"enabled\":true,\"connector-name\":\"comp-cdc\","
            + "\"debezium\":{\"connector-type\":\"mysql\",\"database-host\":\"localhost\","
            + "\"database-port\":3306,\"database-user\":\"u\",\"database-password\":\"p\","
            + "\"database-name\":\"db\"},\"quiesce-seconds\":1}"
            + "}";

    private MigrationJobConfig chainConfig() {
        return MigrationJobConfig.parse(CHAIN_CONFIG_JSON);
    }

    private TargetShardMapping mapping() {
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        return TargetShardMapping.of(facade, chainConfig());
    }

    private io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<ChangeEvent> buildSink(PhysicalTable target) {
        return io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                .jdbcTemplate(jdbcTemplate)
                .querySpace(target.getQuerySpace())
                .tableName(target.getTableName())
                .ledgerTableName("stream_epoch_ledger_v2")
                .dmlMode(io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE)
                .keyColumns("SID")
                .build();
    }

    private IncrementalJobManager newManager() {
        IncrementalJobManager manager = new IncrementalJobManager(mapping(), this::buildSink, 1_000L);
        manager.setChainStopJoinTimeoutMs(500);
        return manager;
    }

    @Test
    public void testPartialStartFailure_RecyclesStartedChains() {
        TargetShardMapping shardMapping = mapping();
        IncrementalJobManager manager = new IncrementalJobManager(shardMapping, target -> {
            if (target.getTableName().equals("MIG_ORDER_1"))
                throw new NopException(MigrationErrors.ERR_MIG_TARGET_TABLE_NOT_READY)
                        .param(MigrationErrors.ARG_TARGET_TABLE, target.getTableName());
            return buildSink(target);
        }, 1_000L);
        manager.setChainStopJoinTimeoutMs(500);

        NopException e = assertThrows(NopException.class,
                () -> manager.startFallbackChains("cmp1", chainConfig(),
                        SourcePosition.mysql("log.bin", 1, "")));
        assertEquals(MigrationErrors.ERR_MIG_TARGET_TABLE_NOT_READY.getErrorCode(), e.getErrorCode());

        // 裁定⑦：已启动链回收——无孤儿句柄滞留
        assertTrue(manager.snapshotRunningJobs().isEmpty(),
                "already-started chains must be recycled on partial-start failure (no orphans)");
    }

    @Test
    public void testDuplicateJobName_PutIfAbsentRejectsReentry() {
        IncrementalJobManager manager = newManager();
        PhysicalTable target = shardMapping().getTargetTables().get(0);
        try {
            manager.startChain("dup1", manager.buildCdcConfig("dup1", chainConfig(), "dup-cdc",
                    "MIG_ORDER"), target, SourcePosition.mysql("log.bin", 1, ""));
            NopException e = assertThrows(NopException.class,
                    () -> manager.startChain("dup1", manager.buildCdcConfig("dup1", chainConfig(),
                            "dup-cdc", "MIG_ORDER"), target, SourcePosition.mysql("log.bin", 1, "")));
            assertEquals(ERR_MIG_CHAIN_JOB_CONFLICT.getErrorCode(), e.getErrorCode());
            assertTrue(String.valueOf(e.getParams().get(ARG_REASON))
                            .contains("orm-migration-incremental-dup1-"),
                    "conflict message must carry the identity key jobName, got: "
                            + e.getParams().get(ARG_REASON));
            // 旧句柄未被静默顶替：注册表内仍是首个句柄
            assertEquals(1, manager.snapshotRunningJobs().size());
        } finally {
            manager.stopAll();
        }
    }

    @Test
    public void testJobNameCarriesQuerySpaceIdentity() {
        IncrementalJobManager manager = newManager();
        PhysicalTable target = shardMapping().getTargetTables().get(0);
        try {
            IncrementalJobManager.RunningChainJob job = manager.startChain("qs1",
                    manager.buildCdcConfig("qs1", chainConfig(), "qs-cdc", "MIG_ORDER"), target,
                    SourcePosition.mysql("log.bin", 1, ""));
            // 裁定⑦：jobName 键派生纳入 querySpace（多 querySpace 同名目标表撞键覆盖消除）
            assertTrue(job.getJobName().contains(target.getQuerySpace() + "-" + target.getTableName()),
                    "jobName must carry '<querySpace>-<tableName>' identity: " + job.getJobName());
            assertEquals("qs-cdc", job.getConnectorName(),
                    "RunningChainJob must carry its registered connector name (裁定⑨构造期携带)");
        } finally {
            manager.stopAll();
        }
    }

    private TargetShardMapping shardMapping() {
        return mapping();
    }
}
