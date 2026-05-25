package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFlowDetector {

    private FlowDetector detector;
    private CallGraph callGraph;
    private SymbolTable symbolTable;

    @BeforeEach
    void setUp() {
        detector = new FlowDetector();
        callGraph = new CallGraph();
        symbolTable = new SymbolTable();

        String[] ids = {"pkg.A", "pkg.B", "pkg.C", "pkg.D", "pkg.E"};
        String[] names = {"handleRequest", "processData", "validateInput", "saveResult", "notifyUser"};
        for (int i = 0; i < ids.length; i++) {
            CodeSymbol sym = new CodeSymbol();
            sym.setId(ids[i]);
            sym.setQualifiedName(ids[i]);
            sym.setName(names[i]);
            sym.setKind(CodeSymbolKind.METHOD);
            symbolTable.add(sym);
        }

        callGraph.addEdge("pkg.A", "pkg.B");
        callGraph.addEdge("pkg.B", "pkg.C");
        callGraph.addEdge("pkg.C", "pkg.D");
        callGraph.addEdge("pkg.D", "pkg.E");
    }

    @Test
    void testDetectFlowsReturnsNonEmptyResult() {
        List<ExecutionFlow> flows = detector.detectFlows("test-idx", symbolTable, callGraph);
        assertNotNull(flows);
        assertFalse(flows.isEmpty(), "Should detect at least one flow from entry point 'handleRequest'");
    }

    @Test
    void testEntryPointDetected() {
        List<ExecutionFlow> flows = detector.detectFlows("test-idx", symbolTable, callGraph);
        boolean hasHandleRequestFlow = flows.stream()
                .anyMatch(f -> f.getEntryPointQualifiedName() != null
                        && f.getEntryPointQualifiedName().equals("pkg.A"));
        assertTrue(hasHandleRequestFlow,
                "Should detect handleRequest as entry point");
    }

    @Test
    void testFlowPathContainsExpectedSymbols() {
        List<ExecutionFlow> flows = detector.detectFlows("test-idx", symbolTable, callGraph);
        ExecutionFlow mainFlow = flows.stream()
                .filter(f -> "pkg.A".equals(f.getEntryPointSymbolId()))
                .findFirst()
                .orElse(null);

        assertNotNull(mainFlow);
        List<String> path = mainFlow.getPathNodeIds();
        assertNotNull(path);
        assertTrue(path.contains("pkg.A"), "Path should contain pkg.A");
        assertTrue(path.contains("pkg.B"), "Path should contain pkg.B");
        assertTrue(path.contains("pkg.C"), "Path should contain pkg.C");
        assertTrue(path.contains("pkg.D"), "Path should contain pkg.D");
        assertTrue(path.contains("pkg.E"), "Path should contain pkg.E");
    }

    @Test
    void testCriticalityScoreComputed() {
        List<ExecutionFlow> flows = detector.detectFlows("test-idx", symbolTable, callGraph);
        for (ExecutionFlow flow : flows) {
            assertTrue(flow.getCriticality() >= 0.0,
                    "Criticality should be >= 0, got " + flow.getCriticality());
            assertTrue(flow.getCriticality() <= 1.0,
                    "Criticality should be <= 1, got " + flow.getCriticality());
        }
    }

    @Test
    void testListFlowsReturnsCachedResult() {
        detector.detectFlows("test-idx", symbolTable, callGraph);
        List<ExecutionFlow> cached = detector.listFlows("test-idx");
        assertNotNull(cached);
        assertFalse(cached.isEmpty());
    }

    @Test
    void testListFlowsReturnsEmptyForUnknownIndex() {
        List<ExecutionFlow> flows = detector.listFlows("nonexistent");
        assertNotNull(flows);
        assertTrue(flows.isEmpty());
    }

    @Test
    void testEmptyGraphReturnsEmptyFlows() {
        CallGraph empty = new CallGraph();
        SymbolTable emptySt = new SymbolTable();
        List<ExecutionFlow> flows = detector.detectFlows("empty-idx", emptySt, empty);
        assertNotNull(flows);
        assertTrue(flows.isEmpty());
    }
}
