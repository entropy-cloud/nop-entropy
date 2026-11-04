package io.nop.code.service.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

/**
 * Read-only access to materialized global graph metrics ({@link NopCodeGraphMetric}).
 *
 * Never computes: when {@link #hasMaterialized} returns false (never materialized, or
 * invalidated by an actual incremental change), consumers fall back to their own degradation
 * path per graph-discovery §3.0. A false for BETWEENNESS on very large graphs is the expected
 * >10000-node skip, not a failure.
 *
 * Row coverage mirrors the materializer: COMMUNITY/BETWEENNESS/PAGE_RANK cover the call-graph
 * node set; ENTRY_POINT covers METHOD/CONSTRUCTOR symbols. Lookup misses for symbols outside
 * those sets are normal.
 *
 * Every family read goes through {@link #findByMetric} with an explicit row cap
 * (default {@value #DEFAULT_MAX_ROWS_PER_METRIC_FAMILY}): rank-ordered families
 * (ENTRY_POINT/BETWEENNESS) push the rankNo ordering down to the query so truncation keeps
 * the top-rank rows; HUB pushes score-descending (score = totalDegree) so truncation keeps
 * the top-degree rows. Reaching the cap is observable via a structured WARN — a truncated
 * family yields a partial row set (missing-type degradation), which is distinct from the
 * zero-row COMMUNITY degradation that triggers the materialize-first self-heal.
 */
public class GraphMetricStore {

    public static final String METRIC_COMMUNITY = "COMMUNITY";
    public static final String METRIC_BETWEENNESS = "BETWEENNESS";
    public static final String METRIC_PAGE_RANK = "PAGE_RANK";
    public static final String METRIC_ENTRY_POINT = "ENTRY_POINT";
    public static final String METRIC_HUB = "HUB";
    public static final String METRIC_COMMUNITY_INFO = "COMMUNITY_INFO";
    public static final String METRIC_GRAPH_SUMMARY = "GRAPH_SUMMARY";

    private static final Logger LOG = LoggerFactory.getLogger(GraphMetricStore.class);

    /**
     * Row cap per metric family (aligned with the BETWEENNESS_MAX_NODES=10000 convention —
     * same-family separate constant, not a cross-class reference; this constant is the single
     * source of truth for the finder row cap, downstream batches must not introduce a second).
     * Test seam: instance field + package-visible setter, production default = this
     * compile-time constant. COMMUNITY/COMMUNITY_INFO/BETWEENNESS/GRAPH_SUMMARY are bounded
     * below the cap by the materializer's own guards (cap unreachable = defense in depth);
     * ENTRY_POINT/PAGE_RANK/HUB can exceed it (cap reachable = truncation + WARN).
     */
    static final int DEFAULT_MAX_ROWS_PER_METRIC_FAMILY = 10000;

    private int maxRowsPerFamily = DEFAULT_MAX_ROWS_PER_METRIC_FAMILY;

    private final IDaoProvider daoProvider;

