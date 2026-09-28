package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeCacheManager;
import io.nop.code.service.impl.GraphMetricMaterializer;
import io.nop.code.service.impl.ShardBuildCoordinator;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N6.4: atomic publish semantics — a partially completed shard build stays BUILDING and is
 * never published; completing the last shard flips the status and materializes global
 * metrics in the same transaction; completeShard is idempotent; re-begin resets the ledger.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestShardBuildCoordinator extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    private ShardBuildCoordinator newCoordinator() {
        ShardBuildCoordinator coordinator = new ShardBuildCoordinator();
        coordinator.daoProvider = daoProvider;
        coordinator.ormTemplate = ormTemplate;
        coordinator.setMetricMaterializer(new GraphMetricMaterializer(daoProvider,
                new CodeCacheManager(), transactionTemplate, ormTemplate));
        return coordinator;
    }

    private void seedIndex(String indexId) {
        // minimal indexed state via the public API so the index row exists
        codeIndexService.indexFile(indexId, "app/Seed.java",
                "package app;\npublic class Seed { public void go() {} }\n");
        // indexFile leaves status untouched (null); coordinator drives it explicitly
    }

    @Test
    void testPartialCompletionNeverPublishes() throws Exception {
        String indexId = "n64_partial";
        seedIndex(indexId);

        ShardBuildCoordinator coordinator = newCoordinator();
        ShardBuildCoordinator.ShardBuildStatus status =
                coordinator.beginShardBuild(indexId, List.of("shard-0", "shard-1"));
        assertEquals(2, status.getTotalShards());
        assertFalse(status.isPublished());

        coordinator.completeShard(indexId, "shard-0");
        status = coordinator.getShardBuildStatus(indexId);
        assertEquals(1, status.getCompletedShards());
        assertFalse(status.isPublished(), "partial completion must never publish");
        assertEquals(List.of("shard-1"), status.getPendingShards());
        assertEquals(ShardBuildCoordinator.STATUS_BUILDING, indexStatus(indexId),
                "a building index must be identifiable");

        assertThrows(IllegalArgumentException.class, () -> coordinator.completeShard(indexId, "unknown-shard"),
                "unknown shardId must fail explicitly");
        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testFinalShardPublishesAtomically() throws Exception {
        String indexId = "n64_publish";
        seedIndex(indexId);

        ShardBuildCoordinator coordinator = newCoordinator();
        coordinator.beginShardBuild(indexId, List.of("shard-0", "shard-1"));
        coordinator.completeShard(indexId, "shard-0");

        ShardBuildCoordinator.ShardBuildStatus status = coordinator.completeShard(indexId, "shard-1");
        assertTrue(status.isPublished(), "completing the last shard must publish");
        assertEquals(ShardBuildCoordinator.STATUS_COMPLETED, indexStatus(indexId),
                "publish must flip the index status in the same transaction");
        assertTrue(metricRowsExist(indexId), "publish must materialize global metrics");

        // idempotent retry of an already-completed shard must not re-publish or throw
        ShardBuildCoordinator.ShardBuildStatus retry = coordinator.completeShard(indexId, "shard-1");
        assertTrue(retry.isPublished());
        assertEquals(ShardBuildCoordinator.STATUS_COMPLETED, indexStatus(indexId));
        codeIndexService.deleteIndex(indexId);
    }

    @Test
    void testReBeginResetsLedger() throws Exception {
        String indexId = "n64_rebegin";
        seedIndex(indexId);

        ShardBuildCoordinator coordinator = newCoordinator();
        coordinator.beginShardBuild(indexId, List.of("shard-0", "shard-1", "shard-2"));
        coordinator.completeShard(indexId, "shard-0");

        // re-begin with a different shard plan: ledger resets, old completions discarded
        ShardBuildCoordinator.ShardBuildStatus status =
                coordinator.beginShardBuild(indexId, List.of("shard-a", "shard-b"));
        assertEquals(2, status.getTotalShards());
        assertEquals(0, status.getCompletedShards());
        assertEquals(List.of("shard-a", "shard-b"), status.getPendingShards());
        codeIndexService.deleteIndex(indexId);
    }

    private String indexStatus(String indexId) {
        IEntityDao<NopCodeIndex> dao = daoProvider.daoFor(NopCodeIndex.class);
        NopCodeIndex index = dao.getEntityById(indexId);
        assertNotNull(index, "index row must exist");
        return index.getStatus();
    }

    private boolean metricRowsExist(String indexId) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        return !dao.findAllByQuery(new QueryBean().addFilter(
                io.nop.api.core.beans.FilterBeans.eq("indexId", indexId))).isEmpty();
    }
}
