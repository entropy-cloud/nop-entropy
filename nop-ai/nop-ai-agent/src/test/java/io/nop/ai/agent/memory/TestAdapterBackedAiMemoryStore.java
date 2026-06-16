package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the composite {@link AdapterBackedAiMemoryStore} (plan 215
 * Phase 2). Covers all 8 {@link IAiMemoryStore} methods, the keyword-fallback
 * path (embedding NoOp), the semantic-search path (embedding functional), and
 * readBudgeted behaviour.
 */
public class TestAdapterBackedAiMemoryStore {

    private AiMemoryItem item(String key, String type, String content) {
        AiMemoryItem it = new AiMemoryItem();
        it.setKey(key);
        it.setType(type);
        it.setContent(content);
        it.setCreateTime(LocalDateTime.now());
        return it;
    }

    private AiMemoryItem item(String key, String type, String content, int priority, boolean pinned) {
        AiMemoryItem it = item(key, type, content);
        it.setPriority(priority);
        it.setPinned(pinned);
        return it;
    }

    /** Functional in-memory triplet (per-session isolated). */
    private AdapterBackedAiMemoryStore functionalStore() {
        return new AdapterBackedAiMemoryStore(
                new InMemoryStorageAdapter(),
                new InMemoryEmbeddingAdapter(),
                new InMemoryVectorAdapter());
    }

    /** Triplet where embedding is NoOp (keyword fallback path). */
    private AdapterBackedAiMemoryStore keywordFallbackStore() {
        return new AdapterBackedAiMemoryStore(
                new InMemoryStorageAdapter(),
                NoOpEmbeddingAdapter.instance(),
                NoOpVectorAdapter.instance());
    }

    // ---- CRUD delegation to storage (all 8 methods exercised) ----

