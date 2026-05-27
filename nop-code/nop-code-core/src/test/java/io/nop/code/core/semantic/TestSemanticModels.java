package io.nop.code.core.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSemanticModels {

    @Test
    void testEdgeConfidenceValues() {
        assertEquals(10, EdgeConfidence.EXTRACTED.getValue());
        assertEquals(20, EdgeConfidence.INFERRED.getValue());
        assertEquals(30, EdgeConfidence.AMBIGUOUS.getValue());
    }

    @Test
    void testEdgeConfidenceFromValueUnknown() {
        assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(99));
    }

    @Test
    void testSemanticRelationTypeCount() {
        assertEquals(8, SemanticRelationType.values().length);
    }

    @Test
    void testSemanticRelationTypes() {
        assertNotNull(SemanticRelationType.SEMANTICALLY_SIMILAR_TO);
        assertNotNull(SemanticRelationType.CONCEPTUALLY_RELATED_TO);
        assertNotNull(SemanticRelationType.SOLVES_SAME_PROBLEM);
        assertNotNull(SemanticRelationType.IMPLEMENTS_PATTERN);
        assertNotNull(SemanticRelationType.ALTERNATIVE_OF);
        assertNotNull(SemanticRelationType.DOCUMENTED_BY);
        assertNotNull(SemanticRelationType.RATIONALE_FOR);
        assertNotNull(SemanticRelationType.CROSS_LANGUAGE_PEER);
    }

    @Test
    void testCodeSemanticEdgeGettersSetters() {
        CodeSemanticEdge edge = new CodeSemanticEdge();
        edge.setId("edge-1");
        edge.setIndexId("idx-1");
        edge.setSourceSymbolId("sym-A");
        edge.setTargetSymbolId("sym-B");
        edge.setDirected(true);
        edge.setRelationType(SemanticRelationType.SEMANTICALLY_SIMILAR_TO);
        edge.setConfidence(EdgeConfidence.EXTRACTED);
        edge.setConfidenceScore(0.85);
        edge.setRationale("similar names");
        edge.setExtractorId("name-sim");
        edge.setExtData("{\"key\":\"value\"}");

        assertEquals("edge-1", edge.getId());
        assertEquals("idx-1", edge.getIndexId());
        assertEquals("sym-A", edge.getSourceSymbolId());
        assertEquals("sym-B", edge.getTargetSymbolId());
        assertTrue(edge.isDirected());
        assertEquals(SemanticRelationType.SEMANTICALLY_SIMILAR_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals(0.85, edge.getConfidenceScore(), 0.001);
        assertEquals("similar names", edge.getRationale());
        assertEquals("name-sim", edge.getExtractorId());
        assertEquals("{\"key\":\"value\"}", edge.getExtData());
    }

    @Test
    void testCodeSemanticEdgeDefaults() {
        CodeSemanticEdge edge = new CodeSemanticEdge();
        assertNull(edge.getId());
        assertFalse(edge.isDirected());
        assertEquals(0.0, edge.getConfidenceScore(), 0.001);
    }
}
