package io.nop.search.lucene;

import io.nop.api.core.exceptions.NopException;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency coordination carrier for {@link LuceneSearchEngine} (plan 2563 MA-01, C10-001 +
 * C10-007). Same package as the engine so the three coordination maps (package-private fields,
 * explicit production visibility seam) can be size-asserted.
 *
 * <p>Legs: (1) concurrent addDocs same topic same docId terminates unique; (2) removeTopic
 * overlapping in-flight search leaks no AlreadyClosedException; (3) access after destroy is
 * rejected and rebuilds nothing; (4) topic-capacity LRU eviction; (5) three-map size contract
 * after removeTopic.
 */
public class TestLuceneSearchEngineCoordination {

    private LuceneSearchEngine newEngine(Path dir, int maxTopics) {
        LuceneConfig config = new LuceneConfig();
        config.setIndexDir(dir.toString());
        config.setMaxTopics(maxTopics);
        LuceneSearchEngine engine = new LuceneSearchEngine();
        engine.setConfig(config);
        engine.init();
        return engine;
    }

    private SearchableDoc doc(String id) {
        SearchableDoc d = new SearchableDoc();
        d.setId(id);
        d.setContent("body-" + id);
        d.setTitle("title-" + id);
        return d;
    }

    private SearchResponse search(LuceneSearchEngine engine, String topic, String query) {
        SearchRequest request = new SearchRequest();
        request.setTopic(topic);
        request.setQuery(query);
        request.setLimit(10);
        return engine.search(request);
    }

    // leg 1 + mutation chain 1 target: concurrent addDocs same docId -> unique final state
    @Test
    public void concurrentAddDocsSameDocIdFinalStateUnique(@TempDir Path dir) throws Exception {
        LuceneSearchEngine engine = newEngine(dir, 0);
        try {
            String topic = "t1";
            int n = 30;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Runnable writer = () -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < n; i++) {
                    engine.addDocs(topic, Collections.singletonList(doc("same-id")));
                }
            };
            pool.submit(writer);
            pool.submit(writer);
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

            SearchResponse response = search(engine, topic, "body-same-id");
            assertEquals(1, response.getTotal(), "expected exactly one doc with same-id, got " + response.getTotal());
        } finally {
            engine.destroy();
        }
    }

    // leg 2 + mutation chain 1 target: removeTopic x in-flight search -> no AlreadyClosedException
    @Test
    public void removeTopicOverlappingSearchNoAlreadyClosed(@TempDir Path dir) throws Exception {
        LuceneSearchEngine engine = newEngine(dir, 0);
        try {
            String topic = "t2";
            engine.addDocs(topic, Collections.singletonList(doc("d1")));
            AtomicReference<Throwable> failure = new AtomicReference<>();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            CountDownLatch start = new CountDownLatch(1);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 400; i++) {
                        search(engine, topic, "body-d1");
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            start.countDown();
            for (int i = 0; i < 20; i++) {
                engine.removeTopic(topic);
                engine.addDocs(topic, Collections.singletonList(doc("d1")));
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
            Throwable t = failure.get();
            if (t != null) {
                throw new AssertionError("in-flight search failed during removeTopic overlap", t);
            }
        } finally {
            engine.destroy();
        }
    }

    // leg 3 + mutation chain 2 target: destroy rejects further access and rebuilds nothing
    @Test
    public void rejectAccessAfterDestroy(@TempDir Path dir) {
        LuceneSearchEngine engine = newEngine(dir, 0);
        engine.addDocs("t3", Collections.singletonList(doc("d1")));
        engine.destroy();
        assertThrows(NopException.class, () -> search(engine, "t3", "body-d1"));
        assertThrows(NopException.class, () -> engine.addDocs("t3", Collections.singletonList(doc("d2"))));

        // double-check semantics: failed rebuilds must not leave index resources in the maps
        assertEquals(0, engine.indexDirs.size());
        assertEquals(0, engine.indexWriters.size());
        assertEquals(0, engine.searcherManagers.size());
    }

    // leg 4 + mutation chain 3 target: topic-capacity LRU eviction reuses removeTopic close
    @Test
    public void capacityEvictsLruTopic(@TempDir Path dir) {
        LuceneSearchEngine engine = newEngine(dir, 2);
        try {
            engine.addDocs("a", Collections.singletonList(doc("d-a")));
            engine.addDocs("b", Collections.singletonList(doc("d-b")));
            assertEquals(2, engine.indexDirs.size());
            // touch "a" again so "b" becomes the least recently used topic
            search(engine, "a", "body-d-a");
            engine.addDocs("c", Collections.singletonList(doc("d-c")));

            assertEquals(2, engine.indexDirs.size(), "capacity bound must be enforced");
            assertFalse(engine.indexDirs.containsKey("b"), "LRU topic 'b' must be evicted");
            assertTrue(engine.indexDirs.containsKey("a"));
            assertTrue(engine.indexDirs.containsKey("c"));
            assertFalse(engine.indexWriters.containsKey("b"));
            // evicted topic stays searchable: computeIfAbsent rebuild reuses the close sequence
            search(engine, "b", "body-d-b");
            assertTrue(engine.indexDirs.containsKey("b"));
        } finally {
            engine.destroy();
        }
    }

    // leg 5 (C10-007): three-map size contract after removeTopic
    @Test
    public void removeTopicClearsAllThreeMaps(@TempDir Path dir) {
        LuceneSearchEngine engine = newEngine(dir, 0);
        try {
            engine.addDocs("t5", Collections.singletonList(doc("d1")));
            // addDocs creates dir+writer; the searcher manager is created by the first read
            assertEquals(1, engine.indexDirs.size());
            assertEquals(1, engine.indexWriters.size());
            assertEquals(0, engine.searcherManagers.size());
            search(engine, "t5", "body-d1");
            assertEquals(1, engine.searcherManagers.size());

            engine.removeTopic("t5");
            assertEquals(0, engine.indexDirs.size());
            assertEquals(0, engine.indexWriters.size());
            assertEquals(0, engine.searcherManagers.size());
        } finally {
            engine.destroy();
        }
    }
}
