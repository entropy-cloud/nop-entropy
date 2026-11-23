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
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import io.nop.orm.IOrmSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    /**
     * Deterministic quiesce window: {@code entered} is signalled the moment the probe
     * reaches the wait between its two snapshots; the driver thread mutates the observed
     * state, then {@code release} lets the probe take its after-snapshot. No wall-clock
     * budget and no clock-read counting (JdbcTemplate itself reads CoreMetrics during
     * snapshot(), which used to satisfy the read gate before the window even started).
     */
    static final class GatedProbe extends IncrementalLagProbe {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean released;

        GatedProbe(IJdbcTemplate jdbc, String ledgerTable) {
            super(jdbc, "", ledgerTable);
        }

        @Override
        protected void sleep(long quiesceSeconds) {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("lag probe quiesce release timed out");
                released = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("lag probe quiesce interrupted", e);
            }
        }
    }

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
    public void testLagNonZeroWhenLedgerCommits() throws Exception {
        insertLedgerRow("mig", 1L, 0);
        GatedProbe probe = new GatedProbe(jdbcTemplate, "stream_epoch_ledger_v2");
        Thread committer = new Thread(() -> {
            try {
                if (!probe.entered.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("probe never reached the quiesce window");
                insertLedgerRow("mig", 2L, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("committer interrupted", e);
            } finally {
                probe.release.countDown();
            }
        });
        committer.setDaemon(true);
        committer.start();
        assertFalse(probe.awaitLagZero(null, 1), "ledger committed within quiesce → lag != 0");
        assertTrue(probe.released, "the committer must have run inside the quiesce window");
    }

    @Test
    public void testLagNonZeroWhenCdcOffsetAdvances() throws Exception {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        GatedProbe probe = new GatedProbe(jdbcTemplate, "stream_epoch_ledger_v2");
        Thread advancer = new Thread(() -> {
            try {
                if (!probe.entered.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("probe never reached the quiesce window");
                Map<java.nio.ByteBuffer, java.nio.ByteBuffer> offsets = new LinkedHashMap<>();
                offsets.put(java.nio.ByteBuffer.wrap("p".getBytes()),
                        java.nio.ByteBuffer.wrap("advanced".getBytes()));
                store.setOffsets(offsets);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("advancer interrupted", e);
            } finally {
                probe.release.countDown();
            }
        });
        advancer.setDaemon(true);
        advancer.start();
        assertFalse(probe.awaitLagZero(CONNECTOR, 1), "CDC offset advanced within quiesce → lag != 0");
        assertTrue(probe.released, "the advancer must have run inside the quiesce window");
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
