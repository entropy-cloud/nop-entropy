package io.nop.code.service.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.dao.entity.NopCodeGraphMetric;
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

    private List<NopCodeGraphMetric> findByMetric(String indexId, String metricType) {
        IEntityDao<NopCodeGraphMetric> dao = daoProvider.daoFor(NopCodeGraphMetric.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                FilterBeans.eq("indexId", indexId),
                FilterBeans.eq("metricType", metricType)));
        return dao.findPageByQuery(query);
    }
}
