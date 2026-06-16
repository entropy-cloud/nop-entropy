package io.nop.ai.agent.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Functional in-memory {@link IVectorAdapter} reference implementation
 * (test / non-production). Maintains a per-session {@code key → vector} map
 * and answers top-k similarity searches via linear scan + cosine similarity
 * (the same formula as nop-ai-core's {@code CosineSimilarity.between}, applied
 * directly to {@code double[]} so the agent-layer contract stays independent
 * of nop-ai-core types — see 设计裁定 3).
 *
 * <p>Per-session isolation: each instance represents one session.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code index(itemKey, vector)} upserts the vector for {@code itemKey}
 *       (insert or replace — used by the composite store's {@code update}
 *       path to refresh the embedding in place).</li>
 *   <li>{@code search(queryVector, topK)} returns the keys whose indexed
 *       vectors have the highest cosine similarity to {@code queryVector},
 *       ordered by descending similarity. Empty index or {@code topK <= 0}
 *       → empty list. Vectors of differing length raise
 *       {@link IllegalArgumentException} (same guard as
 *       {@code CosineSimilarity}).</li>
 *   <li>{@code remove(itemKey)} drops the entry; missing key is a no-op
 *       (idempotent).</li>
 * </ul>
 *
 * <p>Production vector-store implementation (wrapping nop-ai-core
 * {@code IVectorStore} against FAISS / pgvector / Milvus) is an explicit
 * successor.
 */
public class InMemoryVectorAdapter implements IVectorAdapter {

    private static final double EPSILON = 1e-8;

    private final Map<String, double[]> index = new HashMap<>();

    @Override
    public synchronized void index(String itemKey, double[] vector) {
        if (itemKey == null || itemKey.isEmpty()) {
            return;
        }
        // Defensive copy so later caller-side mutation of the array does not
        // corrupt the index (matches InMemoryStorageAdapter's copy-on-write).
        index.put(itemKey, vector.clone());
    }

    @Override
    public synchronized List<String> search(double[] queryVector, int topK) {
        if (topK <= 0 || index.isEmpty() || queryVector == null) {
            return new ArrayList<>();
        }
        List<Map.Entry<String, Double>> scored = new ArrayList<>(index.size());
        for (Map.Entry<String, double[]> e : index.entrySet()) {
            scored.add(Map.entry(e.getKey(), cosine(queryVector, e.getValue())));
        }
        scored.sort(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed());
        int limit = Math.min(topK, scored.size());
        List<String> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).getKey());
        }
        return result;
    }

    @Override
    public synchronized void remove(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return;
        }
        index.remove(itemKey);
    }

    public synchronized int size() {
        return index.size();
    }

    /**
     * Cosine similarity between two {@code double[]} vectors. Same formula as
     * nop-ai-core {@code CosineSimilarity.between}: zero-norm vectors are
     * treated as orthogonal to everything (dot / max(norms, EPSILON)).
     */
    static double cosine(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "vector length mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.max(Math.sqrt(na) * Math.sqrt(nb), EPSILON);
    }
}
