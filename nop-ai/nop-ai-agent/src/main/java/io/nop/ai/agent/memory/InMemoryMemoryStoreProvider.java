package io.nop.ai.agent.memory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link IMemoryStoreProvider} implementation. Maintains a
 * per-session {@link InMemoryAiMemoryStore} in a {@link ConcurrentHashMap};
 * {@link #getOrCreate(String)} is atomic via {@link ConcurrentHashMap#computeIfAbsent}.
 *
 * <p>This is the shipped default wired into {@link io.nop.ai.agent.engine.ReActAgentExecutor.Builder}
 * — working-memory tools (read-memory / write-memory / search-memory) work
 * out-of-the-box without any provider configuration. The data lives in-process
 * and is <b>not</b> persisted (deferred to L4-3 IMemoryAdapter successor).
 */
public class InMemoryMemoryStoreProvider implements IMemoryStoreProvider {

    private final ConcurrentHashMap<String, InMemoryAiMemoryStore> stores = new ConcurrentHashMap<>();

    @Override
    public IAiMemoryStore getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be null or empty");
        }
        return stores.computeIfAbsent(sessionId, k -> new InMemoryAiMemoryStore());
    }

    public int sessionCount() {
        return stores.size();
    }
}
