package io.nop.code.graph.export;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.ExportException;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.graph.community.CommunityDetector;
public class GraphExporter {

    private static final Logger LOG = LoggerFactory.getLogger(GraphExporter.class);

    public String export(CallGraph callGraph, SymbolTable symbolTable, String format,
                         boolean communityView,
                         CommunityDetector.CommunityDetectionResult communities) {
        switch (format.toUpperCase()) {
            case "GRAPHML":
                return exportGraphML(callGraph, symbolTable, communityView, communities);
            case "MERMAID":
                return exportMermaid(callGraph, symbolTable, communityView, communities);
            case "JSON":
                return exportJson(callGraph, symbolTable, communityView, communities);
            default:
                throw new NopException(NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED).param("format", format);
        }
    }

    private String exportGraphML(CallGraph callGraph, SymbolTable symbolTable,
                                  boolean communityView,
                                  CommunityDetector.CommunityDetectionResult communities) {
        Graph<String, DefaultEdge> graph;
        if (communityView && communities != null) {
            graph = buildCommunityGraph(callGraph, communities);
        } else {
            graph = buildDetailGraph(callGraph);
        }

        GraphMLExporter<String, DefaultEdge> exporter = new GraphMLExporter<>(v -> v);

        StringWriter writer = new StringWriter();
        try {
            exporter.exportGraph(graph, writer);
        } catch (ExportException e) {
            throw new NopException(NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED).cause(e);
        }
        return writer.toString();
    }

    private String exportMermaid(CallGraph callGraph, SymbolTable symbolTable,
                                  boolean communityView,
                                  CommunityDetector.CommunityDetectionResult communities) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph LR\n");

        if (communityView && communities != null) {
            Map<String, String> nodeToCommunity = buildNodeToCommunityMap(communities);
            Set<String> edges = new LinkedHashSet<>();

            for (String caller : callGraph.getAllNodeIds()) {
                for (String callee : callGraph.getCallees(caller)) {
                    String srcComm = nodeToCommunity.getOrDefault(caller, caller);
                    String tgtComm = nodeToCommunity.getOrDefault(callee, callee);
                    if (!srcComm.equals(tgtComm)) {
                        edges.add(sanitizeMermaidId(srcComm) + " --> " + sanitizeMermaidId(tgtComm));
                    }
                }
            }

            for (CommunityDetector.Community comm : communities.getCommunities()) {
                String id = sanitizeMermaidId(comm.getId());
                String label = comm.getLabel() != null ? comm.getLabel() : comm.getId();
                sb.append("    ").append(id).append("[\"").append(label)
                        .append(" (").append(comm.getSymbolCount()).append(")\"]\n");
            }
            for (String edge : edges) {
                sb.append("    ").append(edge).append("\n");
            }
        } else {
            for (String caller : callGraph.getAllNodeIds()) {
                for (String callee : callGraph.getCallees(caller)) {
                    String srcLabel = getShortLabel(caller, symbolTable);
                    String tgtLabel = getShortLabel(callee, symbolTable);
                    sb.append("    ").append(sanitizeMermaidId(caller)).append("[\"")
                            .append(srcLabel).append("\"] --> ")
                            .append(sanitizeMermaidId(callee)).append("[\"")
                            .append(tgtLabel).append("\"]\n");
                }
            }
        }

