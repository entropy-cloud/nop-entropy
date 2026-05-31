package io.nop.code.flow;

import java.util.List;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
public interface IFlowDetector {

    List<ExecutionFlow> detectFlows(String indexId, SymbolTable symbolTable, CallGraph callGraph);

    ExecutionFlow getFlow(String indexId, String flowId);

    List<ExecutionFlow> listFlows(String indexId);

    List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths);
}
