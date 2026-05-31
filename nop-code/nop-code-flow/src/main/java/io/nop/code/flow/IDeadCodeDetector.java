package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
public interface IDeadCodeDetector {

    DeadCodeReport detectDeadCode(String indexId, SymbolTable symbolTable, CallGraph callGraph);
}
