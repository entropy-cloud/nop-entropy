package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.core.api.vectorstore.VectorStoreResult;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
