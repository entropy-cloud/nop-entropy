package io.nop.ai.agent.memory;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.List;
import java.util.Map;

public interface IAiMemoryStore {
    List<AiMemoryItem> getAll(Map<String, Object> filters);

    List<AiMemoryItem> getLastN(int n);

    List<AiMemoryItem> search(String query);

    void add(AiMemoryItem item);

    /**
     * Token-budgeted read (Phase 2 capability). The default fails fast with
     * {@link NopAiAgentException} carrying
     * {@code NopAiAgentErrors.ERR_AI_MEMORY_READ_BUDGETED_NOT_SUPPORTED} so a
     * store that silently ignores the budget is detected at runtime (Minimum
     * Rules #24). In-tree {@link InMemoryAiMemoryStore} overrides this with a
     * real implementation; the default is reachable for stores that do not
     * override it.
     */
    default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_MEMORY_READ_BUDGETED_NOT_SUPPORTED);
    }

    /**
     * Keyed update (Phase 2 capability). Fail-fast default — see
     * {@link #readBudgeted(int, Map)}. In-tree {@link InMemoryAiMemoryStore}
     * overrides this; the default is reachable for stores that do not.
     */
    default void update(String key, AiMemoryItem item) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_MEMORY_UPDATE_NOT_SUPPORTED);
    }

    /**
     * Keyed remove (Phase 2 capability). Fail-fast default — see
     * {@link #readBudgeted(int, Map)}. In-tree {@link InMemoryAiMemoryStore}
     * overrides this; the default is reachable for stores that do not.
     */
    default void remove(String key) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_MEMORY_REMOVE_NOT_SUPPORTED);
    }

    /**
     * Batch add (Phase 2 capability). Fail-fast default — see
     * {@link #readBudgeted(int, Map)}. In-tree {@link InMemoryAiMemoryStore}
     * overrides this; the default is reachable for stores that do not.
     */
    default void batchAdd(List<AiMemoryItem> items) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_MEMORY_BATCH_ADD_NOT_SUPPORTED);
    }
}
