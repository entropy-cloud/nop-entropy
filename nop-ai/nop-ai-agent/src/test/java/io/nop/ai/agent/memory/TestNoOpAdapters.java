package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the three NoOp default adapters (plan 215 Phase 2).
 *
 * <p>Verifies the fail-fast / capability-query contract (设计裁定 5, Minimum
 * Rules #24 — no silent no-op):
 * <ul>
 *   <li>{@link NoOpStorageAdapter} and {@link NoOpVectorAdapter} throw
 *       {@link NopAiAgentException} on every method.</li>
 *   <li>{@link NoOpEmbeddingAdapter} returns {@code isAvailable() == false}
 *       and throws {@link UnsupportedOperationException} on embed / embedBatch
 *       (the defensive unreachable path).</li>
 * </ul>
 */
public class TestNoOpAdapters {

    private static AiMemoryItem anyItem() {
        AiMemoryItem it = new AiMemoryItem();
        it.setKey("k");
        it.setContent("c");
        return it;
    }

    // ---- NoOpStorageAdapter: every method throws ----

    @Test
    void noOpStorageFailsFastOnAllMethods() {
        NoOpStorageAdapter a = NoOpStorageAdapter.instance();
        assertThrows(NopAiAgentException.class, () -> a.save(anyItem()));
        assertThrows(NopAiAgentException.class, () -> a.loadAll(null));
        assertThrows(NopAiAgentException.class, () -> a.loadAll("note"));
        assertThrows(NopAiAgentException.class, () -> a.loadByKey("k"));
        assertThrows(NopAiAgentException.class, () -> a.update("k", anyItem()));
        assertThrows(NopAiAgentException.class, () -> a.remove("k"));
        assertThrows(NopAiAgentException.class, () -> a.batchSave(java.util.List.of(anyItem())));
    }

    // ---- NoOpEmbeddingAdapter: capability query false, embed defensive throw ----

    @Test
    void noOpEmbeddingReportsUnavailable() {
        assertFalse(NoOpEmbeddingAdapter.instance().isAvailable());
    }

    @Test
    void noOpEmbeddingEmbedThrowsDefensively() {
        NoOpEmbeddingAdapter a = NoOpEmbeddingAdapter.instance();
        assertThrows(UnsupportedOperationException.class, () -> a.embed("text"));
        assertThrows(UnsupportedOperationException.class, () -> a.embedBatch(java.util.List.of("a", "b")));
    }

    // ---- NoOpVectorAdapter: every method throws ----

    @Test
    void noOpVectorFailsFastOnAllMethods() {
        NoOpVectorAdapter a = NoOpVectorAdapter.instance();
        assertThrows(NopAiAgentException.class, () -> a.index("k", new double[]{1.0}));
        assertThrows(NopAiAgentException.class, () -> a.search(new double[]{1.0}, 5));
        assertThrows(NopAiAgentException.class, () -> a.remove("k"));
    }
}
