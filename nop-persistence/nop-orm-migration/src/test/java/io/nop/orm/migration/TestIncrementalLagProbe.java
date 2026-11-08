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
import io.nop.commons.test.time.ManualClock;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import org.junit.jupiter.api.AfterEach;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ARG_ENTITY_NAME;
import static io.nop.orm.migration.MigrationErrors.ARG_SOURCE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lag=0 判定（m6）：静默期 offset 无前进 + ledger 无新增，两观测量同时满足；
 * shard-router：ChangeEvent 行内分片键 → 目标物理表；null 键 fail-fast。
 */
public class TestIncrementalLagProbe extends AbstractMigrationE2ETestCase {

    private static final String CONNECTOR = "lag-probe-cdc";

    @AfterEach
    void cleanupConnector() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
    }

    @Test
    public void testLagZeroWhenNothingAdvances() {
        insertLedgerRow("mig", 1L, 0);
        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2");
        assertTrue(probe.awaitLagZero(null, 1), "no-incremental job: ledger static → lag=0");
    }

    @Test
    public void testLagNonZeroWhenLedgerCommits() {
        insertLedgerRow("mig", 1L, 0);
        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2");
        // 静默窗口走 CoreMetrics.currentTimeMillis：注册 ManualClock 虚拟化窗口时钟——
        // 提交线程先追加 ledger 行再推进虚拟时间越过窗口截止，保证提交确定性地落在窗口内
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            // 结构同步门：只统计探针线程（main）对时钟的读取次数（详见 CDC 测试内注释）
            Thread probeThread = Thread.currentThread();
            Thread committer = new Thread(() -> {
                io.nop.api.core.util.FutureHelper.waitUntil(() -> clock.readsBy(probeThread) >= 2, 5000);
                insertLedgerRow("mig", 2L, 0);
                clock.advance(1000);
            });
            committer.setDaemon(true);
            committer.start();
            assertFalse(probe.awaitLagZero(null, 1), "ledger committed within quiesce → lag != 0");
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    @Test
    public void testLagNonZeroWhenCdcOffsetAdvances() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2");
        // 静默窗口走 CoreMetrics.currentTimeMillis：注册 ManualClock 虚拟化窗口时钟——
        // 推进线程先推 CDC offset 再推进虚拟时间越过窗口截止，保证变更确定性地落在窗口内
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            // 结构同步门：只统计探针线程（main）对时钟的读取次数——若用 totalReads，
            // 本推进线程自身 waitUntil 的轮询读取会提前满足门条件（残窗 1ms 永不流逝的死锁根因）。
            // 探针读到 2 次 = 已算出窗口截止并进入静默循环，此后变更+推进必然落在窗口内。
            Thread probeThread = Thread.currentThread();
            Thread advancer = new Thread(() -> {
                io.nop.api.core.util.FutureHelper.waitUntil(() -> clock.readsBy(probeThread) >= 2, 5000);
                Map<java.nio.ByteBuffer, java.nio.ByteBuffer> offsets = new LinkedHashMap<>();
                offsets.put(java.nio.ByteBuffer.wrap("p".getBytes()),
                        java.nio.ByteBuffer.wrap("advanced".getBytes()));
                store.setOffsets(offsets);
                clock.advance(1000);
            });
            advancer.setDaemon(true);
            advancer.start();
            assertFalse(probe.awaitLagZero(CONNECTOR, 1), "CDC offset advanced within quiesce → lag != 0");
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    @Test
    public void testLagZeroWithStaticOffsetsAndLedger() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        store.setOffsets(java.util.Map.of(
                java.nio.ByteBuffer.wrap("p".getBytes()),
                java.nio.ByteBuffer.wrap("static".getBytes())));
        insertLedgerRow("mig", 1L, 0);
        IncrementalLagProbe probe = new IncrementalLagProbe(jdbcTemplate, "",
                "stream_epoch_ledger_v2");
        assertTrue(probe.awaitLagZero(CONNECTOR, 1));
    }

    @Test
    public void testRouterRoutesByShardKeyValue() {
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        TargetShardMapping mapping = TargetShardMapping.of(facade, rescaleConfig());
        ShardChangeEventRouter router = new ShardChangeEventRouter(mapping);

        // mod(sid 数值)：奇数 → _0? mod(1,2)=1 → shard "1" → MIG_ORDER_1
        assertEquals("MIG_ORDER_1", router.route(event("c", "1")).getTableName());
        assertEquals("MIG_ORDER_0", router.route(event("u", "2")).getTableName());
        // delete 事件取 key image
        assertEquals("MIG_ORDER_1", router.route(event("d", "3")).getTableName());
        // 回退形态谓词：belongsTo 与 route 一致
        assertTrue(router.belongsTo(event("c", "1"),
                new TargetShardMapping.PhysicalTable("", "MIG_ORDER_1")));
        assertFalse(router.belongsTo(event("c", "1"),
                new TargetShardMapping.PhysicalTable("", "MIG_ORDER_0")));
    }

    @Test
    public void testRouterNullShardKeyFailsTyped() {
        IOrmSessionFactory facade = FakeEntityModelFacade.facade(
                "mig.entity.MigOrder", "MIG_ORDER", "sid", "shard_key");
        TargetShardMapping mapping = TargetShardMapping.of(facade, rescaleConfig());
        ShardChangeEventRouter router = new ShardChangeEventRouter(mapping);

        // null 分片键在流图内以类型化异常抛出（NopException 是 RuntimeException）→ 流任务 FAILED
        NopException e = assertThrows(NopException.class, () -> router.route(event("c", null)));
        assertEquals(MigrationErrors.ERR_MIG_NULL_SHARD_KEY.getErrorCode(), e.getErrorCode());
        assertEquals("mig.entity.MigOrder", e.getParams().get(MigrationErrors.ARG_ENTITY_NAME));
        assertEquals("MIG_ORDER", e.getParams().get(MigrationErrors.ARG_SOURCE_TABLE));
    }

    private ChangeEvent event(String operation, String sid) {
        ChangeEventMetadata meta = new ChangeEventMetadata("mysql", "mig", "db", null,
                "MIG_ORDER", null);
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("SID", sid);
        image.put("shard_key", sid);
        if ("d".equals(operation))
            return new ChangeEvent(meta, operation, null, null, image, 0L);
        return new ChangeEvent(meta, operation, null, image, image, 0L);
    }
}
