package io.nop.code.graph.critical;

import java.util.*;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;

public class CriticalNodeAnalyzer {

    public CriticalNodeResult analyze(CallGraph callGraph, SymbolTable symbolTable, int topN) {
        CriticalNodeResult result = new CriticalNodeResult();
        Set<String> allNodes = callGraph.getAllNodeIds();
        result.setTotalNodes(allNodes.size());
        result.setTopN(topN);

        result.setHubNodes(computeHubNodes(callGraph, symbolTable, topN));
        result.setBridgeNodes(computeBridgeNodes(callGraph, symbolTable, topN));

        return result;
    }

    private List<CriticalNodeResult.NodeScore> computeHubNodes(
            CallGraph callGraph, SymbolTable symbolTable, int topN) {
        Map<String, int[]> degrees = new HashMap<>();

        for (String caller : callGraph.getAllNodeIds()) {
            int[] deg = degrees.computeIfAbsent(caller, k -> new int[2]);
            List<String> callees = callGraph.getCallees(caller);
            deg[1] += callees.size();
            for (String callee : callees) {
                int[] calleeDeg = degrees.computeIfAbsent(callee, k -> new int[2]);
                calleeDeg[0]++;
            }
        }

        return degrees.entrySet().stream()
                .map(entry -> {
                    String nodeId = entry.getKey();
                    int inDeg = entry.getValue()[0];
                    int outDeg = entry.getValue()[1];
                    int totalDeg = inDeg + outDeg;

                    CriticalNodeResult.NodeScore ns = new CriticalNodeResult.NodeScore();
                    ns.setSymbolId(nodeId);
                    ns.setInDegree(inDeg);
                    ns.setOutDegree(outDeg);
                    ns.setTotalDegree(totalDeg);
                    ns.setScore(totalDeg);

                    CodeSymbol sym = symbolTable.getById(nodeId);
                    if (sym != null) {
                        ns.setQualifiedName(sym.getQualifiedName());
                    } else {
                        ns.setQualifiedName(nodeId);
                    }
                    return ns;
                })
                .sorted(Comparator.comparingDouble(CriticalNodeResult.NodeScore::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    private List<CriticalNodeResult.NodeScore> computeBridgeNodes(
            CallGraph callGraph, SymbolTable symbolTable, int topN) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (String node : callGraph.getAllNodeIds()) {
            graph.addVertex(node);
        }
        for (String caller : callGraph.getAllNodeIds()) {
            for (String callee : callGraph.getCallees(caller)) {
                try {
                    graph.addEdge(caller, callee);
                } catch (IllegalArgumentException e) {
                    // JGraphT addEdge throws on duplicate edges, safe to ignore
                }
            }
        }

        BetweennessCentrality<String, DefaultEdge> bc = new BetweennessCentrality<>(graph, false);
        Map<String, Double> scores = bc.getScores();

        return scores.entrySet().stream()
                .map(entry -> {
                    String nodeId = entry.getKey();
                    double betweenness = entry.getValue();

                    CriticalNodeResult.NodeScore ns = new CriticalNodeResult.NodeScore();
                    ns.setSymbolId(nodeId);
                    ns.setScore(betweenness);

                    List<String> callees = callGraph.getCallees(nodeId);
                    List<String> callers = callGraph.getCallers(nodeId);
                    ns.setInDegree(callers.size());
                    ns.setOutDegree(callees.size());
                    ns.setTotalDegree(callers.size() + callees.size());

                    CodeSymbol sym = symbolTable.getById(nodeId);
                    if (sym != null) {
                        ns.setQualifiedName(sym.getQualifiedName());
                    } else {
                        ns.setQualifiedName(nodeId);
                    }
                    return ns;
                })
                .sorted(Comparator.comparingDouble(CriticalNodeResult.NodeScore::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }
}
