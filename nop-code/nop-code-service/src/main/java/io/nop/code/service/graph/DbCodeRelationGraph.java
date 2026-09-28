package io.nop.code.service.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * N6.2: database-backed {@link IGraph} — per-node point queries against the four relation
 * tables (calls / inheritance / annotation usages / semantic edges) instead of a full
 * preloaded edge set (N6.1 decision: portable SQL, point queries + bounded traversal pushed
 * down; unbounded walks and global algorithms stay application-side).
 *
 * <p>Row mapping and attrs semantics are REUSED from {@link CodeRelationGraphLoader}'s
 * static mappers and {@link CodeRelationGraph}'s construction (dedup + attrs), so the two
 * backends cannot drift. Equivalence with the in-memory backend is pinned by
 * TestDbCodeRelationGraphEquivalence.
 *
 * <p>There is deliberately no nodeIds() accessor: enumerating nodes requires a full load,
 * which is exactly what this backend pushes down. Consumers pass the node set in.
 */
public class DbCodeRelationGraph implements IGraph {
    private static final Logger LOG = LoggerFactory.getLogger(DbCodeRelationGraph.class);
    private static final int POINT_QUERY_BATCH = 5000;

    private final String indexId;
    private final IDaoProvider daoProvider;
    private final Function<String, String> filePathResolver;

    public DbCodeRelationGraph(String indexId, IDaoProvider daoProvider,
                               Function<String, String> filePathResolver) {
        this.indexId = indexId;
        this.daoProvider = daoProvider;
        this.filePathResolver = filePathResolver;
    }

    @Override
    public List<Edge> getOutEdges(String nodeId) {
        return edgesAt(nodeId, true);
    }

    @Override
    public List<Edge> getInEdges(String nodeId) {
        return edgesAt(nodeId, false);
    }

    private List<Edge> edgesAt(String nodeId, boolean outgoing) {
        if (nodeId == null || nodeId.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.addAll(callsAt(nodeId, outgoing));
        edges.addAll(inheritancesAt(nodeId, outgoing));
        edges.addAll(annotationsAt(nodeId, outgoing));
        edges.addAll(semanticsAt(nodeId, outgoing));
        // Construction-scoped CodeRelationGraph reuses the dedup + attrs semantics verbatim;
        // the returned list for this node is identical to the in-memory backend's slice.
        CodeRelationGraph projection = new CodeRelationGraph(edges);
        return outgoing ? projection.getOutEdges(nodeId) : projection.getInEdges(nodeId);
    }

    private List<CodeEdgeData> callsAt(String nodeId, boolean outgoing) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeCall call : pointQuery(dao, outgoing ? "callerId" : "calleeId", nodeId)) {
            result.add(CodeRelationGraphLoader.toCallEdge(call, filePathResolver));
        }
        return result;
    }

    private List<CodeEdgeData> inheritancesAt(String nodeId, boolean outgoing) {
        IEntityDao<NopCodeInheritance> dao = daoProvider.daoFor(NopCodeInheritance.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeInheritance inh : pointQuery(dao, outgoing ? "subTypeId" : "superTypeId", nodeId)) {
            result.add(CodeRelationGraphLoader.toInheritanceEdge(inh, filePathResolver));
        }
        return result;
    }

    private List<CodeEdgeData> annotationsAt(String nodeId, boolean outgoing) {
        IEntityDao<NopCodeAnnotationUsage> dao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeAnnotationUsage usage : pointQuery(dao, outgoing ? "annotatedSymbolId" : "annotationTypeId", nodeId)) {
            result.add(CodeRelationGraphLoader.toAnnotationEdge(usage, filePathResolver));
        }
        return result;
    }

    private List<CodeEdgeData> semanticsAt(String nodeId, boolean outgoing) {
        IEntityDao<NopCodeSemanticEdge> dao = daoProvider.daoFor(NopCodeSemanticEdge.class);
        List<CodeEdgeData> result = new ArrayList<>();
        for (NopCodeSemanticEdge edge : pointQuery(dao, outgoing ? "sourceSymbolId" : "targetSymbolId", nodeId)) {
            result.add(CodeRelationGraphLoader.toSemanticEdge(edge, filePathResolver));
        }
        return result;
    }

    private <T extends IDaoEntity> List<T> pointQuery(IEntityDao<T> dao, String endpointField,
                                                      String nodeId) {
        List<T> all = new ArrayList<>();
        long offset = 0;
        while (true) {
            QueryBean query = new QueryBean();
            // N6.1/N6.2: the point query is scoped to the index — same node ids can exist in
            // multiple indexes sharing one database, and edges must never bleed across them
            query.addFilter(FilterBeans.and(
                    FilterBeans.eq("indexId", indexId),
                    FilterBeans.eq(endpointField, nodeId)));
            query.setOffset(offset);
            query.setLimit(POINT_QUERY_BATCH);
            List<T> batch = dao.findPageByQuery(query);
            all.addAll(batch);
            if (batch.size() < POINT_QUERY_BATCH)
                break;
            offset += POINT_QUERY_BATCH;
        }
        return all;
    }

    /**
     * N6.1 bounded traversal (portable form): BFS via repeated point queries with an
     * application-side visited set — hop-by-hop pushdown, no CTE/dialect dependency.
     *
     * @param maxDepth must be >= 1; the start nodes count as depth 0
     * @return the visited node ids (including the starts) in first-visit order
     */
    public Set<String> traverseBounded(Iterable<String> startIds, int maxDepth, Direction direction) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be >= 1, got: " + maxDepth);
        }
        Set<String> visited = new LinkedHashSet<>();
        Set<String> frontier = new LinkedHashSet<>();
        for (String start : startIds) {
            if (start != null && !start.isEmpty() && visited.add(start)) {
                frontier.add(start);
            }
        }
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            Set<String> next = new LinkedHashSet<>();
            for (String nodeId : frontier) {
                if (direction != Direction.IN) {
                    for (Edge edge : edgesAt(nodeId, true)) {
                        if (visited.add(edge.getTargetId())) {
                            next.add(edge.getTargetId());
                        }
                    }
                }
                if (direction != Direction.OUT) {
                    for (Edge edge : edgesAt(nodeId, false)) {
                        if (visited.add(edge.getSourceId())) {
                            next.add(edge.getSourceId());
                        }
                    }
                }
            }
            frontier = next;
        }
        return visited;
    }

    public enum Direction {
        OUT, IN, BOTH
    }
}
