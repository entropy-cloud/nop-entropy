package io.nop.ai.core.api.embedding;

import org.junit.jupiter.api.Test;
import io.nop.ai.core.api.support.VectorData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA4.3-04 focused tests for the math-heavy embedding utilities
 * {@link CosineSimilarity} and {@link RelevanceScore}.
 */
public class TestCosineSimilarityAndRelevanceScore {

    private static VectorData vector(double... values) {
        VectorData data = new VectorData();
        data.setVector(values);
        return data;
    }

    @Test
    public void testIdenticalVectors() {
        double similarity = CosineSimilarity.between(vector(1, 2, 3), vector(1, 2, 3));
        assertEquals(1.0, similarity, 1e-9, "identical vectors must have cosine similarity 1");
    }

    @Test
    public void testOrthogonalVectors() {
        double similarity = CosineSimilarity.between(vector(1, 0), vector(0, 1));
        assertEquals(0.0, similarity, 1e-9, "orthogonal vectors must have cosine similarity 0");
    }

    @Test
    public void testOppositeVectors() {
        double similarity = CosineSimilarity.between(vector(1, 0), vector(-1, 0));
        assertEquals(-1.0, similarity, 1e-9, "opposite vectors must have cosine similarity -1");
    }

    @Test
    public void testZeroVectorHandled() {
        double similarity = CosineSimilarity.between(vector(0, 0), vector(1, 1));
        assertEquals(0.0, similarity, "zero vector must be treated as orthogonal (no division by zero)");
    }

    @Test
    public void testDimensionMismatchRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CosineSimilarity.between(vector(1, 2), vector(1, 2, 3)));
        assertTrue(ex.getMessage().contains("Length"), "mismatch error must explain the length requirement");
    }

    @Test
    public void testNullRejected() {
        assertThrows(Exception.class, () -> CosineSimilarity.between(null, vector(1)));
        assertThrows(Exception.class, () -> CosineSimilarity.between(vector(1), null));
    }

    @Test
    public void testMagnitudeInsensitive() {
        double similarity = CosineSimilarity.between(vector(1, 2, 3), vector(10, 20, 30));
        assertEquals(1.0, similarity, 1e-9, "scaling must not change cosine similarity");
    }

    @Test
    public void testRelevanceScoreRoundTrip() {
        assertEquals(1.0, RelevanceScore.fromCosineSimilarity(1.0), 1e-9);
        assertEquals(0.0, RelevanceScore.fromCosineSimilarity(-1.0), 1e-9);
        assertEquals(0.5, RelevanceScore.fromCosineSimilarity(0.0), 1e-9);
        assertEquals(1.0, CosineSimilarity.fromRelevanceScore(1.0), 1e-9);
        assertEquals(-1.0, CosineSimilarity.fromRelevanceScore(0.0), 1e-9);
        double roundTrip = RelevanceScore.fromCosineSimilarity(CosineSimilarity.fromRelevanceScore(0.3));
        assertEquals(0.3, roundTrip, 1e-9, "relevance score must survive the round trip");
    }
}
