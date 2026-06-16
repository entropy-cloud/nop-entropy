package io.nop.ai.agent.memory;

import java.util.List;

/**
 * Storage leg of the L4-3 memory adapter triplet
 * ({@code IStorageAdapter} / {@code IEmbeddingAdapter} / {@code IVectorAdapter}).
 *
 * <p>Each instance represents exactly one session's persisted memory (per-session
 * isolation — same convention as {@link IAiMemoryStore}: no {@code sessionId}
 * parameter on the methods). A {@code IMemoryStoreProvider} resolves a fresh
 * instance per session.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>{@link #save(AiMemoryItem)} persists a single item (insert or replace by
 *       key — upsert semantics, mirrors {@code IAiMemoryStore.add}).</li>
 *   <li>{@link #loadAll(String)} returns all items. {@code typeFilter} is
 *       optional — when non-null and non-empty, only items whose
 *       {@link AiMemoryItem#getType()} equals {@code typeFilter} are returned.
 *       {@code null} typeFilter means no type filtering.</li>
 *   <li>{@link #loadByKey(String)} returns the item for the given key, or
 *       {@code null} when the key is absent (NOT an exception — absence is a
 *       legitimate state, the caller falls back to keyword search or
 *       skip-vector-index paths).</li>
 *   <li>{@link #update(String, AiMemoryItem)} replaces the item at {@code key}
 *       (key on the stored item is normalized to the supplied {@code key}).</li>
 *   <li>{@link #remove(String)} deletes the item at {@code key}; removing a
 *       non-existent key is a no-op (idempotent).</li>
 *   <li>{@link #batchSave(List)} persists all items in one call (convenience
 *       for bulk writes; semantically equivalent to looping {@link #save}).</li>
 * </ul>
 *
 * <p>No-op / unsupported implementations MUST fail fast (throw
 * {@link io.nop.ai.agent.engine.NopAiAgentException}) rather than silently
 * returning empty/null as if the operation succeeded (Minimum Rules #24). The
 * composite adapter-backed store delegates CRUD directly to this adapter and
 * has no keyword-fallback path for storage, so a NoOp storage adapter is an
 * explicit "persistence not configured" condition that must surface.
 */
public interface IStorageAdapter {
    void save(AiMemoryItem item);

    List<AiMemoryItem> loadAll(String typeFilter);

    AiMemoryItem loadByKey(String key);

    void update(String key, AiMemoryItem item);

    void remove(String key);

    void batchSave(List<AiMemoryItem> items);
}
