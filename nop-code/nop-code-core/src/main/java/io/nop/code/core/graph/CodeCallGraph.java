package io.nop.code.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.graph.api.Edge;
import io.nop.graph.api.IGraph;

/**
 * 将 CallGraph 适配为 IGraph 接口。
 *
 * CallGraph 的 getCallees/getCallers 返回 List<String>（symbolId），
 * 本适配器将其转换为 List<Edge>。
 */
public class CodeCallGraph implements IGraph {

    private final CallGraph callGraph;

    public CodeCallGraph(CallGraph callGraph) {
        this.callGraph = callGraph;
    }

    @Override
    public List<Edge> getOutEdges(String nodeId) {
        if (callGraph == null) {
            return Collections.emptyList();
        }
        List<String> callees = callGraph.getCallees(nodeId);
        List<Edge> edges = new ArrayList<>(callees.size());
        for (String callee : callees) {
            edges.add(new Edge(nodeId, callee, 1.0, "CALLS"));
        }
        return edges;
    }

    @Override
    public List<Edge> getInEdges(String nodeId) {
        if (callGraph == null) {
            return Collections.emptyList();
        }
        List<String> callers = callGraph.getCallers(nodeId);
        List<Edge> edges = new ArrayList<>(callers.size());
        for (String caller : callers) {
            edges.add(new Edge(caller, nodeId, 1.0, "CALLS"));
        }
        return edges;
    }
}
