package io.nop.code.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.entrypoint.EntryPointScorer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.CodeCallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.core.lang.json.JsonTool;
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
    static final String METRIC_HUB = "HUB";
    static final String METRIC_COMMUNITY_INFO = "COMMUNITY_INFO";
    static final String METRIC_GRAPH_SUMMARY = "GRAPH_SUMMARY";

    private static final int PAGE_RANK_ITERATIONS = 20;
    private static final int BETWEENNESS_MAX_NODES = 10000;
    private static final int DELETE_BATCH_SIZE = 1000;

    private final IDaoProvider daoProvider;
    private final CodeCacheManager cacheManager;
    private final ITransactionTemplate transactionTemplate;
    private final IOrmTemplate ormTemplate;

    /**
     * Per-index single-flight gates (6.3-W-03 / plan 2530 裁定② — tryLock
     * skip-and-degrade form; {@link CodeIndexService} indexLocks is the same-family
     * precedent). Non-blocking acquisition: nobody waits on the gate while holding it, so
     * no lock-ordering surface exists for the ShardBuildCoordinator in-transaction call.
     */
    private final ConcurrentHashMap<String, ReentrantLock> materializeGates = new ConcurrentHashMap<>();

    /**
     * Failure backoff: per-index cooldown deadline recorded when the computation body throws
     * (裁定②.4). The cooldown only applies to the failure path - a successful body clears the
     * record, so normal rebuilds are never suppressed by a stale failure. Default window
     * matches the {@code nop.code.rebuild.debounce-millis|30000} family.
     */
    private final ConcurrentHashMap<String, Long> failureCooldownUntil = new ConcurrentHashMap<>();

    private long failureCooldownMillis = 30_000L;

    /** Cooldown window length (package-visible plain setter — direct-new assembly, not an IoC bean; ShardBuildCoordinator.setMetricMaterializer precedent). */
    void setFailureCooldownMillis(long failureCooldownMillis) {
        if (failureCooldownMillis < 0) {
            throw new IllegalArgumentException(
                    "failureCooldownMillis must be >= 0, got: " + failureCooldownMillis);
        }
        this.failureCooldownMillis = failureCooldownMillis;
    }

    GraphMetricMaterializer(IDaoProvider daoProvider, CodeCacheManager cacheManager,
                            ITransactionTemplate transactionTemplate, IOrmTemplate ormTemplate) {
        this.daoProvider = daoProvider;
        this.cacheManager = cacheManager;
        this.transactionTemplate = transactionTemplate;
        this.ormTemplate = ormTemplate;
    }

    /**
     * Recomputes all metric families and replaces the persisted rows for the index.
     * Delete + insert runs in one transaction, so a failure leaves the previous snapshot intact.
     * Leiden runs exactly once per materialization and its result feeds the COMMUNITY,
     * COMMUNITY_INFO and GRAPH_SUMMARY families (compute-once: values returned to callers
     * must equal the persisted values).
     *
     * <p>Single-flight + failure backoff (6.3-W-03 / plan 2530 裁定② — this entry point is
     * the single choke point (唯一收口) for all 6 self-heal/trigger faces, so the gate here covers them all and
     * no call face changed): a concurrent trigger for an index whose materialization is
     * already in progress skips immediately with a structured WARN
     * ({@code reason=in-progress}) — the winner is computing exactly what the loser wants,
     * and read paths degrade through the existing assemble→null→in-memory fallback chains.
     * After a failed body, subsequent calls inside the cooldown window skip with
     * {@code reason=cooldown-active}; success clears the failure record. Residual
     * (explicitly bounded, owner-doc contract): under gate contention the
     * ShardBuildCoordinator publish path degrades to eventual consistency bounded by the
     * in-flight materialization duration; after an in-flight failure, by the cooldown window
     * plus the next read-triggered attempt.
     */
    public void materialize(String indexId) {
        ReentrantLock gate = materializeGates.computeIfAbsent(indexId, k -> new ReentrantLock());
        if (!gate.tryLock()) {
            // single-flight skip-and-degrade: explicit WARN, never a silent skip - losers
            // return immediately and every read face degrades through its existing fallback
            LOG.warn("nop.code.graph-metrics-skip:indexId={},reason=in-progress", indexId);
            return;
        }
        try {
            Long cooldownUntil = failureCooldownUntil.get(indexId);
            if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
                LOG.warn("nop.code.graph-metrics-skip:indexId={},reason=cooldown-active", indexId);
                return;
            }
            CallGraph callGraph = cacheManager.getOrRebuildCallGraph(indexId, daoProvider,
                    (g, e) -> g.addEdge(e.getCallerId(), e.getCalleeId()));
            SymbolTable symbolTable = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider,
                    CodeSymbolConverter::toCodeSymbol);

            CodeCallGraph graph = new CodeCallGraph(callGraph);
            Set<String> nodes = callGraph.getAllNodeIds();

            CommunityResult communityResult = detectCommunities(graph, nodes);
            long computedAt = System.currentTimeMillis();
            List<NopCodeGraphMetric> rows = new ArrayList<>();
            rows.addAll(communityRows(indexId, communityResult, computedAt));
            rows.addAll(communityInfoRows(indexId, communityResult, computedAt));
            rows.addAll(summaryRows(indexId, communityResult, nodes, symbolTable, computedAt));
            rows.addAll(betweennessRows(indexId, graph, nodes, computedAt));
            rows.addAll(pageRankRows(indexId, graph, nodes, computedAt));
            rows.addAll(entryPointRows(indexId, callGraph, symbolTable, computedAt));
            rows.addAll(hubRows(indexId, callGraph, nodes, computedAt));

            replaceRows(indexId, rows);
            LOG.info("nop.code.graph-metrics-materialized:indexId={},rows={}", indexId, rows.size());
            // backoff applies to the failure path only: success clears the failure record
            failureCooldownUntil.remove(indexId);
        } catch (RuntimeException e) {
            // record the failure deadline and rethrow unchanged - caller catch chains stay
            // intact and keep surfacing the root cause
            failureCooldownUntil.put(indexId, System.currentTimeMillis() + failureCooldownMillis);
            throw e;
        } finally {
            gate.unlock();
        }
    }

    /**
     * Single Leiden invocation shared by COMMUNITY / COMMUNITY_INFO / GRAPH_SUMMARY builders.
     * Returns null for node sets too small for community detection and for node sets over the
     * shared size guard (5.6-W-04 / plan 2530 裁定③: references
     * {@link CodeGraphService#MAX_NODES_FOR_COMMUNITY_DETECTION} — same constant, no
     * duplicate; aligned with the BETWEENNESS_MAX_NODES guard family). An over-limit graph
     * yields null - COMMUNITY/COMMUNITY_INFO rows are simply absent and GRAPH_SUMMARY carries
     * no algorithm fields; readers degrade through the existing null chains (assemble →
     * null → in-memory computation). The other metric families are unaffected - the guard
     * only degrades the community family, it never escalates into a materialization failure.
     */
    private CommunityResult detectCommunities(CodeCallGraph graph, Set<String> nodes) {
        if (nodes.size() < 2) {
            LOG.info("nop.code.graph-metrics-skip:indexId-internal,metric=COMMUNITY,reason=node-set-too-small");
            return null;
        }
        if (nodes.size() > CodeGraphService.MAX_NODES_FOR_COMMUNITY_DETECTION) {
            LOG.warn("nop.code.graph-metrics-skip:indexId-internal,metric=COMMUNITY,"
                            + "reason=node-count-exceeds-max,nodes={},max={}",
                    nodes.size(), CodeGraphService.MAX_NODES_FOR_COMMUNITY_DETECTION);
            return null;
        }
        LeidenConfig config = LeidenConfig.create()
                .setResolution(0.1)
                .setMaxIterations(10)
                .setTimeoutMs(60000);
        return LeidenDetector.detect(graph, nodes, config);
    }

    List<NopCodeGraphMetric> communityRows(String indexId, CommunityResult result, long computedAt) {
        if (result == null) {
            return List.of();
        }
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

    /**
     * One row per community carrying its cohesion (symbolId placeholder "community:<id>"
     * satisfies the mandatory symbolId column and the (indexId,metricType,symbolId) unique key).
     */
    List<NopCodeGraphMetric> communityInfoRows(String indexId, CommunityResult result, long computedAt) {
        if (result == null) {
            return List.of();
        }
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        for (CommunityInfo community : result.getCommunities()) {
            NopCodeGraphMetric row = newRow(indexId, METRIC_COMMUNITY_INFO,
                    "community:" + community.getId(), computedAt);
            row.setCommunityId(community.getId());
            row.setScore(community.getCohesion());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Single index-level summary row (symbolId placeholder "__summary__") carrying the scalar
     * aggregates of the community run. symbolCount is the full symbol count (including
     * isolated symbols); callGraphNodeCount is the call-graph node set size.
     */
    List<NopCodeGraphMetric> summaryRows(String indexId, CommunityResult result, Set<String> nodes,
                                         SymbolTable symbolTable, long computedAt) {
        NopCodeGraphMetric row = newRow(indexId, METRIC_GRAPH_SUMMARY, "__summary__", computedAt);
        Map<String, Object> ext = new LinkedHashMap<>();
        if (result != null) {
            ext.put("modularity", result.getModularity());
            ext.put("averageCohesion", result.getAverageCohesion());
            ext.put("algorithmUsed", result.getAlgorithmUsed());
        }
        ext.put("callGraphNodeCount", nodes.size());
        ext.put("symbolCount", symbolTable.size());
        row.setExtData(JsonTool.stringify(ext));
        return List.of(row);
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

    /**
     * One row per call-graph node: score=totalDegree, extData carries inDegree/outDegree.
     * Isolated symbols have no call edges and therefore no HUB row.
     */
    List<NopCodeGraphMetric> hubRows(String indexId, CallGraph callGraph, Set<String> nodes,
                                     long computedAt) {
        List<NopCodeGraphMetric> rows = new ArrayList<>();
        for (String nodeId : nodes) {
            int outDegree = callGraph.getCallees(nodeId).size();
            int inDegree = callGraph.getCallers(nodeId).size();
            NopCodeGraphMetric row = newRow(indexId, METRIC_HUB, nodeId, computedAt);
            row.setScore((double) (inDegree + outDegree));
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("inDegree", inDegree);
            ext.put("outDegree", outDegree);
            row.setExtData(JsonTool.stringify(ext));
            rows.add(row);
        }
        return rows;
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

    // W-15 seam: insert-side slice size (F2: instance field + package setter, zero diff default)
    volatile int insertBatchSize = DELETE_BATCH_SIZE;

    void setInsertBatchSizeForTest(int size) {
        this.insertBatchSize = size;
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
                    // W-15 (plan 2565, adjudication J / F2 form #2): symmetric batching on
                    // the insert side — slices of insertBatchSize saves + flush + clear per
                    // slice (mirrors the delete side above; same session, equivalent risk).
                    // Seam: instance field + package setter, default DELETE_BATCH_SIZE.
                    int saved = 0;
                    for (NopCodeGraphMetric row : rows) {
                        dao.saveEntity(row);
                        saved++;
                        if (saved >= insertBatchSize) {
                            session.flush();
                            session.clear();
                            saved = 0;
                        }
                    }
                    if (saved > 0) {
                        session.flush();
                        session.clear();
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
