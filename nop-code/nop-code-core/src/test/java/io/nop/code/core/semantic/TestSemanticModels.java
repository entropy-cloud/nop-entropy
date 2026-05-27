package io.nop.code.core.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSemanticModels {

    @Test
    void testEdgeConfidenceFromValue_unknownReturnsExtracted() {
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(99));
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(-1));
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(0));
    }

    @Test
    void testEdgeConfidenceFromValue_knownValues() {
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(10));
        assertEquals(EdgeConfidence.INFERRED, EdgeConfidence.fromValue(20));
        assertEquals(EdgeConfidence.AMBIGUOUS, EdgeConfidence.fromValue(30));
    }

    @Test
    void testCodeSemanticEdge_defaultDirectedIsFalse() {
        CodeSemanticEdge edge = new CodeSemanticEdge();
        assertFalse(edge.isDirected());
        assertEquals(0.0, edge.getConfidenceScore(), 0.001);
        assertNull(edge.getId());
    }
}
