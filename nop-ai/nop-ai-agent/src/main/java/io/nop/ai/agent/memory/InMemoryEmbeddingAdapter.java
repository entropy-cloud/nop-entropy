package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.ArrayList;
import java.util.List;

/**
 * Functional in-memory {@link IEmbeddingAdapter} reference implementation
 * (test / non-production). Produces a <b>deterministic</b> pseudo-embedding
 * derived from character bigram frequency so that lexically similar texts
 * produce high cosine similarity, WITHOUT calling any external LLM API.
 *
 * <p>Algorithm (fixed-dimension frequency histogram, L2-normalized):
 * <ol>
 *   <li>Lowercase the text and pad with a single leading / trailing space
 *       so word boundaries contribute shared bigrams (e.g. {@code " the "} →
 *       {@code " t"}, {@code "th"}, {@code "he"}, {@code "e "}).</li>
 *   <li>For each bigram, hash it to a bucket in a fixed-size vector
 *       ({@link #DIMENSION} dimensions) via {@code (bigram.hashCode() &amp;
 *       0x7fffffff) % DIMENSION} and increment that bucket's count.</li>
 *   <li>L2-normalize the histogram (divide by its Euclidean norm). Empty /
 *       null text yields the all-zeros vector (norm 0 → returned as-is;
 *       cosine similarity with anything is then ~0, correctly signalling
 *       uninformative).</li>
 * </ol>
 *
 * <p>Properties guaranteed:
 * <ul>
 *   <li>{@link #isAvailable()} returns {@code true} (functional adapter).</li>
 *   <li>Deterministic: same text → identical vector.</li>
 *   <li>Texts sharing bigrams (e.g. common words / word-boundary bigrams)
 *       produce high cosine similarity; texts with no shared bigrams produce
 *       low similarity (≈0).</li>
 *   <li>Empty / null text → valid zero vector (never throws).</li>
 * </ul>
 *
 * <p>The real embedding API integration (wrapping nop-ai-core
 * {@code IEmbeddingModel} to call a real LLM embedding endpoint) is an
 * explicit successor — this mock only validates the adapter contract and
 * the composite store's vector-search path end-to-end.
 */
public class InMemoryEmbeddingAdapter implements IEmbeddingAdapter {

    /**
     * Fixed dimension of the pseudo-embedding histogram. Large enough to keep
     * bigram hash collisions rare for typical short memory items (so unrelated
     * texts produce near-orthogonal vectors), small enough to be cheap. In the
     * range of real embedding model dimensions (768–1536).
     */
    public static final int DIMENSION = 1024;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double[] embed(String text) {
        double[] v = new double[DIMENSION];
        if (text == null || text.isEmpty()) {
            return v;
        }
        String padded = " " + text.toLowerCase() + " ";
        for (int i = 0; i + 2 <= padded.length(); i++) {
            String bigram = padded.substring(i, i + 2);
            int bucket = (bigram.hashCode() & 0x7fffffff) % DIMENSION;
            v[bucket] += 1.0;
        }
        // L2-normalize so cosine similarity == dot product of normalized vectors.
        double norm = 0.0;
        for (double c : v) {
            norm += c * c;
        }
        norm = Math.sqrt(norm);
        if (norm > 0.0) {
            for (int i = 0; i < DIMENSION; i++) {
                v[i] /= norm;
            }
        }
        return v;
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null) {
            throw new NopAiAgentException("texts must not be null");
        }
        List<double[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }
}
