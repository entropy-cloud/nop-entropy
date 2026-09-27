package io.nop.code.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * Typed multi-family projection of code relations onto {@link IGraph}.
 *
 * Covers CALLS / INHERITANCE / ANNOTATION / SEMANTIC edge families. Edge.type carries the
 * family ({@link CodeEdgeData#getEdgeType()}); domain attributes (relationType / confidence /
 * provenance / directed / sourceFilePath / targetFilePath) are exposed via {@link Edge#getAttrs()},
 * with null fields omitted. The attrs relationType is the refinement when present, the family
 * otherwise, so downstream scoring can distinguish EXTENDS from a bare INHERITANCE edge.
 *
 * Duplicate edges (same sourceId/targetId/edgeType/relationType) keep the first occurrence.
 * Edge equality ignores attrs, so projections remain safe for set operations (GraphDiffer).
 *
 * Consumers needing typed edge attributes (surprise scoring, question generation, stateless
 * analysis migration) must read this view, not {@link CodeCallGraph} which stays a CALLS-only
 * topology projection for existing graph algorithms.
 */
public class CodeRelationGraph implements IGraph {

    private final Map<String, List<Edge>> outEdges;
    private final Map<String, List<Edge>> inEdges;
    private final Set<String> nodeIds;

    public CodeRelationGraph(List<CodeEdgeData> edges) {
        Map<String, List<Edge>> out = new HashMap<>();
        Map<String, List<Edge>> in = new HashMap<>();
        Set<String> nodes = new HashSet<>();
        Set<String> seen = new HashSet<>();

        if (edges != null) {
            for (CodeEdgeData data : edges) {
                String relation = data.getRelationType() != null ? data.getRelationType() : data.getEdgeType();
                String dedupKey = data.getSourceId() + "->" + data.getTargetId() + "|"
                        + data.getEdgeType() + "|" + relation;
                if (!seen.add(dedupKey)) {
                    continue;
                }

                Edge edge = new Edge(data.getSourceId(), data.getTargetId(), data.getWeight(), data.getEdgeType());
                edge.setAttrs(buildAttrs(data, relation));
                out.computeIfAbsent(data.getSourceId(), k -> new ArrayList<>()).add(edge);
                in.computeIfAbsent(data.getTargetId(), k -> new ArrayList<>()).add(edge);
                nodes.add(data.getSourceId());
                nodes.add(data.getTargetId());
            }
        }

        this.outEdges = out;
        this.inEdges = in;
        this.nodeIds = nodes;
    }

    private static Map<String, Object> buildAttrs(CodeEdgeData data, String relation) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("relationType", relation);
        putIfNotNull(attrs, "confidence", data.getConfidence());
        putIfNotNull(attrs, "provenance", data.getProvenance());
        attrs.put("directed", data.isDirected());
        putIfNotNull(attrs, "sourceFilePath", data.getSourceFilePath());
        putIfNotNull(attrs, "targetFilePath", data.getTargetFilePath());
        return attrs;
    }

    private static void putIfNotNull(Map<String, Object> attrs, String key, String value) {
        if (value != null && !value.isEmpty()) {
            attrs.put(key, value);
        }
    }

    /**
     * All node ids appearing on either side of any edge. Concrete-class accessor — the
     * {@link IGraph} contract has no node enumeration; algorithm callers pass this set on.
     */
    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(nodeIds);
    }

    @Override
    public List<Edge> getOutEdges(String nodeId) {
        List<Edge> edges = outEdges.get(nodeId);
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    @Override
    public List<Edge> getInEdges(String nodeId) {
        List<Edge> edges = inEdges.get(nodeId);
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }
}
