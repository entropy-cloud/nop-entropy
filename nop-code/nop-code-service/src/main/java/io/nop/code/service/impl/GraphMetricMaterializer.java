package io.nop.code.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.entrypoint.EntryPointScorer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.CodeCallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graph.algorithm.BetweennessCentrality;
import io.nop.graph.api.CommunityInfo;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.LeidenConfig;
import io.nop.graph.algorithm.LeidenDetector;
import io.nop.code.service.util.CodeSymbolConverter;
import io.nop.graph.algorithm.PageRank;
import io.nop.orm.IOrmTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.api.core.annotations.txn.TransactionPropagation;

/**
 * Materializes global graph algorithm results (community / betweenness / PageRank / entry
 * point scores) into {@link NopCodeGraphMetric} rows at index time. Queries later read the
 * persisted rows (via {@code GraphMetricStore}) instead of recomputing.
 *
 * Constructed by {@link CodeIndexService#ensureSubServices()} so it shares the same
 * {@link CodeCacheManager} instance — a private cache here would never be invalidated by
 * {@code invalidateAnalysisCache} and would materialize stale graphs.
 *
 * Row coverage: COMMUNITY/BETWEENNESS/PAGE_RANK rows cover the call-graph node set only
 * (isolated symbols have no rows); ENTRY_POINT rows cover METHOD/CONSTRUCTOR symbols.
 *
 * Betweenness keeps the existing >10000-node skip guard (rows are simply absent, a warn
 * is logged) — large-graph bridge signals degrade per graph-discovery §3.0.
 */
class GraphMetricMaterializer {
    private static final Logger LOG = LoggerFactory.getLogger(GraphMetricMaterializer.class);

    static final String METRIC_COMMUNITY = "COMMUNITY";
    static final String METRIC_BETWEENNESS = "BETWEENNESS";
    static final String METRIC_PAGE_RANK = "PAGE_RANK";
    static final String METRIC_ENTRY_POINT = "ENTRY_POINT";

    private static final int PAGE_RANK_ITERATIONS = 20;
    private static final int BETWEENNESS_MAX_NODES = 10000;
    private static final int DELETE_BATCH_SIZE = 1000;

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;
    private final ITransactionTemplate transactionTemplate;
    private final IOrmTemplate ormTemplate;

