package io.nop.ai.agent.memory;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestIAiMemoryStoreDefaultMethods {

    private final IAiMemoryStore store = new IAiMemoryStore() {
        @Override
        public List<AiMemoryItem> getAll(Map<String, Object> filters) {
            return Collections.emptyList();
        }

        @Override
        public List<AiMemoryItem> getLastN(int n) {
            return Collections.emptyList();
        }

        @Override
        public List<AiMemoryItem> search(String query) {
            return Collections.emptyList();
        }

        @Override
        public void add(AiMemoryItem item) {
        }
    };

    @Test
    void readBudgetedThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.readBudgeted(1000, Collections.emptyMap()));
        assertEquals(NopAiAgentErrors.ERR_AI_MEMORY_READ_BUDGETED_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("readBudgeted requires Phase 2", ex.getDescription());
    }

    @Test
    void updateThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.update("k1", new AiMemoryItem()));
        assertEquals(NopAiAgentErrors.ERR_AI_MEMORY_UPDATE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("update requires Phase 2", ex.getDescription());
    }

    @Test
    void removeThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.remove("k1"));
        assertEquals(NopAiAgentErrors.ERR_AI_MEMORY_REMOVE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("remove requires Phase 2", ex.getDescription());
    }

    @Test
    void batchAddThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.batchAdd(Collections.emptyList()));
        assertEquals(NopAiAgentErrors.ERR_AI_MEMORY_BATCH_ADD_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("batchAdd requires Phase 2", ex.getDescription());
    }
}
