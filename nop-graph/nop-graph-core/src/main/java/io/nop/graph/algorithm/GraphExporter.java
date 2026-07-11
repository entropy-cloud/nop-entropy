package io.nop.graph.algorithm;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.ExportException;
import org.jgrapht.nio.graphml.GraphMLExporter;

import io.nop.api.core.exceptions.NopException;
import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 图导出算法，支持 GraphML / Mermaid / JSON 三种格式。
 *
 * communityView 模式将节点聚合为社区视图。
 */
public final class GraphExporter {

    private GraphExporter() {
    }

    /**
     * 导出图（无社区视图）。
     */
    public static String export(IGraph graph, Set<String> nodes, String format) {
        return export(graph, nodes, format, null);
    }

    /**
     * 导出图（可选社区视图）。
     *
     * @param graph     图
     * @param nodes     节点集
     * @param format    格式: GRAPHML / MERMAID / JSON
     * @param communities 社区检测结果（null 表示不做社区视图）
     */
    public static String export(IGraph graph, Set<String> nodes, String format,
                                 CommunityResult communities) {
        if (graph == null || nodes == null || format == null) {
            throw new IllegalArgumentException("graph, nodes, and format must not be null");
        }

        switch (format.toUpperCase()) {
            case "GRAPHML":
                return exportGraphML(graph, nodes, communities);
            case "MERMAID":
                return exportMermaid(graph, nodes, communities);
            case "JSON":
                return exportJson(graph, nodes, communities);
            default:
                throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    private static String exportGraphML(IGraph graph, Set<String> nodes,
                                         CommunityResult communities) {
        Graph<String, DefaultEdge> jgraph;
        if (communities != null) {
            jgraph = buildCommunityGraph(graph, nodes, communities);
        } else {
            jgraph = buildDetailGraph(graph, nodes);
        }

        GraphMLExporter<String, DefaultEdge> exporter = new GraphMLExporter<>(v -> v);
        StringWriter writer = new StringWriter();
        try {
            exporter.exportGraph(jgraph, writer);
        } catch (ExportException e) {
            throw new NopException("nop.err.graph.export-failed", e, false, true);
        }
        return writer.toString();
    }

    private static String exportMermaid(IGraph graph, Set<String> nodes,
                                         CommunityResult communities) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph LR\n");

        if (communities != null) {
            Map<String, Integer> nodeToCommunity = buildNodeToCommunityMap(communities);
            Set<String> edges = new LinkedHashSet<>();

            for (String node : nodes) {
                for (Edge edge : graph.getOutEdges(node)) {
                    String target = edge.getTargetId();
                    if (nodes.contains(target)) {
                        int srcComm = nodeToCommunity.getOrDefault(node, -1);
                        int tgtComm = nodeToCommunity.getOrDefault(target, -1);
                        if (srcComm != tgtComm) {
                            edges.add("comm_" + srcComm + " --> comm_" + tgtComm);
                        }
                    }
                }
            }

            for (io.nop.graph.api.CommunityInfo comm : communities.getCommunities()) {
                sb.append("    comm_").append(comm.getId())
                        .append("[\"Community ").append(comm.getId())
                        .append(" (").append(comm.getNodeCount()).append(")\"]\n");
            }
            for (String edge : edges) {
                sb.append("    ").append(edge).append("\n");
            }
        } else {
            for (String node : nodes) {
                for (Edge edge : graph.getOutEdges(node)) {
                    String target = edge.getTargetId();
                    if (nodes.contains(target)) {
                        sb.append("    ").append(sanitizeMermaidId(node))
                                .append("[\"").append(shortId(node)).append("\"] --> ")
                                .append(sanitizeMermaidId(target))
                                .append("[\"").append(shortId(target)).append("\"]\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String exportJson(IGraph graph, Set<String> nodes,
                                      CommunityResult communities) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        if (communities != null) {
            Map<String, Integer> nodeToCommunity = buildNodeToCommunityMap(communities);
            Set<String> seenEdges = new HashSet<>();
            List<String> edgeEntries = new ArrayList<>();

            for (String node : nodes) {
                for (Edge edge : graph.getOutEdges(node)) {
                    String target = edge.getTargetId();
                    if (nodes.contains(target)) {
                        int srcComm = nodeToCommunity.getOrDefault(node, -1);
                        int tgtComm = nodeToCommunity.getOrDefault(target, -1);
                        if (srcComm != tgtComm) {
                            String key = srcComm + "->" + tgtComm;
                            if (seenEdges.add(key)) {
                                edgeEntries.add("    {\"source\":\"comm_" + srcComm
                                        + "\",\"target\":\"comm_" + tgtComm + "\"}");
                            }
                        }
                    }
                }
            }

            sb.append("  \"nodes\": [\n");
            List<io.nop.graph.api.CommunityInfo> comms = communities.getCommunities();
            for (int i = 0; i < comms.size(); i++) {
                io.nop.graph.api.CommunityInfo c = comms.get(i);
                sb.append("    {\"id\":\"comm_").append(c.getId())
                        .append("\",\"size\":").append(c.getNodeCount())
                        .append(",\"cohesion\":").append(String.format("%.4f", c.getCohesion()))
                        .append("}");
                if (i < comms.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            sb.append("  \"edges\": [\n");
            for (int i = 0; i < edgeEntries.size(); i++) {
                sb.append(edgeEntries.get(i));
                if (i < edgeEntries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
        } else {
            sb.append("  \"nodes\": [\n");
            List<String> nodeList = new ArrayList<>(nodes);
            for (int i = 0; i < nodeList.size(); i++) {
                sb.append("    {\"id\":\"").append(escapeJson(nodeList.get(i))).append("\"}");
                if (i < nodeList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            sb.append("  \"edges\": [\n");
            List<String[]> allEdges = new ArrayList<>();
            for (String node : nodes) {
                for (Edge edge : graph.getOutEdges(node)) {
                    if (nodes.contains(edge.getTargetId())) {
                        allEdges.add(new String[]{node, edge.getTargetId()});
                    }
                }
            }
            for (int i = 0; i < allEdges.size(); i++) {
                sb.append("    {\"source\":\"").append(escapeJson(allEdges.get(i)[0]))
                        .append("\",\"target\":\"").append(escapeJson(allEdges.get(i)[1])).append("\"}");
                if (i < allEdges.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private static Graph<String, DefaultEdge> buildDetailGraph(IGraph graph, Set<String> nodes) {
        Graph<String, DefaultEdge> jgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String node : nodes) {
            jgraph.addVertex(node);
        }
        for (String node : nodes) {
            for (Edge edge : graph.getOutEdges(node)) {
                if (nodes.contains(edge.getTargetId())) {
                    try {
                        jgraph.addEdge(node, edge.getTargetId());
                    } catch (IllegalArgumentException e) {
                        // skip duplicate
                    }
                }
            }
        }
        return jgraph;
    }

    private static Graph<String, DefaultEdge> buildCommunityGraph(
            IGraph graph, Set<String> nodes, CommunityResult communities) {
        Graph<String, DefaultEdge> jgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<String, Integer> nodeToCommunity = buildNodeToCommunityMap(communities);

        for (io.nop.graph.api.CommunityInfo comm : communities.getCommunities()) {
            jgraph.addVertex("comm_" + comm.getId());
        }

        Set<String> seen = new HashSet<>();
        for (String node : nodes) {
            for (Edge edge : graph.getOutEdges(node)) {
                String target = edge.getTargetId();
                if (nodes.contains(target)) {
                    Integer srcComm = nodeToCommunity.get(node);
                    Integer tgtComm = nodeToCommunity.get(target);
                    if (srcComm != null && tgtComm != null && !srcComm.equals(tgtComm)) {
                        String key = srcComm + "->" + tgtComm;
                        if (seen.add(key)) {
                            try {
                                jgraph.addEdge("comm_" + srcComm, "comm_" + tgtComm);
                            } catch (IllegalArgumentException e) {
                                // skip
                            }
                        }
                    }
                }
            }
        }
        return jgraph;
    }

    private static Map<String, Integer> buildNodeToCommunityMap(CommunityResult communities) {
        Map<String, Integer> map = new HashMap<>();
        for (io.nop.graph.api.CommunityInfo comm : communities.getCommunities()) {
            for (String nodeId : comm.getNodeIds()) {
                map.put(nodeId, comm.getId());
            }
        }
        return map;
    }

    private static String sanitizeMermaidId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String shortId(String id) {
        int dot = id.lastIndexOf('.');
        return dot > 0 ? id.substring(dot + 1) : id;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