    @Test
    void addPersistsAndIsVisibleViaGetAll() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("k1", "note", "hello world"));

        Map<String, Object> filter = new HashMap<>();
        filter.put("type", "note");
        List<AiMemoryItem> notes = store.getAll(filter);
        assertEquals(1, notes.size());
        assertEquals("hello world", notes.get(0).getContent());
    }

    @Test
    void addWithoutKeyAutoGeneratesAndStillIndexes() {
        AdapterBackedAiMemoryStore store = functionalStore();
        AiMemoryItem noKey = new AiMemoryItem();
        noKey.setContent("auto generated");
        store.add(noKey);

        assertEquals(1, store.size());
        // semantic search must still find it (composite store is key authority)
        assertFalse(store.search("auto").isEmpty());
    }

    @Test
    void getLastNReturnsMostRecent() {
        AdapterBackedAiMemoryStore store = functionalStore();
        AiMemoryItem a = item("k1", "n", "first");
        a.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        AiMemoryItem b = item("k2", "n", "second");
        b.setCreateTime(LocalDateTime.of(2026, 6, 2, 10, 0));
        AiMemoryItem c = item("k3", "n", "third");
        c.setCreateTime(LocalDateTime.of(2026, 6, 3, 10, 0));
        store.add(a);
        store.add(b);
        store.add(c);

        List<AiMemoryItem> last2 = store.getLastN(2);
        assertEquals(2, last2.size());
        assertEquals("third", last2.get(0).getContent());
        assertEquals("second", last2.get(1).getContent());
        assertEquals(0, store.getLastN(0).size());
    }

    @Test
    void updateOverwritesAndRefreshesVectorIndex() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("k1", "note", "cat dog"));

        AiMemoryItem updated = item("ignored", "fact", "fish bird");
        store.update("k1", updated);

        // storage updated
        Map<String, Object> f = new HashMap<>();
        f.put("type", "fact");
        assertEquals(1, store.getAll(f).size());
        // vector index refreshed: search for the new content outranks the old
        List<AiMemoryItem> hits = store.search("fish bird");
        assertFalse(hits.isEmpty());
        assertEquals("k1", hits.get(0).getKey());
    }

    @Test
    void removeDeletesFromStorageAndIndex() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("k1", "n", "alpha beta"));
        store.add(item("k2", "n", "gamma delta"));
        assertEquals(2, store.size());

        store.remove("k1");
        assertEquals(1, store.size());
        // the removed item is no longer in search results
        assertTrue(store.search("alpha").stream().noneMatch(i -> "k1".equals(i.getKey())));
        // idempotent
        store.remove("k1");
        store.remove(null);
        assertEquals(1, store.size());
    }

    @Test
    void batchAddIndexesAll() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.batchAdd(List.of(
                item("b1", "n", "one two"),
                item("b2", "n", "three four")));
        assertEquals(2, store.size());
        assertFalse(store.search("one").isEmpty());
        assertFalse(store.search("three").isEmpty());
        // null batch is a no-op
        store.batchAdd(null);
        assertEquals(2, store.size());
    }

    // ---- search: keyword fallback path (embedding NoOp) ----

    @Test
    void searchKeywordFallbackWhenEmbeddingUnavailable() {
        AdapterBackedAiMemoryStore store = keywordFallbackStore();
        store.add(item("k1", "note", "Hello World"));
        store.add(item("k2", "note", "Goodbye world"));
        store.add(item("k3", "fact", "No match here"));

        assertFalse(store.isSemanticSearchEnabled(),
                "NoOp embedding must report semantic search disabled");
        List<AiMemoryItem> matches = store.search("world");
        // substring match, case-insensitive (same as InMemoryAiMemoryStore)
        assertEquals(2, matches.size());
        assertTrue(matches.stream().allMatch(i -> i.getContent().toLowerCase().contains("world")));
    }

    @Test
    void searchKeywordFallbackDoesNotCallEmbed() {
        // A counting embedding adapter that reports unavailable; if the composite
        // store wrongly calls embed() on the fallback path, the counter detects it.
        AtomicInteger embedCalls = new AtomicInteger();
        IEmbeddingAdapter unavailableSpy = new IEmbeddingAdapter() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public double[] embed(String text) {
                embedCalls.incrementAndGet();
                return new double[InMemoryEmbeddingAdapter.DIMENSION];
            }

            @Override
            public List<double[]> embedBatch(List<String> texts) {
                return List.of();
            }
        };
        AdapterBackedAiMemoryStore store = new AdapterBackedAiMemoryStore(
                new InMemoryStorageAdapter(), unavailableSpy, NoOpVectorAdapter.instance());

        store.add(item("k1", "note", "findable keyword content"));
        // search with NoOp embedding must go keyword path and NOT call embed()
        assertDoesNotThrow(() -> store.search("keyword"));
        assertEquals(0, embedCalls.get(),
                "keyword fallback path must not call IEmbeddingAdapter.embed (capability-query honored)");
    }

    @Test
    void searchEmptyQueryReturnsEmptyOnBothPaths() {
        AdapterBackedAiMemoryStore functional = functionalStore();
        functional.add(item("k1", "n", "x"));
        assertEquals(0, functional.search(null).size());
        assertEquals(0, functional.search("").size());

        AdapterBackedAiMemoryStore keyword = keywordFallbackStore();
        keyword.add(item("k1", "n", "x"));
        assertEquals(0, keyword.search(null).size());
    }

    // ---- search: semantic path (embedding functional) ----

    @Test
    void semanticSearchRanksSharedLexicalFeaturesHigher() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("shared", "note", "the quick brown fox jumps"));
        store.add(item("unrelated", "note", "zzxxx ccc qqq mmm vvv"));

        // query that shares the word "the"/"fox" with "shared", nothing with "unrelated"
        List<AiMemoryItem> ranked = store.search("the lazy fox runs");
        assertTrue(store.isSemanticSearchEnabled());
        assertFalse(ranked.isEmpty());
        // The lexically-shared item must rank first (exit criterion)
        assertEquals("shared", ranked.get(0).getKey(),
                "Semantic search must rank lexically-similar items above unrelated items. Ranking: "
                        + ranked);
        assertTrue(ranked.stream().anyMatch(i -> "shared".equals(i.getKey())));
    }

    @Test
    void semanticSearchResolvesKeysToItemsInSimilarityOrder() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("k1", "n", "alpha alpha alpha"));
        store.add(item("k2", "n", "beta beta beta"));
        store.add(item("k3", "n", "alpha alpha gamma"));

        List<AiMemoryItem> hits = store.search("alpha alpha gamma");
        // k3 (near-identical) and k1 (shares alpha) must both rank above k2 (no overlap)
        assertEquals("k3", hits.get(0).getKey());
        // every returned entry is a real storage item (no orphan keys leak through)
        for (AiMemoryItem it : hits) {
            assertNotNull(it.getContent());
        }
    }

    // ---- readBudgeted ----

    @Test
    void readBudgetedPrioritizesPinnedThenPriorityDesc() {
        AdapterBackedAiMemoryStore store = functionalStore();
        // 8 chars = 2 tokens each; budget 4 tokens → pinned + high fit, low excluded
        store.add(item("low", "note", "abcd1234", 1, false));
        store.add(item("high", "note", "abcd1234", 5, false));
        store.add(item("pinned1", "note", "abcd1234", 0, true));

        List<AiMemoryItem> budgeted = store.readBudgeted(4, new HashMap<>());
        assertTrue(budgeted.stream().anyMatch(i -> "pinned1".equals(i.getKey())));
        assertTrue(budgeted.stream().anyMatch(i -> "high".equals(i.getKey())));
        assertFalse(budgeted.stream().anyMatch(i -> "low".equals(i.getKey())));
    }

    @Test
    void readBudgetedZeroBudgetReturnsEmpty() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("pinned", "note", "abcd", 0, true));
        assertEquals(0, store.readBudgeted(0, new HashMap<>()).size());
    }

    // ---- constructor guards ----

    @Test
    void constructorRejectsNullAdapters() {
        assertThrows(NopAiAgentException.class,
                () -> new AdapterBackedAiMemoryStore(null, NoOpEmbeddingAdapter.instance(), NoOpVectorAdapter.instance()));
        assertThrows(NopAiAgentException.class,
                () -> new AdapterBackedAiMemoryStore(new InMemoryStorageAdapter(), null, NoOpVectorAdapter.instance()));
        assertThrows(NopAiAgentException.class,
                () -> new AdapterBackedAiMemoryStore(new InMemoryStorageAdapter(), NoOpEmbeddingAdapter.instance(), null));
    }

    @Test
    void clearRemovesAllItems() {
        AdapterBackedAiMemoryStore store = functionalStore();
        store.add(item("k1", "n", "c1"));
        store.add(item("k2", "n", "c2"));
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    void accessorExposeAdapters() {
        InMemoryStorageAdapter s = new InMemoryStorageAdapter();
        InMemoryEmbeddingAdapter e = new InMemoryEmbeddingAdapter();
        InMemoryVectorAdapter v = new InMemoryVectorAdapter();
        AdapterBackedAiMemoryStore store = new AdapterBackedAiMemoryStore(s, e, v);
        assertSame(s, store.getStorage());
        assertSame(e, store.getEmbedding());
        assertSame(v, store.getVector());
        assertTrue(store.isSemanticSearchEnabled());
    }
}
