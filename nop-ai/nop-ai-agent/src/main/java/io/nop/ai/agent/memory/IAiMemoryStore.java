package io.nop.ai.agent.memory;

import java.util.List;
import java.util.Map;

public interface IAiMemoryStore {
    List<AiMemoryItem> getAll(Map<String, Object> filters);

    List<AiMemoryItem> getLastN(int n);

    List<AiMemoryItem> search(String query);

    void add(AiMemoryItem item);

    default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
        throw new UnsupportedOperationException("readBudgeted requires Phase 2");
    }

    default void update(String key, AiMemoryItem item) {
        throw new UnsupportedOperationException("update requires Phase 2");
    }

    default void remove(String key) {
        throw new UnsupportedOperationException("remove requires Phase 2");
    }

    default void batchAdd(List<AiMemoryItem> items) {
        throw new UnsupportedOperationException("batchAdd requires Phase 2");
    }
}