        return sb.toString();
    }

    private String exportJson(CallGraph callGraph, SymbolTable symbolTable,
                               boolean communityView,
                               CommunityDetector.CommunityDetectionResult communities) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        if (communityView && communities != null) {
            Map<String, String> nodeToCommunity = buildNodeToCommunityMap(communities);
            Set<String> seenEdges = new HashSet<>();
            List<String> edgeEntries = new ArrayList<>();

            for (String caller : callGraph.getAllNodeIds()) {
                for (String callee : callGraph.getCallees(caller)) {
                    String srcComm = nodeToCommunity.getOrDefault(caller, caller);
                    String tgtComm = nodeToCommunity.getOrDefault(callee, callee);
                    if (!srcComm.equals(tgtComm)) {
                        String key = srcComm + "->" + tgtComm;
                        if (seenEdges.add(key)) {
                            edgeEntries.add("    {\"source\":\"" + escapeJson(srcComm)
                                    + "\",\"target\":\"" + escapeJson(tgtComm) + "\"}");
                        }
                    }
                }
            }

            sb.append("  \"nodes\": [\n");
            List<CommunityDetector.Community> comms = communities.getCommunities();
            for (int i = 0; i < comms.size(); i++) {
                CommunityDetector.Community c = comms.get(i);
                sb.append("    {\"id\":\"").append(escapeJson(c.getId()))
                        .append("\",\"label\":\"").append(escapeJson(c.getLabel() != null ? c.getLabel() : c.getId()))
                        .append("\",\"size\":").append(c.getSymbolCount())
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
            Set<String> allNodes = callGraph.getAllNodeIds();
            List<String> nodeList = new ArrayList<>(allNodes);
            for (int i = 0; i < nodeList.size(); i++) {
                String nodeId = nodeList.get(i);
                CodeSymbol sym = symbolTable.getById(nodeId);
                String qn = sym != null ? sym.getQualifiedName() : nodeId;
                sb.append("    {\"id\":\"").append(escapeJson(nodeId))
                        .append("\",\"name\":\"").append(escapeJson(qn)).append("\"}");
                if (i < nodeList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            sb.append("  \"edges\": [\n");
            List<String[]> allEdges = new ArrayList<>();
            for (String caller : callGraph.getAllNodeIds()) {
                for (String callee : callGraph.getCallees(caller)) {
                    allEdges.add(new String[]{caller, callee});
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

    private Graph<String, DefaultEdge> buildDetailGraph(CallGraph callGraph) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (String node : callGraph.getAllNodeIds()) {
            graph.addVertex(node);
        }
        for (String caller : callGraph.getAllNodeIds()) {
            for (String callee : callGraph.getCallees(caller)) {
                try {
                    graph.addEdge(caller, callee);
                } catch (IllegalArgumentException e) {
                    LOG.debug("Skipping duplicate edge {} -> {}", caller, callee, e);
                }
            }
        }
        return graph;
    }

    private Graph<String, DefaultEdge> buildCommunityGraph(CallGraph callGraph,
                                                            CommunityDetector.CommunityDetectionResult communities) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<String, String> nodeToCommunity = buildNodeToCommunityMap(communities);

        for (CommunityDetector.Community comm : communities.getCommunities()) {
            graph.addVertex(comm.getId());
        }

        Set<String> seen = new HashSet<>();
        for (String caller : callGraph.getAllNodeIds()) {
            for (String callee : callGraph.getCallees(caller)) {
                String srcComm = nodeToCommunity.get(caller);
                String tgtComm = nodeToCommunity.get(callee);
                if (srcComm != null && tgtComm != null && !srcComm.equals(tgtComm)) {
                    String key = srcComm + "->" + tgtComm;
                    if (seen.add(key)) {
                        try {
                            graph.addEdge(srcComm, tgtComm);
                        } catch (IllegalArgumentException e) {
                            LOG.debug("Skipping duplicate community edge {} -> {}", srcComm, tgtComm, e);
                        }
                    }
                }
            }
        }
        return graph;
    }

    private Map<String, String> buildNodeToCommunityMap(
            CommunityDetector.CommunityDetectionResult communities) {
        Map<String, String> map = new HashMap<>();
        for (CommunityDetector.Community comm : communities.getCommunities()) {
            for (String symbolId : comm.getSymbolIds()) {
                map.put(symbolId, comm.getId());
            }
        }
        return map;
    }

    private String sanitizeMermaidId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String getShortLabel(String nodeId, SymbolTable symbolTable) {
        CodeSymbol sym = symbolTable.getById(nodeId);
        if (sym != null && sym.getQualifiedName() != null) {
            String qn = sym.getQualifiedName();
            int dot = qn.lastIndexOf('.');
            return dot > 0 ? qn.substring(dot + 1) : qn;
        }
        return nodeId.length() > 20 ? nodeId.substring(0, 20) : nodeId;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