    GraphMetricMaterializer(IDaoProvider daoProvider, CodeCacheManager cacheManager,
                            ITransactionTemplate transactionTemplate, IOrmTemplate ormTemplate) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
        this.transactionTemplate = transactionTemplate;
        this.ormTemplate = ormTemplate;
    }

    /**
     * Recomputes all four metric families and replaces the persisted rows for the index.
     * Delete + insert runs in one transaction, so a failure leaves the previous snapshot intact.
     */
    public void materialize(String indexId) {
        CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
        SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                CodeSymbolConverter::toCodeSymbol);

        CodeCallGraph graph = new CodeCallGraph(callGraph);
        Set<String> nodes = callGraph.getAllNodeIds();

        long computedAt = System.currentTimeMillis();
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        rows.addAll(communityRows(indexId, graph, nodes, computedAt));
        rows.addAll(betweennessRows(indexId, graph, nodes, computedAt));
        rows.addAll(pageRankRows(indexId, graph, nodes, computedAt));
        rows.addAll(entryPointRows(indexId, callGraph, symbolTable, computedAt));

        replaceRows(indexId, rows);
        LOG.info("nop.code.graph-metrics-materialized:indexId={},rows={}", indexId, rows.size());
    }

    List<NopCodeGraphMetric> communityRows(String indexId, CodeCallGraph graph, Set<String> nodes,
                                           long computedAt) {
        if (nodes.size() < 2) {
            LOG.info("nop.code.graph-metrics-skip:indexId={},metric=COMMUNITY,reason=node-set-too-small", indexId);
            return List.of();
        }
        LeidenConfig config = LeidenConfig.create()
                .setResolution(0.1)
                .setMaxIterations(10)
                .setTimeoutMs(60000);
        CommunityResult result = LeidenDetector.detect(graph, nodes, config);
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        for (CommunityInfo community : result.getCommunities()) {
            for (String nodeId : community.getNodeIds()) {
                NopCodeGraphMetric row = newRow(indexId, METRIC_COMMUNITY, nodeId, computedAt);
                row.setCommunityId(community.getId());
                rows.add(row);
            }
        }
        return rows;
    }

    List<NopCodeGraphMetric> betweennessRows(String indexId, CodeCallGraph graph, Set<String> nodes,
                                             long computedAt) {
        if (nodes.isEmpty()) {
            LOG.info("nop.code.graph-metrics-skip:indexId={},metric=BETWEENNESS,reason=empty-node-set", indexId);
            return List.of();
        }
        if (nodes.size() > BETWEENNESS_MAX_NODES) {
            LOG.warn("nop.code.graph-metrics-skip:indexId={},metric=BETWEENNESS,reason=node-count-exceeds-max,nodes={},max={}",
                    indexId, nodes.size(), BETWEENNESS_MAX_NODES);
            return List.of();
        }
        Map<String, Double> scores = BetweennessCentrality.compute(graph, nodes);
        return scoreRows(indexId, METRIC_BETWEENNESS, scores, computedAt);
    }

    List<NopCodeGraphMetric> pageRankRows(String indexId, CodeCallGraph graph, Set<String> nodes,
                                          long computedAt) {
        if (nodes.isEmpty()) {
            LOG.info("nop.code.graph-metrics-skip:indexId={},metric=PAGE_RANK,reason=empty-node-set", indexId);
            return List.of();
        }
        // iterations=20 follows the small-graph default used by nop-graph-core tests
        Map<String, Double> scores = PageRank.compute(graph, nodes, PAGE_RANK_ITERATIONS);
        return scoreRows(indexId, METRIC_PAGE_RANK, scores, computedAt);
    }

    List<NopCodeGraphMetric> entryPointRows(String indexId, CallGraph callGraph, SymbolTable symbolTable,
                                            long computedAt) {
        List<EntryPointScorer.EntryPointScore> scores =
                new EntryPointScorer().scoreEntryPoints(callGraph, symbolTable);
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        int rank = 0;
        for (EntryPointScorer.EntryPointScore score : scores) {
            rank++;
            NopCodeGraphMetric row = newRow(indexId, METRIC_ENTRY_POINT, score.getSymbolId(), computedAt);
            row.setScore(score.getScore());
            row.setRankNo(rank);
            if (score.getEntryPointType() != null) {
                row.setEntryPointType(score.getEntryPointType().name());
            }
            rows.add(row);
        }
        return rows;
    }

    private List<NopCodeGraphMetric> scoreRows(String indexId, String metricType, Map<String, Double> scores,
                                               long computedAt) {
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, Double> entry : ordered) {
            rank++;
            NopCodeGraphMetric row = newRow(indexId, metricType, entry.getKey(), computedAt);
            row.setScore(entry.getValue());
            row.setRankNo(rank);
            rows.add(row);
        }
        return rows;
    }

    private NopCodeGraphMetric newRow(String indexId, String metricType, String symbolId, long computedAt) {
        NopCodeGraphMetric row = (NopCodeGraphMetric) ormTemplate.newEntity(NopCodeGraphMetric.class.getName());
        row.setIndexId(indexId);
        row.setMetricType(metricType);
        row.setSymbolId(symbolId);
        row.setComputedAt(new java.sql.Timestamp(computedAt));
        return row;
    }

    private void replaceRows(String indexId, List<NopCodeGraphMetric> rows) {
        transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                ormTemplate.runInSession(session -> {
                    IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
                    while (true) {
                        QueryBean query = new QueryBean();
                        query.addFilter(FilterBeans.eq("indexId", indexId));
                        query.setOffset(0);
                        query.setLimit(DELETE_BATCH_SIZE);
                        List<NopCodeGraphMetric> batch = dao.findPageByQuery(query);
                        if (batch.isEmpty())
                            break;
                        for (NopCodeGraphMetric row : batch) {
                            session.delete(row);
                        }
                        session.flush();
                        session.clear();
                        if (batch.size() < DELETE_BATCH_SIZE)
                            break;
                    }
                    for (NopCodeGraphMetric row : rows) {
                        dao.saveEntity(row);
                    }
                    return null;
                }));
    }

    /**
     * Deletes the materialized rows for an index. Used on actual incremental changes and on
     * deleteIndex; runs inside the caller's transaction/session when one is active.
     */
    public void invalidate(String indexId) {
        transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
                ormTemplate.runInSession(session -> {
                    deleteByIndex(session, indexId);
                    return null;
                }));
    }

    void deleteByIndex(io.nop.orm.IOrmSession session, String indexId) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setOffset(0);
            query.setLimit(DELETE_BATCH_SIZE);
            List<NopCodeGraphMetric> batch = dao.findPageByQuery(query);
            if (batch.isEmpty())
                break;
            for (NopCodeGraphMetric row : batch) {
                session.delete(row);
            }
            session.flush();
            session.clear();
            if (batch.size() < DELETE_BATCH_SIZE)
                break;
        }
    }
}
