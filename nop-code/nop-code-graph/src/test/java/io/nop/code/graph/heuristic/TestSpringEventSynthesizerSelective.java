package io.nop.code.graph.heuristic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestSpringEventSynthesizerSelective {

    @Test
    void testOnlyMatchesSpecificEventType() {
        SymbolTable st = new SymbolTable();
        CallGraph cg = new CallGraph();

        CodeSymbol listenerA = createSymbol("ListenerA.onUserCreated", CodeSymbolKind.METHOD);
        listenerA.setReturnType("UserCreatedEvent");
        Map<String, Object> extA = new LinkedHashMap<>();
        extA.put("annotations", List.of("EventListener"));
        listenerA.setExtData(JsonTool.stringify(extA));
        st.add(listenerA);

        CodeSymbol listenerB = createSymbol("ListenerB.onOrderPlaced", CodeSymbolKind.METHOD);
        listenerB.setReturnType("OrderPlacedEvent");
        Map<String, Object> extB = new LinkedHashMap<>();
        extB.put("annotations", List.of("EventListener"));
        listenerB.setExtData(JsonTool.stringify(extB));
        st.add(listenerB);

        CodeSymbol publisher = createSymbol("MyService.publishUserCreated", CodeSymbolKind.METHOD);
        st.add(publisher);

        CodeSymbol publishEvent = createSymbol("AppEventPublisher.publishEvent", CodeSymbolKind.METHOD);
        st.add(publishEvent);

        cg.addEdge(publisher.getId(), publishEvent.getId());

        HeuristicContext ctx = new HeuristicContext(st, Collections.emptyMap(), cg, "test-idx");
        SpringEventSynthesizer synth = new SpringEventSynthesizer();
        List<CodeMethodCall> results = synth.synthesize(ctx);

        for (CodeMethodCall edge : results) {
            assertNotEquals(listenerB.getId(), edge.getCalleeId(),
                    "Should not associate publisher with unrelated event listener");
        }
    }

    @Test
    void testNoEdgeForUnmatchedPublisher() {
        SymbolTable st = new SymbolTable();
        CallGraph cg = new CallGraph();

        CodeSymbol listener = createSymbol("Listener.onUserCreated", CodeSymbolKind.METHOD);
        listener.setReturnType("UserCreatedEvent");
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("annotations", List.of("EventListener"));
        listener.setExtData(JsonTool.stringify(ext));
        st.add(listener);

        CodeSymbol publisher = createSymbol("MyService.doSomething", CodeSymbolKind.METHOD);
        st.add(publisher);

        CodeSymbol publishEvent = createSymbol("AppEventPublisher.publishEvent", CodeSymbolKind.METHOD);
        st.add(publishEvent);

        cg.addEdge(publisher.getId(), publishEvent.getId());

        HeuristicContext ctx = new HeuristicContext(st, Collections.emptyMap(), cg, "test-idx");
        SpringEventSynthesizer synth = new SpringEventSynthesizer();
        List<CodeMethodCall> results = synth.synthesize(ctx);

        assertTrue(results.isEmpty(), "Unmatched publisher should produce no edges");
    }

    private CodeSymbol createSymbol(String qualifiedName, CodeSymbolKind kind) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(UUID.randomUUID().toString());
        sym.setName(qualifiedName.contains(".") ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName);
        sym.setQualifiedName(qualifiedName);
        sym.setKind(kind);
        return sym;
    }
}
