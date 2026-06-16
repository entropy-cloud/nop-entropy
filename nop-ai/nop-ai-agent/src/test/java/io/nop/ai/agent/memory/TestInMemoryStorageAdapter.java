package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the in-memory {@link IStorageAdapter} reference impl
 * (plan 215 Phase 2). Covers the storage CRUD contract: save / load-all /
 * load-by-key / update / remove / batch-save, type filter, null-key
 * semantics, and the fail-fast invariants.
 */
public class TestInMemoryStorageAdapter {

    private AiMemoryItem item(String key, String type, String content) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    @Test
    void savePersistsAndLoadByKeyReturnsItem() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("k1", "note", "hello"));

        AiMemoryItem loaded = a.loadByKey("k1");
        assertNotNull(loaded);
        assertEquals("hello", loaded.getContent());
        assertEquals("note", loaded.getType());
    }

    @Test
    void saveStoresDefensiveCopyOfCallerItem() {
        // Mirrors InMemoryAiMemoryStore: save normalizes (defensive copy on write),
        // so mutating the caller's item AFTER save must not affect storage. (Reads
        // return the live stored reference, same as InMemoryAiMemoryStore.findByKey.)
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        AiMemoryItem src = item("k1", "note", "hello");
        a.save(src);

        src.setContent("mutated-by-caller");
        assertEquals("hello", a.loadByKey("k1").getContent(),
                "save must store a defensive copy; caller-side mutation after save must not leak in");
    }

    @Test
    void saveWithoutKeyAutoGeneratesKey() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        AiMemoryItem noKey = new AiMemoryItem();
        noKey.setContent("auto");
        a.save(noKey);

        assertEquals(1, a.size());
        // createTime auto-filled
        assertTrue(a.loadAll(null).stream().allMatch(i -> i.getCreateTime() != null));
    }

    @Test
    void loadAllWithTypeFilter() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("k1", "note", "n1"));
        a.save(item("k2", "fact", "f1"));
        a.save(item("k3", "note", "n2"));

        List<AiMemoryItem> notes = a.loadAll("note");
        assertEquals(2, notes.size());
        assertTrue(notes.stream().allMatch(i -> "note".equals(i.getType())));

        // null / empty type filter returns all
        assertEquals(3, a.loadAll(null).size());
        assertEquals(3, a.loadAll("").size());
    }

    @Test
    void loadByKeyAbsentReturnsNullNotException() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        assertNull(a.loadByKey("missing"));
        assertNull(a.loadByKey(null));
        assertNull(a.loadByKey(""));
    }

    @Test
    void updateOverwritesByKeyAndNormalizesKey() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("k1", "note", "old"));

        AiMemoryItem updated = item("ignored", "fact", "new");
        a.update("k1", updated);

        AiMemoryItem loaded = a.loadByKey("k1");
        assertNotNull(loaded);
        assertEquals("new", loaded.getContent());
        // update forces the stored key to the supplied key
        assertEquals("k1", loaded.getKey());
        assertEquals("fact", loaded.getType());
        assertEquals(1, a.size());
    }

    @Test
    void updateRejectsNullOrEmptyKey() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        assertThrows(NopAiAgentException.class, () -> a.update(null, item("k", "n", "c")));
        assertThrows(NopAiAgentException.class, () -> a.update("", item("k", "n", "c")));
        assertThrows(NopAiAgentException.class, () -> a.update("k1", null));
    }

    @Test
    void removeIsIdempotent() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("k1", "n", "c1"));
        a.save(item("k2", "n", "c2"));

        a.remove("k1");
        assertEquals(1, a.size());
        assertNull(a.loadByKey("k1"));
        // removing again or a missing key is a no-op
        a.remove("k1");
        a.remove("never-existed");
        a.remove(null);
        assertEquals(1, a.size());
    }

    @Test
    void batchSaveAddsAll() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("existing", "n", "x"));
        a.batchSave(List.of(item("b1", "n", "v1"), item("b2", "n", "v2")));

        assertEquals(3, a.size());
        assertNotNull(a.loadByKey("b1"));
        assertNotNull(a.loadByKey("b2"));

        // null batch is a no-op
        a.batchSave(null);
        assertEquals(3, a.size());
    }

    @Test
    void saveRejectsNullItem() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        assertThrows(NopAiAgentException.class, () -> a.save(null));
    }

    @Test
    void clearRemovesAll() {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        a.save(item("k1", "n", "c1"));
        a.save(item("k2", "n", "c2"));
        a.clear();
        assertEquals(0, a.size());
        assertTrue(a.loadAll(null).isEmpty());
    }

    @Test
    void loadAllSortedAscendingByCreateTime() throws Exception {
        InMemoryStorageAdapter a = new InMemoryStorageAdapter();
        AiMemoryItem first = item("k1", "n", "first");
        first.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        AiMemoryItem second = item("k2", "n", "second");
        second.setCreateTime(LocalDateTime.of(2026, 6, 2, 10, 0));
        a.save(second);
        a.save(first);

        List<AiMemoryItem> all = a.loadAll(null);
        assertEquals("first", all.get(0).getContent());
        assertEquals("second", all.get(1).getContent());
    }
}
