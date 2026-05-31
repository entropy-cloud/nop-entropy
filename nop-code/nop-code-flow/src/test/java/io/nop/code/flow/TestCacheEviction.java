package io.nop.code.flow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;

import static org.junit.jupiter.api.Assertions.*;

class TestCacheEviction {

    private FlowDetector createDetectorWithPopulatedCache(String indexId) {
        FlowDetector detector = new FlowDetector();
        SymbolTable symbolTable = createMinimalSymbolTable("sym-entry", "execute", "com.example.Service.execute");
        detector.detectFlows(indexId, symbolTable, new CallGraph());
        return detector;
    }

    @Test
    void testInvalidateCacheRemovesPopulatedEntry() {
        FlowDetector detector = createDetectorWithPopulatedCache("test-idx");

        List<ExecutionFlow> flows = detector.listFlows("test-idx");
        assertFalse(flows.isEmpty(), "Cache should contain flows after detectFlows()");

        detector.invalidateCache("test-idx");

        assertTrue(detector.listFlows("test-idx").isEmpty(),
                "Cache should be empty after invalidateCache()");
    }

    @Test
    void testInvalidateCacheOnNonExistentIndexDoesNotThrow() {
        FlowDetector detector = new FlowDetector();
        assertDoesNotThrow(() -> detector.invalidateCache("nonexistent_idx"));
        assertTrue(detector.listFlows("nonexistent_idx").isEmpty());
    }

    @Test
    void testInvalidateCacheDoesNotAffectOtherIndices() {
        FlowDetector detector = new FlowDetector();
        SymbolTable stA = createMinimalSymbolTable("sym-a", "execute", "com.example.A.execute");
        SymbolTable stB = createMinimalSymbolTable("sym-b", "run", "com.example.B.run");

        detector.detectFlows("idx-a", stA, new CallGraph());
        detector.detectFlows("idx-b", stB, new CallGraph());

        assertFalse(detector.listFlows("idx-a").isEmpty());
        assertFalse(detector.listFlows("idx-b").isEmpty());

        detector.invalidateCache("idx-b");

        assertFalse(detector.listFlows("idx-a").isEmpty(),
                "Invalidating idx-b should not affect idx-a");
        assertTrue(detector.listFlows("idx-b").isEmpty(),
                "idx-b should be empty after invalidation");
    }

    @Test
    void testDetectFlowsPopulatesCache() {
        FlowDetector detector = new FlowDetector();
        SymbolTable symbolTable = createMinimalSymbolTable("sym-main", "main", "com.example.Main.main");

        assertTrue(detector.listFlows("fresh-idx").isEmpty(),
                "Cache should be empty before detectFlows()");

        detector.detectFlows("fresh-idx", symbolTable, new CallGraph());

        assertFalse(detector.listFlows("fresh-idx").isEmpty(),
                "Cache should contain flows after detectFlows()");
    }

    private SymbolTable createMinimalSymbolTable(String id, String name, String qualifiedName) {
        SymbolTable st = new SymbolTable();
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setName(name);
        s.setKind(CodeSymbolKind.METHOD);
        s.setQualifiedName(qualifiedName);
        st.add(s);
        return st;
    }
}
