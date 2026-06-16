package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the in-memory {@link IEmbeddingAdapter} reference impl
 * (plan 215 Phase 2). Verifies the deterministic pseudo-embedding contract:
 * isAvailable, determinism, lexical-similarity signal, empty-content handling.
 */
public class TestInMemoryEmbeddingAdapter {

    private static double cosine(double[] a, double[] b) {
        return InMemoryVectorAdapter.cosine(a, b);
    }

    @Test
    void isAvailableReturnsTrue() {
        assertTrue(new InMemoryEmbeddingAdapter().isAvailable());
    }

    @Test
    void sameContentProducesSameVector() {
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        double[] v1 = emb.embed("the quick brown fox");
        double[] v2 = emb.embed("the quick brown fox");
        assertTrue(Arrays.equals(v1, v2), "identical text must produce identical vectors");
    }

    @Test
    void vectorHasFixedDimensionAndIsNormalized() {
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        double[] v = emb.embed("some non-empty text");
        assertEquals(InMemoryEmbeddingAdapter.DIMENSION, v.length);
        double norm = 0.0;
        for (double c : v) {
            norm += c * c;
        }
        // L2-normalized non-empty text → unit norm (within tolerance)
        assertEquals(1.0, Math.sqrt(norm), 1e-6);
    }

    @Test
    void sharedLexicalFeaturesProduceHighSimilarity() {
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        // "the cat" and "the dog" share word-boundary bigrams of "the" and "on"
        double[] a = emb.embed("the cat sat on the mat");
        double[] b = emb.embed("the dog ran on the road");
        // An unrelated string shares essentially no bigrams
        double[] unrelated = emb.embed("q9z7x2 jk1nm4 pq8rt6");

        double simShared = cosine(a, b);
        double simUnrelated = cosine(a, unrelated);

        // Primary property: shared-bigram texts rank strictly higher than unrelated.
        assertTrue(simShared > simUnrelated,
                "Texts sharing the word 'the'/'on' must be more similar than unrelated texts. "
                        + "simShared=" + simShared + " simUnrelated=" + simUnrelated);
        // Loose absolute bounds (robust to rare bigram hash collisions in 256 dims).
        assertTrue(simShared > 0.15,
                "Shared bigrams should yield clearly non-trivial similarity. simShared=" + simShared);
        assertTrue(simUnrelated < 0.15,
                "Unrelated texts should be near-orthogonal. simUnrelated=" + simUnrelated);
    }

    @Test
    void noSharedContentProducesLowSimilarity() {
        // Use diverse-character texts (not repeated chars, which concentrate
        // counts and amplify hash collisions). Two texts with no shared words
        // must produce near-orthogonal pseudo-embeddings.
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        double[] a = emb.embed("q9z7x2 jk1nm4 pq8rt6");
        double[] b = emb.embed("the quick brown fox");
        double sim = cosine(a, b);
        assertTrue(sim < 0.15, "no-shared-bigram texts should have ~0 similarity. sim=" + sim);
    }

    @Test
    void emptyContentReturnsZeroVectorNotNull() {
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        double[] empty = emb.embed("");
        double[] nil = emb.embed(null);
        assertNotNull(empty);
        assertNotNull(nil);
        assertEquals(InMemoryEmbeddingAdapter.DIMENSION, empty.length);
        assertEquals(InMemoryEmbeddingAdapter.DIMENSION, nil.length);
        for (double c : empty) {
            assertEquals(0.0, c, 0.0);
        }
    }

    @Test
    void embedBatchIsPositionAligned() {
        InMemoryEmbeddingAdapter emb = new InMemoryEmbeddingAdapter();
        List<double[]> batch = emb.embedBatch(List.of("alpha", "alpha", "beta"));
        assertEquals(3, batch.size());
        // position-aligned with input
        assertTrue(Arrays.equals(batch.get(0), batch.get(1)));
        // different text → different vector
        assertEquals(false, Arrays.equals(batch.get(0), batch.get(2)));
    }

    @Test
    void embedBatchRejectsNull() {
        assertThrows(NopAiAgentException.class, () -> new InMemoryEmbeddingAdapter().embedBatch(null));
    }
}
