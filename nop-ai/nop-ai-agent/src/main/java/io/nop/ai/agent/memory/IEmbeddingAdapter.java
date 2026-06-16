package io.nop.ai.agent.memory;

import java.util.List;

/**
 * Embedding leg of the L4-3 memory adapter triplet. Converts text (the
 * {@link AiMemoryItem#getContent()} payload) into a dense {@code double[]}
 * vector so the composite store can route {@code search} through
 * {@link IVectorAdapter} for similarity retrieval.
 *
 * <p>The agent-layer adapter contract is intentionally independent of the
 * nop-ai-core {@code IEmbeddingModel} / {@code VectorData} types — functional
 * implementations (successor: real LLM embedding API wrapper) may adapt those
 * types internally, but the agent-layer contract stays self-contained.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>{@link #isAvailable()} is a <b>capability query</b>, not a side-effecting
 *       call. It returns {@code false} when embedding is not configured (the
 *       NoOp default), {@code true} when a functional embedding backend is wired.
 *       Callers (the composite store) MUST consult {@code isAvailable()} before
 *       calling {@link #embed(String)} and fall back to keyword search when it
 *       returns {@code false} — they MUST NOT call {@code embed()} defensively
 *       and swallow the resulting exception (Minimum Rules #24, no
 *       catch-and-swallow).</li>
 *   <li>{@link #embed(String)} is only invoked when {@code isAvailable()} is
 *       {@code true}. Same text MUST produce the same vector (deterministic for
 *       a given model / mock). Empty / null text is allowed and MUST return a
 *       valid vector (a zero vector is acceptable — the vector adapter's
 *       cosine similarity naturally ranks such entries as uninformative).</li>
 *   <li>{@link #embedBatch(List)} is the vectorized form of {@code embed}; the
 *       returned list is position-aligned with the input. Functional
 *       implementations may use it to amortize a remote API round-trip.</li>
 * </ul>
 *
 * <p>The NoOp default ({@link NoOpEmbeddingAdapter}) returns
 * {@code isAvailable() == false} so the composite store transparently falls
 * back to keyword substring matching (backward compatible with the shipped
 * {@link InMemoryAiMemoryStore} search behaviour). Its {@code embed} /
 * {@code embedBatch} throw {@link UnsupportedOperationException} as a defensive
 * unreachable-path guard — they are never reached when callers honour
 * {@code isAvailable()}.
 */
public interface IEmbeddingAdapter {
    boolean isAvailable();

    double[] embed(String text);

    List<double[]> embedBatch(List<String> texts);
}
