package io.nop.code.graph.heuristic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestSpringEventSynthesizer {

    @Test
    void testSynthesizesEdgesFromPublisherToListener() {
        SymbolTable symbolTable = new SymbolTable();
        CallGraph callGraph = new CallGraph();

        CodeSymbol listenerMethod = createSymbol("MyListener.onEvent", CodeSymbolKind.METHOD, null);
        listenerMethod.setReturnType("UserCreatedEvent");
        Map<String, Object> extMap = new LinkedHashMap<>();
        extMap.put("annotations", List.of("EventListener"));
        listenerMethod.setExtData(JsonTool.stringify(extMap));
        symbolTable.add(listenerMethod);

        CodeSymbol publishMethod = createSymbol("MyService.publishUserCreated", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishMethod);

        CodeSymbol publishEventMethod = createSymbol("ApplicationEventPublisher.publishEvent", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishEventMethod);

        callGraph.addEdge(publishMethod.getId(), publishEventMethod.getId());

        HeuristicContext context = new HeuristicContext(symbolTable, Collections.emptyMap(), callGraph, "test-idx");
        SpringEventSynthesizer synthesizer = new SpringEventSynthesizer();
        List<CodeMethodCall> results = synthesizer.synthesize(context);

        assertFalse(results.isEmpty(), "Should synthesize at least one edge");
        CodeMethodCall edge = results.get(0);
        assertEquals(publishMethod.getId(), edge.getCallerId());
        assertEquals(listenerMethod.getId(), edge.getCalleeId());
        assertEquals(EdgeProvenance.HEURISTIC, edge.getProvenance());
        assertEquals(EdgeConfidence.INFERRED, edge.getConfidence());
        assertNotNull(edge.getMetadata());
        assertTrue(edge.getMetadata().contains("spring-event"));
    }

    @Test
    void testBroadMatchingWithPublisherAndListener() {
        SymbolTable symbolTable = new SymbolTable();
        CallGraph callGraph = new CallGraph();

        CodeSymbol listenerMethod = createSymbol("MyListener.onEvent", CodeSymbolKind.METHOD, null);
        listenerMethod.setReturnType("UserCreatedEvent");
        Map<String, Object> extMap = new LinkedHashMap<>();
        extMap.put("annotations", List.of("EventListener"));
        listenerMethod.setExtData(JsonTool.stringify(extMap));
        symbolTable.add(listenerMethod);

        CodeSymbol publishMethod = createSymbol("MyService.publishUserCreated", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishMethod);

        CodeSymbol publishEventMethod = createSymbol("ApplicationEventPublisher.publishEvent", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishEventMethod);

        callGraph.addEdge(publishMethod.getId(), publishEventMethod.getId());

        HeuristicContext context = new HeuristicContext(symbolTable, Collections.emptyMap(), callGraph, "test-idx");
        SpringEventSynthesizer synthesizer = new SpringEventSynthesizer();
        List<CodeMethodCall> results = synthesizer.synthesize(context);

        assertFalse(results.isEmpty(), "Broad matching should synthesize edge from publisher to listener");
        CodeMethodCall edge = results.get(0);
        assertEquals(publishMethod.getId(), edge.getCallerId());
        assertEquals(listenerMethod.getId(), edge.getCalleeId());
    }

    @Test
    void testNoEdgesWhenNoListeners() {
        SymbolTable symbolTable = new SymbolTable();
        CallGraph callGraph = new CallGraph();

        CodeSymbol publishMethod = createSymbol("MyService.doWork", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishMethod);

        CodeSymbol publishEventMethod = createSymbol("ApplicationEventPublisher.publishEvent", CodeSymbolKind.METHOD, null);
        symbolTable.add(publishEventMethod);

        callGraph.addEdge(publishMethod.getId(), publishEventMethod.getId());

        HeuristicContext context = new HeuristicContext(symbolTable, Collections.emptyMap(), callGraph, "test-idx");
        SpringEventSynthesizer synthesizer = new SpringEventSynthesizer();
        List<CodeMethodCall> results = synthesizer.synthesize(context);

        assertTrue(results.isEmpty());
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
