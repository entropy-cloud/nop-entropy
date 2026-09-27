package io.nop.code.service.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
public class GraphMetricStore {

    public static final String METRIC_COMMUNITY = "COMMUNITY";
    public static final String METRIC_BETWEENNESS = "BETWEENNESS";
    public static final String METRIC_PAGE_RANK = "PAGE_RANK";
    public static final String METRIC_ENTRY_POINT = "ENTRY_POINT";
    public static final String METRIC_HUB = "HUB";
    public static final String METRIC_COMMUNITY_INFO = "COMMUNITY_INFO";
    public static final String METRIC_GRAPH_SUMMARY = "GRAPH_SUMMARY";

    private final IDaoProvider daoProvider;

    public GraphMetricStore(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
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
     * (isolated), matching the materializer coverage.
     */
    public Map<String, int[]> loadHubs(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_HUB);
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
     * materialized generation order — readers must not re-sort by score.
     */
    public List<NopCodeGraphMetric> loadEntryPoints(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_ENTRY_POINT);
        rows.sort(java.util.Comparator.comparingInt(
                r -> r.getRankNo() != null ? r.getRankNo() : Integer.MAX_VALUE));
        return rows;
    }

    /** BETWEENNESS rows in rank order; empty on >10000-node graphs (agreed degradation). */
    public List<NopCodeGraphMetric> loadBetweenness(String indexId) {
        List<NopCodeGraphMetric> rows = findByMetric(indexId, METRIC_BETWEENNESS);
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

    private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq("metricType", metricType)));
        return dao.findPageByQuery(query);
    }
}
