package io.nop.ai.agent.memory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for the functional {@link InMemoryAiMemoryStore}.
 *
 * <p>Covers all 8 {@link IAiMemoryStore} methods:
 * <ul>
 *     <li>{@code add} auto-fills createTime, content round-trips</li>
 *     <li>{@code getAll} supports type filter and sorts ascending by createTime</li>
 *     <li>{@code getLastN} returns the N most recent (descending)</li>
 *     <li>{@code search} matches content/type/key substring (case-insensitive)</li>
 *     <li>{@code readBudgeted} prioritizes pinned then priority desc, respects token budget</li>
 *     <li>{@code update} overwrites by key</li>
 *     <li>{@code remove} deletes by key</li>
 *     <li>{@code batchAdd} adds all</li>
 * </ul>
 *
 * <p>Concurrency: multi-threaded {@code add} preserves count and key uniqueness.
 */
public class TestInMemoryAiMemoryStore {

    private AiMemoryItem item(String key, String content) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setContent(content);
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    private AiMemoryItem item(String key, String type, String content, int priority, boolean pinned) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setPriority(priority);
        item.setPinned(pinned);
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    @Test
    void addAndGetRoundTrip() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryItem toAdd = new AiMemoryItem();
        toAdd.setKey("k1");
        toAdd.setContent("hello world");
        toAdd.setType("note");

        store.add(toAdd);

        assertEquals(1, store.size());
        AiMemoryItem stored = store.findByKey("k1");
        assertNotNull(stored);
        assertEquals("hello world", stored.getContent());
        assertEquals("note", stored.getType());
        // createTime auto-filled when null
        assertNotNull(stored.getCreateTime());
        // tokenEstimate lazily computed as chars/4 = 11/4 = 2
        assertEquals(2, stored.getTokenEstimate());
    }

    @Test
    void addWithoutKeyAutoGeneratesKey() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        AiMemoryItem toAdd = new AiMemoryItem();
        toAdd.setContent("no key");

        store.add(toAdd);

        assertEquals(1, store.size());
    }

    @Test
    void getAllWithTypeFilter() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "note", "note1", 0, false));
        store.add(item("k2", "fact", "fact1", 0, false));
        store.add(item("k3", "note", "note2", 0, false));

        Map<String, Object> filter = new HashMap<>();
        filter.put("type", "note");
        List<AiMemoryItem> notes = store.getAll(filter);
        assertEquals(2, notes.size());
        assertTrue(notes.stream().allMatch(i -> "note".equals(i.getType())));
    }

    @Test
    void getAllWithoutFilterReturnsAll() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "a"));
        store.add(item("k2", "b"));

        List<AiMemoryItem> all = store.getAll(null);
        assertEquals(2, all.size());
    }

    @Test
    void getLastNReturnsMostRecent() throws Exception {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();

        AiMemoryItem first = new AiMemoryItem();
        first.setKey("k1");
        first.setContent("first");
        first.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        store.add(first);
        Thread.sleep(5);

        AiMemoryItem second = new AiMemoryItem();
        second.setKey("k2");
        second.setContent("second");
        second.setCreateTime(LocalDateTime.of(2026, 6, 2, 10, 0));
        store.add(second);
        Thread.sleep(5);

        AiMemoryItem third = new AiMemoryItem();
        third.setKey("k3");
        third.setContent("third");
        third.setCreateTime(LocalDateTime.of(2026, 6, 3, 10, 0));
        store.add(third);

        List<AiMemoryItem> last2 = store.getLastN(2);
        assertEquals(2, last2.size());
        assertEquals("third", last2.get(0).getContent());
        assertEquals("second", last2.get(1).getContent());

        assertEquals(0, store.getLastN(0).size());
        assertEquals(3, store.getLastN(10).size());
    }

    @Test
    void searchMatchesContentCaseInsensitive() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "note", "Hello World", 0, false));
        store.add(item("k2", "note", "Goodbye world", 0, false));
        store.add(item("k3", "fact", "No match here", 0, false));

        List<AiMemoryItem> matches = store.search("world");
        assertEquals(2, matches.size());
    }

    @Test
    void searchMatchesTypeAndKey() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("foo-key", "fact", "unrelated content", 0, false));
        store.add(item("k2", "NOT-found", "no match content", 0, false));

        List<AiMemoryItem> byKey = store.search("foo");
        assertEquals(1, byKey.size());

        List<AiMemoryItem> byType = store.search("fact");
        assertEquals(1, byType.size());
    }

    @Test
    void searchEmptyQueryReturnsEmpty() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "content"));

        assertEquals(0, store.search(null).size());
        assertEquals(0, store.search("").size());
    }

    @Test
    void readBudgetedPrioritizesPinnedThenPriorityDesc() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        // Each item: 8 chars = 2 tokens (chars/4). budget = 4 tokens → fits pinned + high, excludes low.
        store.add(item("low", "note", "abcd1234", 1, false));
        store.add(item("high", "note", "abcd1234", 5, false));
        store.add(item("pinned1", "note", "abcd1234", 0, true));

        List<AiMemoryItem> budgeted = store.readBudgeted(4, new HashMap<>());
        // pinned always included first (2 tokens used), then high (2 tokens, total 4), then low skipped (would exceed)
        assertTrue(budgeted.stream().anyMatch(i -> "pinned1".equals(i.getKey())));
        assertTrue(budgeted.stream().anyMatch(i -> "high".equals(i.getKey())));
        assertFalse(budgeted.stream().anyMatch(i -> "low".equals(i.getKey())));
    }

    @Test
    void readBudgetedWithZeroBudgetReturnsEmpty() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("pinned", "note", "abcd", 0, true));

        assertEquals(0, store.readBudgeted(0, new HashMap<>()).size());
    }

    @Test
    void updateOverwritesByKey() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "old content"));

        AiMemoryItem updated = item("k1", "new content");
        updated.setType("note");
        store.update("k1", updated);

        AiMemoryItem stored = store.findByKey("k1");
        assertNotNull(stored);
        assertEquals("new content", stored.getContent());
        assertEquals("note", stored.getType());
        assertEquals(1, store.size());
    }

    @Test
    void removeDeletesByKey() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "content1"));
        store.add(item("k2", "content2"));

        store.remove("k1");
        assertEquals(1, store.size());
        assertNull(store.findByKey("k1"));
        assertNotNull(store.findByKey("k2"));

        // remove non-existent key is a no-op
        store.remove("non-existent");
        assertEquals(1, store.size());
    }

    @Test
    void batchAddAddsAll() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("existing", "x"));

        List<AiMemoryItem> batch = List.of(
                item("b1", "v1"),
                item("b2", "v2"),
                item("b3", "v3"));
        store.batchAdd(batch);

        assertEquals(4, store.size());
        assertNotNull(store.findByKey("b1"));
        assertNotNull(store.findByKey("b3"));
    }

    @Test
    void clearRemovesAll() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "c1"));
        store.add(item("k2", "c2"));

        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    void concurrentAddIsThreadSafe() throws Exception {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        int threads = 8;
        int perThread = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int base = t * 1000;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        store.add(item("key-" + (base + i), "content"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(threads * perThread, store.size());
    }

    @Test
    void noUoeOnAnyMethod() {
        InMemoryAiMemoryStore store = new InMemoryAiMemoryStore();
        store.add(item("k1", "content"));

        // None of these should throw UnsupportedOperationException (the interface defaults)
        store.readBudgeted(100, new HashMap<>());
        store.update("k1", item("k1", "updated"));
        store.batchAdd(List.of(item("k2", "v2")));
        store.remove("k1");
    }
}