    public GraphMetricStore(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * Test seam — package-visible by design: GraphMetricStore is direct-new assembled (never an
     * IoC bean), so an unannotated setter is never wired in production.
     */
    void setMaxRowsPerFamily(int maxRowsPerFamily) {
        if (maxRowsPerFamily <= 0)
            throw new IllegalArgumentException("maxRowsPerFamily must be positive, got: " + maxRowsPerFamily);
        this.maxRowsPerFamily = maxRowsPerFamily;
    }

    public boolean hasMaterialized(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq("metricType", metricType)));
        query.setLimit(1);
        return !dao.findPageByQuery(query).isEmpty();
    }

    public Map<String, Integer> loadCommunities(String indexId) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_COMMUNITY);
        Map<String, Integer> result = new HashMap<>();
        for (NopCodeGraphMetric row : rows) {
            if (row.getCommunityId() != null) {
                result.put(row.getSymbolId(), row.getCommunityId());
            }
        }
        return result;
    }

    public Map<String, Double> loadScores(String indexId, String metricType) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, metricType);
        Map<String, Double> result = new HashMap<>();
        for (NopCodeGraphMetric row : rows) {
            if (row.getScore() != null) {
                result.put(row.getSymbolId(), row.getScore());
            }
        }
        return result;
    }

    /**
     * Per-node hub degrees keyed by symbolId. Missing entry for a symbol = no call edges
     * (isolated), matching the materializer coverage. The query orders by score descending
     * (score = totalDegree) so the row cap keeps the top-degree rows — top-degree/god-node
     * selection consumers keep degree correctness under truncation.
     */
    public Map<String, int[]> loadHubs(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_HUB, "score", true);
        Map<String, int[]> result = new HashMap<>();
        for (NopCodeGraphMetric row : rows) {
            Map<String, Object> ext = parseExt(row.getExtData());
            int in = asInt(ext.get("inDegree"));
            int out = asInt(ext.get("outDegree"));
            result.put(row.getSymbolId(), new int[]{in + out, in, out});
        }
        return result;
    }

    /** Community id -> cohesion, from the per-community COMMUNITY_INFO rows. */
    public Map<Integer, Double> loadCommunityInfo(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_COMMUNITY_INFO);
        Map<Integer, Double> result = new HashMap<>();
        for (NopCodeGraphMetric row : rows) {
            if (row.getCommunityId() != null && row.getScore() != null) {
                result.put(row.getCommunityId(), row.getScore());
            }
        }
        return result;
    }

    /** The single GRAPH_SUMMARY row's ext data, or null when absent. */
    public Map<String, Object> loadSummary(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_GRAPH_SUMMARY);
        if (rows.isEmpty()) {
            return null;
        }
        return parseExt(rows.get(0).getExtData());
    }

    /**
     * Whether every family required by the given query method is materialized. BETWEENNESS is
     * intentionally not required by any method: its absence on >10000-node graphs is the
     * agreed per-family degradation (bridgeNodes empty), not a fallback trigger.
     */
    public boolean hasRequiredFamilies(String indexId, String... metricTypes) {
        for (String metricType : metricTypes) {
            if (!hasMaterialized(indexId, metricType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ENTRY_POINT rows in materialized rank order (rankNo ascending). Ties keep the
     * materialized generation order — readers must not re-sort by score. The rankNo ordering
     * is pushed down to the query so the row cap keeps the top-rank rows; the in-memory
     * stable re-sort below is retained as a secondary guarantee.
     */
    public List<NopCodeGraphMetric> loadEntryPoints(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_ENTRY_POINT, "rankNo", false);
        rows.sort(java.util.Comparator.comparingInt(
                r -> r.getRankNo() != null ? r.getRankNo() : Integer.MAX_VALUE));
        return rows;
    }

    /** BETWEENNESS rows in rank order; empty on >10000-node graphs (agreed degradation). */
    public List<NopCodeGraphMetric> loadBetweenness(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_BETWEENNESS, "rankNo", false);
        rows.sort(java.util.Comparator.comparingInt(
                r -> r.getRankNo() != null ? r.getRankNo() : Integer.MAX_VALUE));
        return rows;
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseExt(String extData) {
        if (extData == null || extData.isEmpty()) {
            return new HashMap<>();
        }
        Object parsed = JsonTool.parseNonStrict(extData);
        return parsed instanceof Map ? (Map<String, Object>) parsed : new HashMap<>();
    }

    /**
     * Shared root of every family LOAD path (7 public finders): explicit row cap + truncation
     * observability. {@link #hasMaterialized} keeps its independent setLimit(1) presence-check
     * path and is unaffected. When {@code orderField} is given, the ordering is pushed down to
     * the query so the cap keeps the top rows (rankNo ascending / score descending).
     */
    private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType) {
        return findByMetric(indexId, metricType, null, false);
    }

    private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType,
                                                  String orderField, boolean desc) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq("metricType", metricType)));
        if (orderField != null) {
            query.addOrderField(orderField, desc);
        }
        query.setLimit(maxRowsPerFamily);
        List<NopCodeGraphMetric> rows = dao.findPageByQuery(query);
        warnIfCapped(rows.size(), maxRowsPerFamily, metricType + "/" + indexId);
        return rows;
    }

    // aligned with CodeQueryService.warnIfCapped: size == cap means more rows may exist —
    // surface the truncation instead of silently returning a partial family
    private static void warnIfCapped(int resultSize, int cap, String context) {
        if (cap > 0 && resultSize >= cap) {
            LOG.warn("Metric family rows capped at {} for {}; result may be incomplete"
                    + " (more rows may exist)", cap, context);
        }
    }
}
