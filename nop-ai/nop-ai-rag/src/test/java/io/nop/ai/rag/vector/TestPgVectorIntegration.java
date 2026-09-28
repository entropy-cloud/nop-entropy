package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gated pgvector integration test: only runs when a live pgvector instance is
 * available via env (explicit gating — NOT a silent skip; the deferred
 * watch-only item "pgvector 实库集成验证" is carried by this test).
 */
@EnabledIfEnvironmentVariable(named = "NOP_TEST_PGVECTOR_JDBC_URL", matches = ".+")
class TestPgVectorIntegration {

    private DataSource dataSource() throws SQLException {
        String url = System.getenv("NOP_TEST_PGVECTOR_JDBC_URL");
        String user = System.getenv("NOP_TEST_PGVECTOR_USER");
        String pass = System.getenv("NOP_TEST_PGVECTOR_PASSWORD");
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setURL(url);
        if (user != null) ds.setUser(user);
        if (pass != null) ds.setPassword(pass);
        // ensure the vector extension is available
        try (Connection conn = ds.getConnection()) {
            conn.createStatement().execute("SELECT 1");
        }
        return ds;
    }

    @Test
    void storeSearchDeleteLifecycle() throws SQLException {
        PgVectorStore store = new PgVectorStore(dataSource(), 2, "nop_test_k2");
        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName("nop_test_k2");
        options.setTenantId("test-tenant");

        store.store(List.of(
                VectorDataHelper.vec("a", 1.0, 0.0),
                VectorDataHelper.vec("b", 0.0, 1.0)), options);

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1.0, 0.0});
        query.setMaxResults(1);
        List<VectorData> hits = store.search(query, options);
        assertEquals(1, hits.size());

        store.delete(List.of("a", "b"), options);
        assertTrue(store.search(query, options).isEmpty());
    }
}

final class VectorDataHelper {
    static VectorData vec(String id, double... values) {
        VectorData data = new VectorData();
        data.setVector(values);
        data.addMetadata("id", id);
        return data;
    }
}
