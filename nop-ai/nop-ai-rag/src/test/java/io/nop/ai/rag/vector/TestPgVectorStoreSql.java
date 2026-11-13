package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPgVectorStoreSql {

    // plan 2520: the C6-001 store-read E2E exercises readSearchMetadata, whose jsonb
    // parse needs the platform JSON provider (same setup as TestPgVectorSearchRowMapping)
    @BeforeAll
    static void init() {
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", "false");
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", "false");
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", "false");
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    private final List<String> capturedSql = new ArrayList<>();
    private final List<Object> capturedParams = new ArrayList<>();
    // C6-001 E2E (rule 22): when enabled, fake search statements return one row
    private boolean fakeSearchRowEnabled;

    private DataSource fakeDataSource() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return fakeConnection();
            }
            return defaultReturn(method.getReturnType());
        };
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DataSource.class}, handler);
    }

    private Connection fakeConnection() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                String sql = (String) args[0];
                capturedSql.add(sql);
                return fakePreparedStatement();
            }
            return defaultReturn(method.getReturnType());
        };
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Connection.class}, handler);
    }

    private PreparedStatement fakePreparedStatement() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("setObject".equals(method.getName()) || "setFloat".equals(method.getName())
                    || "setInt".equals(method.getName())) {
                if (args.length > 1) {
                    capturedParams.add(args[1]);
                }
                return null;
            }
            if ("executeQuery".equals(method.getName())) {
                final boolean[] consumed = {false};
                InvocationHandler rsHandler = (p2, m2, a2) -> {
                    if ("next".equals(m2.getName())) {
                        if (fakeSearchRowEnabled && !consumed[0]) {
                            consumed[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(m2.getName())) {
                        int idx = (Integer) a2[0];
                        if (idx == 1) return "[1.0,2.0]";
                        if (idx == 2) return "ctx";
                        if (idx == 3) return "{\"id\":\"doc1\"}";
                        return null;
                    }
                    return defaultReturn(m2.getReturnType());
                };
                return Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class[]{ResultSet.class}, rsHandler);
            }
            if ("execute".equals(method.getName())) {
                return false;
            }
            if ("executeBatch".equals(method.getName())) {
                return new int[0];
            }
            if ("clearBatch".equals(method.getName()) || "addBatch".equals(method.getName())) {
                return null;
            }
            return defaultReturn(method.getReturnType());
        };
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{PreparedStatement.class}, handler);
    }

    private Object defaultReturn(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private VectorStoreOptions opts() {
        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName("docs");
        options.setTenantId("t1");
        return options;
    }

    private PgVectorStore newStore() {
        return new PgVectorStore(fakeDataSource(), 2, "docs");
    }

    @Test
    void ddlIsIdempotentWithRealTableName() {
        capturedSql.clear();
        PgVectorStore store = newStore();

        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        store.store(List.of(data), opts());

        String ddl = capturedSql.stream()
                .filter(s -> s.contains("CREATE TABLE IF NOT EXISTS")).findFirst().orElse("");
        assertTrue(ddl.contains("docs"),
                "DDL must reference the actual collection name (audit Blocker regression): " + ddl);
        assertTrue(ddl.contains("UNIQUE(tenant_id, id)"), "DDL must declare tenant-id uniqueness: " + ddl);
        assertTrue(ddl.contains("IF NOT EXISTS"), "DDL must be idempotent: " + ddl);
    }

    @Test
    void storeProducesUpsertWithTenantPredicate() {
        capturedSql.clear();
        PgVectorStore store = newStore();

        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        store.store(List.of(data), opts());

        assertTrue(capturedSql.stream().anyMatch(s -> s.contains("ON CONFLICT(tenant_id, id)")
                        && s.contains("?::vector")),
                "store must produce upsert with vector cast: " + capturedSql);
    }

    @Test
    void deleteHasTenantPredicate() {
        capturedSql.clear();
        PgVectorStore store = newStore();
        store.delete(List.of("a"), opts());

        String deleteSql = capturedSql.stream()
                .filter(s -> s.startsWith("DELETE")).findFirst().orElse("");
        assertTrue(deleteSql.contains("tenant_id = ?"),
                "delete must carry tenant predicate (isolation): " + deleteSql);
    }

    @Test
    void searchProducesCosineWithTenantPredicate() {
        capturedSql.clear();
        capturedParams.clear();
        PgVectorStore store = newStore();

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1.0, 0.0});
        query.setMaxResults(5);
        query.setMinScore(0.3);
        store.search(query, opts());

        String searchSql = capturedSql.stream()
                .filter(s -> s.contains("<=>")).findFirst().orElse("");
        assertTrue(searchSql.contains("tenant_id = ?"), "search must filter by tenant: " + searchSql);
        assertTrue(searchSql.contains("LIMIT ?"), "search must apply LIMIT: " + searchSql);
        assertTrue(searchSql.contains("?::vector"), "query vector must be cast: " + searchSql);
        assertTrue(capturedParams.contains("[1.0,0.0]"),
                "vector literal must be a parameter: " + capturedParams);
    }

    @Test
    void conditionTriggersWarnNotCrash() {
        capturedSql.clear();
        PgVectorStore store = newStore();
        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1.0, 0.0});
        // A4: condition is ignored with WARN, search must not crash
        store.search(query, opts());
        assertTrue(capturedSql.stream().anyMatch(s -> s.contains("<=>")));
    }

    @Test
    void illegalCollectionNameFailsLoud() {
        assertThrows(NopException.class, () -> new PgVectorStore(fakeDataSource(), 2, "docs; DROP TABLE x"));
    }

    @Test
    void dimensionMismatchFailsLoud() {
        PgVectorStore store = new PgVectorStore(fakeDataSource(), 3, "docs");
        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        assertThrows(NopException.class, () -> store.store(List.of(data), opts()));
    }

    @Test
    void queryVectorRequired() {
        PgVectorStore store = newStore();
        VectorQueryBean query = new VectorQueryBean();
        query.setText("hello");
        assertThrows(NopException.class, () -> store.search(query, opts()));
    }

    @Test
    void vectorLiteralFormat() {
        assertEquals("[1.0,2.0,3.5]", PgVectorStore.toVectorLiteral(new double[]{1.0, 2.0, 3.5}));
        double[] back = PgVectorStore.fromVectorLiteral("[1.0,2.0,3.5]");
        assertEquals(3, back.length);
        assertEquals(3.5, back[2], 1e-9);
    }

    // ---- C6-001 (plan 2520): schema-ready guard must be per-collection ----

    private VectorStoreOptions opts(String collection) {
        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName(collection);
        options.setTenantId("t1");
        return options;
    }

    private long countCreateTableDdl() {
        return capturedSql.stream().filter(s -> s.contains("CREATE TABLE IF NOT EXISTS")).count();
    }

    @Test
    void schemaReadyIsPerCollectionSecondCollectionGetsOwnDdl() {
        capturedSql.clear();
        PgVectorStore store = newStore();

        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        store.store(List.of(data), opts("docs_a"));

        VectorQueryBean query = new VectorQueryBean();
        query.setVector(new double[]{1.0, 2.0});
        store.search(query, opts("docs_b"));

        assertEquals(2, countCreateTableDdl(),
                "C6-001: per-collection guard must issue DDL for the second collection (a store-level"
                        + " ready flag permanently breaks every collection after the first): " + capturedSql);
        assertTrue(capturedSql.stream().filter(s -> s.contains("CREATE TABLE IF NOT EXISTS"))
                        .anyMatch(s -> s.contains("docs_b")),
                "DDL for the second collection must reference its own table: " + capturedSql);
    }

    @Test
    void singleCollectionRepeatOperationsIssueDdlOnlyOnce() {
        capturedSql.clear();
        PgVectorStore store = newStore();

        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        store.store(List.of(data), opts());
        store.delete(List.of("a"), opts());

        assertEquals(1, countCreateTableDdl(),
                "anti-overfix: ready-set fast path must not re-issue DDL for an already-ready collection: "
                        + capturedSql);
    }

    @Test
    void updateDelegatesThroughPerCollectionGuard() {
        capturedSql.clear();
        PgVectorStore store = newStore();

        VectorData data = new VectorData();
        data.setVector(new double[]{1.0, 2.0});
        store.update(List.of(data), opts());

        assertTrue(capturedSql.stream().anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS")),
                "rule 23 wiring: update delegates to store and must pass the per-collection guard: "
                        + capturedSql);
    }

    @Test
    void e2eStoreAndSearchAcrossTwoCollectionsBothHit() {
        capturedSql.clear();
        fakeSearchRowEnabled = true;
        try {
            PgVectorStore store = newStore();
            VectorData data = new VectorData();
            data.setVector(new double[]{1.0, 2.0});
            data.addMetadata("id", "doc1");
            store.store(List.of(data), opts("docs_a"));
            store.store(List.of(data), opts("docs_b"));

            VectorQueryBean query = new VectorQueryBean();
            query.setVector(new double[]{1.0, 2.0});
            List<VectorData> hitsA = store.search(query, opts("docs_a"));
            List<VectorData> hitsB = store.search(query, opts("docs_b"));

            assertEquals(1, hitsA.size(), "collection a must return its row (rule 22 store-read chain)");
            assertEquals("doc1", hitsA.get(0).getMetadata("id"));
            assertEquals("ctx", hitsA.get(0).getMetadata("content"));
            assertEquals(1, hitsB.size(), "collection b must return its row: the second collection"
                    + " must be usable, not permanently broken");
            assertEquals("doc1", hitsB.get(0).getMetadata("id"));

            assertEquals(2, countCreateTableDdl(),
                    "per-collection guard issues one DDL per collection: " + capturedSql);
            assertTrue(capturedSql.stream().filter(s -> s.contains("INSERT INTO"))
                            .anyMatch(s -> s.contains("docs_a")),
                    "upsert routed to collection a: " + capturedSql);
            assertTrue(capturedSql.stream().filter(s -> s.contains("INSERT INTO"))
                            .anyMatch(s -> s.contains("docs_b")),
                    "upsert routed to collection b: " + capturedSql);
            assertTrue(capturedSql.stream().filter(s -> s.contains("<=>"))
                            .anyMatch(s -> s.contains("docs_b")),
                    "search routed to collection b: " + capturedSql);
        } finally {
            fakeSearchRowEnabled = false;
        }
    }
}
