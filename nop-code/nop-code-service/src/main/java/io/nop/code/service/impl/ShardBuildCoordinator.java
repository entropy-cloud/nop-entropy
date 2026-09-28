package io.nop.code.service.impl;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.dao.entity.NopCodeShardLedger;
import io.nop.code.service.impl.GraphMetricMaterializer;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * N6.4: 集群分片构建的原子发布协调器。
 *
 * <p>语义（详见 ai-dev/design/nop-code/cluster-index-consistency.md）：
 * <ul>
 * <li>begin：index.status=BUILDING + ledger 全量重建（PENDING）——构建期索引可辨识；</li>
 * <li>completeShard：登记 COMPLETED（幂等 upsert）；全部 COMPLETED 时在**同一事务**内
 *     status=COMPLETED + 全局度量物化——发布点原子；</li>
 * <li>未全完成不发布；未知 shardId 显式报错；重复 begin 重置 ledger。</li>
 * </ul>
 */
public class ShardBuildCoordinator {
    public static final String STATUS_BUILDING = "BUILDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String SHARD_PENDING = "PENDING";
    public static final String SHARD_COMPLETED = "COMPLETED";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private GraphMetricMaterializer metricMaterializer;

    public void setMetricMaterializer(GraphMetricMaterializer metricMaterializer) {
        this.metricMaterializer = metricMaterializer;
    }

    public ShardBuildStatus beginShardBuild(String indexId, List<String> shardIds) {
        if (shardIds == null || shardIds.isEmpty()) {
            throw new IllegalArgumentException("shardIds must be non-empty for a shard build: " + indexId);
        }
        ormTemplate.runInSession(session -> {
            NopCodeIndex index = (NopCodeIndex) session.get(
                    NopCodeIndex.class.getName(), indexId);
            if (index == null) {
                throw new IllegalArgumentException("index not found: " + indexId);
            }
            index.setStatus(STATUS_BUILDING);

            IEntityDao<NopCodeShardLedger> ledgerDao = daoProvider.daoFor(NopCodeShardLedger.class);
            deleteLedger(indexId, ledgerDao);
            for (String shardId : shardIds) {
                NopCodeShardLedger row = (NopCodeShardLedger) ormTemplate.newEntity(NopCodeShardLedger.class.getName());
                row.setId(ledgerRowId(indexId, shardId));
                row.setIndexId(indexId);
                row.setShardId(shardId);
                row.setStatus(SHARD_PENDING);
                session.save(row);

            }
            return null;
        });
        return getShardBuildStatus(indexId);
    }

    /**
     * 登记一个分片完成（幂等）；全部完成时同事务发布（status=COMPLETED + 全局度量物化）。
     */
    public ShardBuildStatus completeShard(String indexId, String shardId) {
        return ormTemplate.runInSession(session -> {
            NopCodeShardLedger row = (NopCodeShardLedger) session.get(
                    NopCodeShardLedger.class.getName(), ledgerRowId(indexId, shardId));
            if (row == null) {
                throw new IllegalArgumentException(
                        "unknown shardId " + shardId + " for index " + indexId
                                + " (call beginShardBuild first)");
            }
            if (!SHARD_COMPLETED.equals(row.getStatus())) {
                row.setStatus(SHARD_COMPLETED);
                row.setCompletedAt(io.nop.api.core.time.CoreMetrics.currentTimestamp());
            }
            IEntityDao<NopCodeShardLedger> ledgerDao = daoProvider.daoFor(NopCodeShardLedger.class);
            return finalizeIfAllCompleted(indexId, ledgerDao, session);
        });
    }

    private ShardBuildStatus finalizeIfAllCompleted(String indexId, IEntityDao<NopCodeShardLedger> ledgerDao,
                                                    io.nop.orm.IOrmSession session) {
        List<NopCodeShardLedger> rows = loadLedger(indexId, ledgerDao);
        boolean allCompleted = !rows.isEmpty()
                && rows.stream().allMatch(r -> SHARD_COMPLETED.equals(r.getStatus()));
        boolean published = false;
        if (allCompleted) {
            NopCodeIndex index = (NopCodeIndex) session.get(
                    NopCodeIndex.class.getName(), indexId);
            if (index == null) {
                throw new IllegalArgumentException("index not found: " + indexId);
            }
            index.setStatus(STATUS_COMPLETED);
            if (metricMaterializer != null) {
                // publish point: global metrics materialized in the same transaction as the
                // status flip, so a published index is always fully scored
                metricMaterializer.materialize(indexId);
            }
            published = true;
        }
        return buildStatus(indexId, rows, published);
    }

    public ShardBuildStatus getShardBuildStatus(String indexId) {
        return buildStatus(indexId, loadLedger(indexId, daoProvider.daoFor(NopCodeShardLedger.class)), false);
    }

    private ShardBuildStatus buildStatus(String indexId, List<NopCodeShardLedger> rows, boolean published) {
        ShardBuildStatus status = new ShardBuildStatus();
        status.setIndexId(indexId);
        status.setTotalShards(rows.size());
        status.setCompletedShards((int) rows.stream().filter(r -> SHARD_COMPLETED.equals(r.getStatus())).count());
        status.setPublished(published);
        List<String> pending = new ArrayList<>();
        for (NopCodeShardLedger row : rows) {
            if (!SHARD_COMPLETED.equals(row.getStatus())) {
                pending.add(row.getShardId());
            }
        }
        status.setPendingShards(pending);
        return status;
    }

    private List<NopCodeShardLedger> loadLedger(String indexId, IEntityDao<NopCodeShardLedger> ledgerDao) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return ledgerDao.findAllByQuery(query);
    }

    private void deleteLedger(String indexId, IEntityDao<NopCodeShardLedger> ledgerDao) {
        List<NopCodeShardLedger> rows = loadLedger(indexId, ledgerDao);
        if (!rows.isEmpty()) {
            ledgerDao.batchDeleteEntities(rows);
        }
    }

    private static String ledgerRowId(String indexId, String shardId) {
        return indexId + ":" + shardId;
    }

    public static class ShardBuildStatus {
        private String indexId;
        private int totalShards;
        private int completedShards;
        private boolean published;
        private List<String> pendingShards = new ArrayList<>();

        public String getIndexId() {
            return indexId;
        }

        public void setIndexId(String indexId) {
            this.indexId = indexId;
        }

        public int getTotalShards() {
            return totalShards;
        }

        public void setTotalShards(int totalShards) {
            this.totalShards = totalShards;
        }

        public int getCompletedShards() {
            return completedShards;
        }

        public void setCompletedShards(int completedShards) {
            this.completedShards = completedShards;
        }

        public boolean isPublished() {
            return published;
        }

        public void setPublished(boolean published) {
            this.published = published;
        }

        public List<String> getPendingShards() {
            return pendingShards;
        }

        public void setPendingShards(List<String> pendingShards) {
            this.pendingShards = pendingShards;
        }
    }
}
