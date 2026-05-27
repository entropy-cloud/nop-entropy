package io.nop.code.core.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSemanticModels {

    @Test
    void testEdgeConfidenceFromValue_fallbackToExtractedForUnknown() {
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(99));
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(-1));
    }

    @Test
    void testEdgeConfidenceFromValue_knownValuesMapCorrectly() {
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(10));
        assertEquals(EdgeConfidence.INFERRED, EdgeConfidence.fromValue(20));
        assertEquals(EdgeConfidence.AMBIGUOUS, EdgeConfidence.fromValue(30));
    }
}
