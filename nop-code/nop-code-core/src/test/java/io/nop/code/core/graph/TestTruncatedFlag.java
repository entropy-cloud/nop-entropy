package io.nop.code.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTruncatedFlag {

    @Test
    void testSymbolTableTruncatedFlag() {
        SymbolTable table = new SymbolTable();
        assertFalse(table.isTruncated());
        table.setTruncated(true);
        assertTrue(table.isTruncated());
        table.setTruncated(false);
        assertFalse(table.isTruncated());
    }

    @Test
    void testCallGraphTruncatedFlag() {
        CallGraph graph = new CallGraph();
        assertFalse(graph.isTruncated());
        graph.setTruncated(true);
        assertTrue(graph.isTruncated());
        graph.setTruncated(false);
        assertFalse(graph.isTruncated());
    }
}
