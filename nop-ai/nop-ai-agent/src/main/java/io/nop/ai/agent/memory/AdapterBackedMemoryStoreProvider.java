package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter-backed {@link IMemoryStoreProvider} (plan 215 / L4-3). Resolves a
 * per-session {@link AdapterBackedAiMemoryStore} composing the supplied
 * adapter triplet.
 *
 * <p>Per-session isolation: {@link ConcurrentHashMap#computeIfAbsent} creates
 * one composite store per sessionId atomically. Same sessionId → same store;
 * different sessionId → different store (each with its own adapter-backed
 * backend, mirroring {@link InMemoryMemoryStoreProvider}).
 *
 * <p>Three construction shapes are supported:
 * <ul>
 *   <li>Explicit triplet: {@code new AdapterBackedMemoryStoreProvider(storage,
 *       embedding, vector)} — the adapters are shared as <b>factories</b> in
 *       the sense that each session gets a fresh composite store bound to the
 *       SAME adapter instances (use this when the adapters themselves are
 *       per-session-aware, e.g. a DB adapter that scopes by sessionId, or for
 *       tests that share one in-memory triplet).</li>
 *   <li>Per-session factories: pass a {@link StoreFactory} so each session
 *       gets its own isolated adapter instances (the typical shape for the
 *       in-memory functional reference adapters, which hold per-session
 *       state).</li>
 * </ul>
 *
 * <p>Shipped default is NOT this provider — {@link InMemoryMemoryStoreProvider}
 * remains the engine default (设计裁定 4). This provider is opt-in via
 * {@code DefaultAgentEngine.setMemoryStoreProvider(...)}.
 */
public class AdapterBackedMemoryStoreProvider implements IMemoryStoreProvider {

    /**
     * Factory that returns a fresh adapter triplet for a session. Use when the
     * adapters hold per-session mutable state (e.g. the in-memory reference
     * adapters). For stateless / sessionId-aware adapters (e.g. a DB adapter
     * that scopes rows by sessionId) use the shared-triplet constructors
     * instead.
     */
    @FunctionalInterface
    public interface StoreFactory {
        AdapterBackedAiMemoryStore create(String sessionId);
    }

    private final ConcurrentHashMap<String, AdapterBackedAiMemoryStore> stores = new ConcurrentHashMap<>();
    private final StoreFactory factory;

    public AdapterBackedMemoryStoreProvider(IStorageAdapter storage,
                                            IEmbeddingAdapter embedding,
                                            IVectorAdapter vector) {
        if (storage == null || embedding == null || vector == null) {
            throw new NopAiAgentException("adapter triplet must not contain null");
        }
        // Shared adapter instances across sessions (stateless / sessionId-aware adapters).
        this.factory = sid -> new AdapterBackedAiMemoryStore(storage, embedding, vector);
    }

    public AdapterBackedMemoryStoreProvider(StoreFactory factory) {
        if (factory == null) {
            throw new NopAiAgentException("StoreFactory must not be null");
        }
        this.factory = factory;
    }

    @Override
    public AdapterBackedAiMemoryStore getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException("sessionId must not be null or empty");
        }
        return stores.computeIfAbsent(sessionId, factory::create);
    }

    public int sessionCount() {
        return stores.size();
    }
}
