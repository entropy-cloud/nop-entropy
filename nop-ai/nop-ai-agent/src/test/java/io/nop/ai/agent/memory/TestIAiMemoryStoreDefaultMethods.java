package io.nop.ai.agent.memory;

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
    void readBudgetedThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.readBudgeted(1000, Collections.emptyMap()));
        assertEquals("readBudgeted requires Phase 2", ex.getMessage());
    }

    @Test
    void updateThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.update("k1", new AiMemoryItem()));
        assertEquals("update requires Phase 2", ex.getMessage());
    }

    @Test
    void removeThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.remove("k1"));
        assertEquals("remove requires Phase 2", ex.getMessage());
    }

    @Test
    void batchAddThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.batchAdd(Collections.emptyList()));
        assertEquals("batchAdd requires Phase 2", ex.getMessage());
    }
}
