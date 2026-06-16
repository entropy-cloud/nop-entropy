package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.List;

/**
 * Fail-fast NoOp {@link IVectorAdapter}. Every method throws
 * {@link NopAiAgentException} so a misconfigured adapter-backed store surfaces
 * the missing vector-index backend immediately (Minimum Rules #24).
 *
 * <p>There is intentionally NO graceful-degradation fallback path for the vector
 * adapter: when an integrator functionalizes {@link IEmbeddingAdapter} they
 * MUST also functionalize {@link IVectorAdapter}, otherwise the composite
 * store's vector-search path fails fast rather than silently returning empty
 * results.
 */
public class NoOpVectorAdapter implements IVectorAdapter {

    public static NoOpVectorAdapter instance() {
        return new NoOpVectorAdapter();
    }

    private static NopAiAgentException notConfigured(String op) {
        return new NopAiAgentException(
                "IVectorAdapter." + op + " is not configured: no vector-index backend is wired. "
                        + "Functionalize IVectorAdapter (e.g. InMemoryVectorAdapter for tests, "
                        + "or a real vector-store adapter successor) to enable similarity retrieval.");
    }

    @Override
    public void index(String itemKey, double[] vector) {
        throw notConfigured("index");
    }

    @Override
    public List<String> search(double[] queryVector, int topK) {
        throw notConfigured("search");
    }

    @Override
    public void remove(String itemKey) {
        throw notConfigured("remove");
    }
}
