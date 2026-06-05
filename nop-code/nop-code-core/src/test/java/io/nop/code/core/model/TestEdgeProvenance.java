package io.nop.code.core.model;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestEdgeProvenance {

    @Test
    void testAllValues() {
        assertEquals(5, EdgeProvenance.values().length);
        assertNotNull(EdgeProvenance.AST_EXTRACTION);
        assertNotNull(EdgeProvenance.SYMBOL_SOLVER);
        assertNotNull(EdgeProvenance.HEURISTIC);
        assertNotNull(EdgeProvenance.FRAMEWORK_INFERENCE);
        assertNotNull(EdgeProvenance.MANUAL);
    }

    @Test
    void testExportedModifier() {
        CodeSymbol sym = new CodeSymbol();
        assertFalse(sym.isExportedFlag());
        sym.setExportedFlag(true);
        assertTrue(sym.isExportedFlag());
        assertTrue(sym.hasModifier(CodeSymbol.MODIFIER_EXPORTED));
        assertEquals(1 << 9, CodeSymbol.MODIFIER_EXPORTED);
    }

    @Test
    void testCodeSymbolFilePathAndLanguage() {
        CodeSymbol sym = new CodeSymbol();
        assertNull(sym.getFilePath());
        assertNull(sym.getLanguage());
        sym.setFilePath("src/main/java/Foo.java");
        sym.setLanguage("JAVA");
        assertEquals("src/main/java/Foo.java", sym.getFilePath());
        assertEquals("JAVA", sym.getLanguage());
    }

    @Test
    void testCodeMethodCallProvenanceAndMetadata() {
        CodeMethodCall call = new CodeMethodCall();
        assertNull(call.getProvenance());
        assertNull(call.getMetadata());
        call.setProvenance(EdgeProvenance.HEURISTIC);
        call.setMetadata("{\"synthesizedBy\":\"test\"}");
        assertEquals(EdgeProvenance.HEURISTIC, call.getProvenance());
        assertEquals("{\"synthesizedBy\":\"test\"}", call.getMetadata());
    }

    @Test
    void testCodeInheritanceProvenance() {
        CodeInheritance inh = new CodeInheritance();
        assertNull(inh.getProvenance());
        inh.setProvenance(EdgeProvenance.AST_EXTRACTION);
        assertEquals(EdgeProvenance.AST_EXTRACTION, inh.getProvenance());
    }

    @Test
    void testCodeAnnotationUsageProvenance() {
        CodeAnnotationUsage usage = new CodeAnnotationUsage();
        assertNull(usage.getProvenance());
        usage.setProvenance(EdgeProvenance.AST_EXTRACTION);
        assertEquals(EdgeProvenance.AST_EXTRACTION, usage.getProvenance());
    }

    @Test
    void testCodeSymbolKindRoute() {
        assertNotNull(CodeSymbolKind.ROUTE);
        assertEquals(100, CodeSymbolKind.ROUTE.getValue());
    }

    @Test
    void testCodeUsageKindNewValues() {
        assertNotNull(CodeUsageKind.TYPE_OF);
        assertNotNull(CodeUsageKind.INSTANTIATES);
    }

    @Test
    void testCodeRouteInfo() {
        CodeRouteInfo route = new CodeRouteInfo();
        route.setHttpMethod("GET");
        route.setRoutePath("/api/users/{id}");
        route.setHandlerSymbolId("sym-123");
        route.setHandlerQualifiedName("com.example.UserController.getUser");
        assertEquals("GET", route.getHttpMethod());
        assertEquals("/api/users/{id}", route.getRoutePath());
        assertEquals("sym-123", route.getHandlerSymbolId());
        assertEquals("com.example.UserController.getUser", route.getHandlerQualifiedName());
    }

    @Test
    void testHeuristicContext() {
        SymbolTable st = new SymbolTable();
        CallGraph cg = new CallGraph();
        Map<String, Set<String>> idx = new HashMap<>();
        HeuristicContext ctx = new HeuristicContext(st, idx, cg, "idx-1");
        assertSame(st, ctx.getSymbolTable());
        assertSame(cg, ctx.getCallGraph());
        assertSame(idx, ctx.getInheritanceIndex());
        assertEquals("idx-1", ctx.getIndexId());
    }
}
