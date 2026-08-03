/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.ResultPartitionType;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Decomposes a {@link JobGraph} into pipelined regions using a union-find
 * (disjoint-set) connected-component algorithm.
 *
 * <p><b>Region cut rule</b>: an edge whose
 * {@link JobEdge#isMaterializationEnabled()} is {@code true} is a region
 * boundary — the two vertices it connects belong to different regions (their
 * lifecycles are decoupled by the materialization point). All other edges are
 * <em>within-region</em> connectors: they union their endpoints into the same
 * region.
 *
 * <p><b>Zero regression</b>: a job graph with no materialization-enabled edges
 * decomposes into exactly one region containing every vertex, matching the
 * all-pipelined single-region invariant established in
 * {@code failover-design.md} §2.1.
 *
 * <p><b>No-Silent-No-Op (#24)</b>: every edge is explicitly classified as
 * either {@link EdgeClassification#WITHIN_REGION} or
 * {@link EdgeClassification#REGION_BOUNDARY}. A {@link ResultPartitionType#BLOCKING}
 * edge — which is never produced by
 * {@code JobGraphGenerator.determinePartitionType()} but exists in the enum —
 * is treated as an unclassifiable edge and fails fast rather than being
 * silently assigned to either bucket (a BLOCKING edge is semantically a region
 * boundary, but the system's contract is that region boundaries are expressed
 * via the materialization marker, not the partition-type enum; encountering a
 * BLOCKING edge indicates a programming error upstream).
 *
 * @see Region
 * @see RegionId
 * @see RegionDecomposition
 */
public final class RegionDecomposer {

    private RegionDecomposer() {
    }

    /**
     * Internal edge classification result. Every edge in the graph is mapped
     * to exactly one of these values; there is no default/silent bucket.
     */
    private enum EdgeClassification {
        /** Edge connects two vertices in the same region (not a cut point). */
        WITHIN_REGION,
        /** Edge is a region boundary (materialization-enabled). */
        REGION_BOUNDARY
    }

    /**
     * Decomposes the given job graph into pipelined regions.
     *
     * <p>The decomposition assigns region IDs of the form {@code "region-<n>"}
     * where {@code <n>} is the zero-based index in the order the regions first
     * appear when iterating the graph's vertex map (insertion order of
     * {@link JobGraph#addVertex}).
     *
     * @param jobGraph the job graph to decompose (must not be null and must
     *                 contain at least one vertex)
     * @return the decomposition (never null)
     * @throws StreamException if the graph is empty or an edge references an
     *                         unknown vertex or an unclassifiable edge is found
     */
    public static RegionDecomposition decompose(JobGraph jobGraph) {
        if (jobGraph == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Cannot decompose a null JobGraph");
        }
        if (jobGraph.getNumberOfVertices() == 0) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Cannot decompose an empty JobGraph (no vertices)");
        }

        Set<String> vertexIds = jobGraph.getVertices().keySet();

        // --- Union-Find initialization: each vertex starts as its own root ---
        Map<String, String> parent = new LinkedHashMap<>();
        for (String vertexId : vertexIds) {
            parent.put(vertexId, vertexId);
        }

        // --- Classify each edge and union within-region endpoints ---
        for (JobEdge edge : jobGraph.getEdges()) {
            String source = edge.getSourceVertex();
            String target = edge.getTargetVertex();

            // Defensive: verify the edge references known vertices. JobGraph.addEdge
            // already validates this, but a defensively-built or mutated graph could
            // violate the invariant — fail fast rather than producing a wrong result.
            if (!parent.containsKey(source)) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Edge references unknown source vertex: " + source);
            }
            if (!parent.containsKey(target)) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Edge references unknown target vertex: " + target);
            }

            EdgeClassification classification = classify(edge);
            if (classification == EdgeClassification.WITHIN_REGION) {
                union(parent, source, target);
            }
            // REGION_BOUNDARY: do NOT union — the two vertices stay in separate sets.
        }

        // --- Group vertices by their union-find root ---
        // Preserve the insertion order of vertices so region numbering is stable.
        Map<String, List<String>> rootToMembers = new LinkedHashMap<>();
        for (String vertexId : vertexIds) {
            String root = find(parent, vertexId);
            rootToMembers.computeIfAbsent(root, k -> new ArrayList<>()).add(vertexId);
        }

        // --- Assign region IDs and build the result ---
        List<Region> regions = new ArrayList<>();
        Map<String, RegionId> vertexToRegion = new LinkedHashMap<>();
        int regionIndex = 0;
        for (Map.Entry<String, List<String>> entry : rootToMembers.entrySet()) {
            RegionId regionId = new RegionId("region-" + regionIndex);
            Set<String> members = new LinkedHashSet<>(entry.getValue());
            regions.add(new Region(regionId, members));
            for (String vertexId : entry.getValue()) {
                vertexToRegion.put(vertexId, regionId);
            }
            regionIndex++;
        }

        return new RegionDecomposition(regions, vertexToRegion);
    }

    /**
     * Classifies an edge as within-region or region-boundary. This is the
     * single decision point for the decomposition; every edge is mapped to
     * exactly one bucket.
     *
     * <p>No-Silent-No-Op (#24): a {@link ResultPartitionType#BLOCKING} edge is
     * treated as unclassifiable because the system's contract (established in
     * successor plan 1) is that region boundaries are expressed via the
     * materialization marker, not the partition-type enum. The
     * {@code determinePartitionType} path never produces BLOCKING; encountering
     * one indicates an upstream programming error. Failing fast here prevents a
     * wrong decomposition from being silently propagated downstream.
     */
    private static EdgeClassification classify(JobEdge edge) {
        ResultPartitionType type = edge.getPartitionType();
        if (type == ResultPartitionType.BLOCKING) {
            // Fail-fast: BLOCKING edges are not produced by the system. If one
            // appears, it is a programming error. We refuse to silently treat
            // it as either within-region (wrong — it IS a boundary) or
            // region-boundary (the contract says boundaries use the materialization
            // marker, not the enum). The caller must fix the upstream producer.
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Region decomposition encountered a BLOCKING partition-type edge "
                            + edge.getSourceVertex() + "->" + edge.getTargetVertex()
                            + ". determinePartitionType never produces BLOCKING; region boundaries must "
                            + "be expressed via JobEdge.setMaterializationEnabled(true), not the "
                            + "partition-type enum. This indicates an upstream programming error.");
        }
        if (edge.isMaterializationEnabled()) {
            return EdgeClassification.REGION_BOUNDARY;
        }
        return EdgeClassification.WITHIN_REGION;
    }

    // --- Union-Find helpers (path compression + union by root identity) ---

    private static String find(Map<String, String> parent, String vertex) {
        String current = vertex;
        // Walk to the root
        while (!parent.get(current).equals(current)) {
            current = parent.get(current);
        }
        String root = current;
        // Path compression
        current = vertex;
        while (!parent.get(current).equals(root)) {
            String next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }
}
