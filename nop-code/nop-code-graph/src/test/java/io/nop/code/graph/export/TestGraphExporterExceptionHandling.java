package io.nop.code.graph.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphExporterExceptionHandling {

    @Test
    void testUnsupportedFormatThrowsNopException() {
        CallGraph graph = new CallGraph();
        SymbolTable table = new SymbolTable();
        GraphExporter exporter = new GraphExporter();

        NopException ex = assertThrows(NopException.class,
                () -> exporter.export(graph, table, "XML", false, null));
        assertTrue(ex.getMessage().contains("graph-export-failed"));
    }

    @Test
    void testValidFormatsDoNotThrow() {
        CallGraph graph = new CallGraph();
        SymbolTable table = new SymbolTable();
        CodeSymbol sym = new CodeSymbol();
        sym.setId("sym1");
        sym.setQualifiedName("com.example.Foo");
        sym.setName("Foo");
        sym.setKind(CodeSymbolKind.CLASS);
        table.add(sym);
        graph.addEdge("sym1", "sym2");

        GraphExporter exporter = new GraphExporter();

        String json = assertDoesNotThrow(() -> exporter.export(graph, table, "JSON", false, null));
        assertNotNull(json);
        assertTrue(json.contains("sym1"));

        String mermaid = assertDoesNotThrow(() -> exporter.export(graph, table, "MERMAID", false, null));
        assertNotNull(mermaid);
    }
}
