package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the adapter-backed {@link IMemoryStoreProvider} (plan 215
 * Phase 2). Verifies per-session isolation (same sessionId → same store;
 * different sessionId → different store with isolated adapter backends) and
 * the null/empty sessionId fail-fast invariant.
 */
public class TestAdapterBackedMemoryStoreProvider {

    /** Per-session factory: each session gets fresh in-memory adapter instances. */
    private AdapterBackedMemoryStoreProvider perSessionFactory() {
        return new AdapterBackedMemoryStoreProvider(
                sid -> new AdapterBackedAiMemoryStore(
                        new InMemoryStorageAdapter(),
                        new InMemoryEmbeddingAdapter(),
                        new InMemoryVectorAdapter()));
    }

    @Test
    void sameSessionIdReturnsSameInstance() {
        AdapterBackedMemoryStoreProvider provider = perSessionFactory();
        IAiMemoryStore a = provider.getOrCreate("s1");
        IAiMemoryStore b = provider.getOrCreate("s1");
        assertSame(a, b);
        assertEquals(1, provider.sessionCount());
        assertTrue(a instanceof AdapterBackedAiMemoryStore);
    }

    @Test
    void differentSessionIdsReturnDifferentInstances() {
        AdapterBackedMemoryStoreProvider provider = perSessionFactory();
        IAiMemoryStore a = provider.getOrCreate("sA");
        IAiMemoryStore b = provider.getOrCreate("sB");
        assertNotSame(a, b);
        assertEquals(2, provider.sessionCount());
    }

    @Test
    void perSessionIsolationDataDoesNotLeak() {
        AdapterBackedMemoryStoreProvider provider = perSessionFactory();
        IAiMemoryStore storeA = provider.getOrCreate("sA");
        AiMemoryItem item = new AiMemoryItem();
        item.setKey("k1");
        item.setContent("private-to-A");
        storeA.add(item);

        IAiMemoryStore storeB = provider.getOrCreate("sB");
        assertEquals(1, storeA.getAll(null).size());
        assertTrue(storeB.getAll(null).isEmpty(),
                "Session B must not see items written by Session A (per-session adapter isolation)");
    }

    @Test
    void sharedTripletConstructorSharesAdapterInstancesAcrossSessions() {
        // The shared-triplet constructor shares adapter instances — used for
        // stateless / sessionId-aware adapters (e.g. a future DB adapter). For
        // per-session stateful adapters the StoreFactory form is required.
        InMemoryStorageAdapter sharedStorage = new InMemoryStorageAdapter();
        AdapterBackedMemoryStoreProvider provider = new AdapterBackedMemoryStoreProvider(
                sharedStorage, new InMemoryEmbeddingAdapter(), new InMemoryVectorAdapter());

        AdapterBackedAiMemoryStore a = provider.getOrCreate("s1");
        AdapterBackedAiMemoryStore b = provider.getOrCreate("s2");
        assertSame(a.getStorage(), b.getStorage(),
                "shared-triplet constructor wires the SAME adapter instances into every session");
    }

    @Test
    void nullAndEmptySessionIdFailFast() {
        AdapterBackedMemoryStoreProvider provider = perSessionFactory();
        assertThrows(NopAiAgentException.class, () -> provider.getOrCreate(null));
        assertThrows(NopAiAgentException.class, () -> provider.getOrCreate(""));
    }

    @Test
    void constructorsRejectNullInputs() {
        assertThrows(NopAiAgentException.class,
                () -> new AdapterBackedMemoryStoreProvider(null, NoOpEmbeddingAdapter.instance(), NoOpVectorAdapter.instance()));
        assertThrows(NopAiAgentException.class,
                () -> new AdapterBackedMemoryStoreProvider((AdapterBackedMemoryStoreProvider.StoreFactory) null));
    }
}
