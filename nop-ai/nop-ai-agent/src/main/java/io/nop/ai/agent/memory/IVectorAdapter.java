package io.nop.ai.agent.memory;

import java.util.List;

/**
 * Vector-retrieval leg of the L4-3 memory adapter triplet. Maintains a
 * per-session vector index keyed by memory-item key and answers top-k
 * similarity searches used by the composite store's {@code search} path.
 *
 * <p>Like the other two adapters, each instance represents exactly one session
 * (per-session isolation; no {@code sessionId} parameter).
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>{@link #index(String, double[])} inserts or replaces (upsert) the
 *       vector associated with {@code itemKey}. Indexing the same key twice
 *       replaces the prior vector — used by the composite store's
 *       {@code update} path to refresh the embedding in place.</li>
 *   <li>{@link #search(double[], int)} takes a query vector and a {@code topK}
 *       and returns the item keys whose indexed vectors are most similar to
 *       the query, ordered by descending similarity. Returns an empty list
 *       when the index is empty or {@code topK <= 0}. Similarity metric is
 *       cosine similarity (the in-memory reference implementation reuses the
 *       same formula as nop-ai-core's {@code CosineSimilarity.between}).</li>
 *   <li>{@link #remove(String)} drops the entry at {@code itemKey}; removing a
 *       non-existent key is a no-op (idempotent), mirroring
 *       {@link IStorageAdapter#remove}.</li>
 * </ul>
 *
 * <p>The NoOp default ({@link NoOpVectorAdapter}) fails fast on every method
 * (throws {@link io.nop.ai.agent.engine.NopAiAgentException}). There is no
 * keyword-style graceful-degradation fallback for the vector adapter: when an
 * integrator functionalizes the embedding adapter they MUST also functionalize
 * the vector adapter, otherwise the composite store's vector-search path
 * surfaces the misconfiguration as an explicit failure rather than silently
 * producing empty results (Minimum Rules #24).
 */
public interface IVectorAdapter {
    void index(String itemKey, double[] vector);

    List<String> search(double[] queryVector, int topK);

    void remove(String itemKey);
}
