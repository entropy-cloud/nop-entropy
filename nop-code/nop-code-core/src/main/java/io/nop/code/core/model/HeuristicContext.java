package io.nop.code.core.model;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

import java.util.Map;
import java.util.Set;

public class HeuristicContext {
    private final SymbolTable symbolTable;
    private final Map<String, Set<String>> inheritanceIndex;
    private final CallGraph callGraph;
    private final String indexId;

    public HeuristicContext(SymbolTable symbolTable,
                            Map<String, Set<String>> inheritanceIndex,
                            CallGraph callGraph,
                            String indexId) {
        this.symbolTable = symbolTable;
        this.inheritanceIndex = inheritanceIndex;
        this.callGraph = callGraph;
        this.indexId = indexId;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public Map<String, Set<String>> getInheritanceIndex() {
        return inheritanceIndex;
    }

    public CallGraph getCallGraph() {
        return callGraph;
    }

    public String getIndexId() {
        return indexId;
    }
}
