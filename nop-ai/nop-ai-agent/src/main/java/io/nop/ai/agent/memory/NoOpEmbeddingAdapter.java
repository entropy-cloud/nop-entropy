package io.nop.ai.agent.memory;

import java.util.List;

/**
 * Capability-query NoOp {@link IEmbeddingAdapter}. Reports
 * {@link #isAvailable()} == {@code false} so the composite store transparently
 * falls back to keyword substring matching (backward compatible with the
 * shipped {@link InMemoryAiMemoryStore} search behaviour).
 *
 * <p>{@link #embed(String)} / {@link #embedBatch(List)} throw
 * {@link UnsupportedOperationException} as a defensive unreachable-path guard.
 * They are NEVER reached when callers honour {@link #isAvailable()} (the
 * composite store consults it before embedding). Throwing here — instead of
 * returning a zero/null vector — ensures a caller that bypasses the
 * capability query fails loudly rather than silently producing uninformative
 * vectors (Minimum Rules #24).
 */
public class NoOpEmbeddingAdapter implements IEmbeddingAdapter {

    public static NoOpEmbeddingAdapter instance() {
        return new NoOpEmbeddingAdapter();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public double[] embed(String text) {
        throw new UnsupportedOperationException(
                "IEmbeddingAdapter.embed is unreachable when isAvailable() == false; "
                        + "functionalize IEmbeddingAdapter (e.g. InMemoryEmbeddingAdapter for tests, "
                        + "or a real LLM embedding API adapter successor) to enable embedding.");
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        throw new UnsupportedOperationException(
                "IEmbeddingAdapter.embedBatch is unreachable when isAvailable() == false; "
                        + "functionalize IEmbeddingAdapter to enable embedding.");
    }
}
