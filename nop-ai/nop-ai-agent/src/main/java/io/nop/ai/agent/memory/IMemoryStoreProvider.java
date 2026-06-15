package io.nop.ai.agent.memory;

/**
 * Resolves a per-session {@link IAiMemoryStore} instance by {@code sessionId}.
 *
 * <p>The {@link IAiMemoryStore} interface intentionally does not carry a
 * {@code sessionId} parameter — each instance represents exactly one session's
 * memory. This Provider pattern keeps the {@link IAiMemoryStore} contract
 * stable (no signature change) while enabling per-session isolation, and
 * allows future successors (L4-3 {@code IMemoryAdapter}) to swap in DB / vector
 * backends without touching the interface.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>Same {@code sessionId} → same store instance (idempotent)</li>
 *   <li>Different {@code sessionId} → different store instance (isolation)</li>
 * </ul>
 */
public interface IMemoryStoreProvider {
    IAiMemoryStore getOrCreate(String sessionId);
}
