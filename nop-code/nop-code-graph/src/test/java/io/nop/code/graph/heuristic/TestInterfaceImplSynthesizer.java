package io.nop.code.graph.heuristic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestInterfaceImplSynthesizer {

    @Test
    void testSynthesizesEdgesForInterfaceImpl() {
        SymbolTable symbolTable = new SymbolTable();
        CallGraph callGraph = new CallGraph();

        CodeSymbol iface = createSymbol("IFoo", CodeSymbolKind.INTERFACE);
        CodeSymbol impl = createSymbol("FooImpl", CodeSymbolKind.CLASS);
        CodeSymbol ifaceMethod = createSymbol("IFoo.bar", CodeSymbolKind.METHOD, iface.getId());
        CodeSymbol implMethod = createSymbol("FooImpl.bar", CodeSymbolKind.METHOD, impl.getId());
        CodeSymbol caller = createSymbol("Caller.doWork", CodeSymbolKind.METHOD, null);

        symbolTable.add(iface);
        symbolTable.add(impl);
        symbolTable.add(ifaceMethod);
        symbolTable.add(implMethod);
        symbolTable.add(caller);

        callGraph.addEdge(caller.getId(), ifaceMethod.getId());

        Map<String, Set<String>> inheritanceIndex = new HashMap<>();
        inheritanceIndex.put("IFoo", Set.of(impl.getId()));

        HeuristicContext context = new HeuristicContext(symbolTable, inheritanceIndex, callGraph, "test-idx");
        InterfaceImplSynthesizer synthesizer = new InterfaceImplSynthesizer();
        List<CodeMethodCall> results = synthesizer.synthesize(context);

        assertEquals(1, results.size());
        CodeMethodCall edge = results.get(0);
        assertEquals(caller.getId(), edge.getCallerId());
        assertEquals(implMethod.getId(), edge.getCalleeId());
        assertEquals(EdgeProvenance.HEURISTIC, edge.getProvenance());
        assertEquals(EdgeConfidence.INFERRED, edge.getConfidence());
        assertEquals(-1, edge.getLine());
        assertEquals(0, edge.getColumn());
        assertNotNull(edge.getMetadata());
        assertTrue(edge.getMetadata().contains("interface-impl"));
        assertTrue(edge.getMetadata().contains("IFoo.bar"));
    }

    @Test
    void testNoEdgesWhenNoImplementations() {
        SymbolTable symbolTable = new SymbolTable();
        CallGraph callGraph = new CallGraph();

        CodeSymbol iface = createSymbol("IFoo", CodeSymbolKind.INTERFACE);
        CodeSymbol ifaceMethod = createSymbol("IFoo.bar", CodeSymbolKind.METHOD, iface.getId());
        CodeSymbol caller = createSymbol("Caller.doWork", CodeSymbolKind.METHOD, null);

        symbolTable.add(iface);
        symbolTable.add(ifaceMethod);
        symbolTable.add(caller);

        callGraph.addEdge(caller.getId(), ifaceMethod.getId());

        HeuristicContext context = new HeuristicContext(symbolTable, Collections.emptyMap(), callGraph, "test-idx");
        InterfaceImplSynthesizer synthesizer = new InterfaceImplSynthesizer();
        List<CodeMethodCall> results = synthesizer.synthesize(context);

        assertTrue(results.isEmpty());
    }

    private CodeSymbol createSymbol(String qualifiedName, CodeSymbolKind kind) {
        return createSymbol(qualifiedName, kind, null);
    }

    private CodeSymbol createSymbol(String qualifiedName, CodeSymbolKind kind, String declaringSymbolId) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(UUID.randomUUID().toString());
        sym.setName(qualifiedName.contains(".") ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName);
        sym.setQualifiedName(qualifiedName);
        sym.setKind(kind);
        sym.setDeclaringSymbolId(declaringSymbolId);
        return sym;
    }
}
