package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.core.api.vectorstore.VectorStoreResult;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInMemoryVectorStore {

    private VectorData vec(String id, String tenant, double... values) {
        VectorData data = new VectorData();
        data.setVector(values);
        if (id != null) {
            data.addMetadata("id", id);
        }
        if (tenant != null) {
            data.addMetadata("tenant", tenant);
        }
        return data;
    }

    private VectorStoreOptions opts(String collection, String tenant) {
        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName(collection);
        if (tenant != null) {
            options.setTenantId(tenant);
        }
        return options;
    }

    @Test
    void storeGeneratesAndWritesBackId() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        VectorData data = vec(null, null, 1, 0);
        VectorStoreResult result = store.store(data, opts("c1", null));
        assertEquals(1, result.getIds().size());
        assertNotNull(data.getMetadata("id"), "generated id must be written back to metadata");
    }

    @Test
    void searchRanksByCosineAndAppliesTopK() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(List.of(
                vec("a", null, 1, 0),
                vec("b", null, 0.9, 0.1),
                vec("c", null, 0, 1)), opts("c1", null));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1, 0});
        query.setMaxResults(2);
        List<VectorData> hits = store.search(query, opts("c1", null));

        assertEquals(2, hits.size());
        assertEquals("a", hits.get(0).getMetadata("id"));
        assertEquals("b", hits.get(1).getMetadata("id"));
    }

    @Test
    void minScoreFilters() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(List.of(
                vec("a", null, 1, 0),
                vec("c", null, 0, 1)), opts("c1", null));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1, 0});
        query.setMinScore(0.5);
        List<VectorData> hits = store.search(query, opts("c1", null));
        assertEquals(1, hits.size());
        assertEquals("a", hits.get(0).getMetadata("id"));
    }

    @Test
    void updateReflectedInSearch() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(vec("a", null, 1, 0), opts("c1", null));
        store.update(vec("a", null, 0, 1), opts("c1", null));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{0, 1});
        List<VectorData> hits = store.search(query, opts("c1", null));
        assertEquals("a", hits.get(0).getMetadata("id"));
        // plan 2520 C10-002 strength pin (唯一允许的既有钉同 diff 改写): the old entry
        // must be REPLACED, not duplicated — a removeIf-then-add race keeps both.
        assertEquals(1, hits.size(),
                "upsert-by-id must replace: update must leave exactly one entry for the id");
    }

    @Test
    void deleteRemovesFromSearch() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(vec("a", null, 1, 0), opts("c1", null));
        store.delete(List.of("a"), opts("c1", null));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1, 0});
        assertTrue(store.search(query, opts("c1", null)).isEmpty());
    }

    @Test
    void tenantIsolation() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(vec("secret", "t1", 1, 0), opts("c1", "t1"));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1, 0});
        // tenant t2 cannot see t1 data
        assertTrue(store.search(query, opts("c1", "t2")).isEmpty());
        // default tenant cannot see t1 data
        assertTrue(store.search(query, opts("c1", null)).isEmpty());
        // delete across tenants must not remove t1 data
        store.delete(List.of("secret"), opts("c1", "t2"));
        assertEquals("secret", store.search(query, opts("c1", "t1")).get(0).getMetadata("id"));
    }

    @Test
    void zeroVectorFailsLoud() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        assertThrows(NopException.class, () -> store.store(vec("z", null, 0, 0), opts("c1", null)));
        assertThrows(NopException.class, () -> store.search(
                new VectorQueryBean().text("q"), opts("c1", null)));
    }

    @Test
    void missingCollectionFailsLoud() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        assertThrows(NopException.class, () -> store.store(vec("a", null, 1, 0), null));
    }

    @Test
    void conditionSilentlyIgnored() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.store(vec("a", null, 1, 0), opts("c1", null));
        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1, 0});
        // A4: condition TreeBean silently ignored by the in-memory backend
        List<VectorData> hits = store.search(query, opts("c1", null));
        assertEquals(1, hits.size());
    }

    @Test
    void singleItemDelegates() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        VectorData data = vec("a", null, 1, 0);
        store.store(data, opts("c1", null));
        store.update(vec("a", null, 0, 1), opts("c1", null));
        store.delete(List.of("a"), opts("c1", null));
        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{0, 1});
        assertTrue(store.search(query, opts("c1", null)).isEmpty());
    }

    // ---- C1-003 (plan 2520): same-id concurrent upsert must not duplicate ----

    @Test
    void concurrentUpsertSameIdProducesSingleEntry() throws Exception {
        int trials = 10;
        for (int trial = 0; trial < trials; trial++) {
            InMemoryVectorStore store = new InMemoryVectorStore();
            int threads = 4;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(() -> {
                    try {
                        start.await();
                        store.store(vec("dup", null, 1, 0), opts("c1", null));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                t.start();
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "trial " + trial + " timed out");

            VectorQueryBean query = new VectorQueryBean();
            query.setVector(new double[]{1, 0});
            List<VectorData> hits = store.search(query, opts("c1", null));
            assertTrue(hits.size() <= 1, "trial " + trial + ": concurrent same-id upsert produced "
                    + hits.size() + " entries — removeIf+add compound upsert is not atomic (C1-003)");
        }
    }
}
