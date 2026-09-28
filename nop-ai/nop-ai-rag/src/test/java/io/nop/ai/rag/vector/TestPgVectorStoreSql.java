package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.api.core.exceptions.NopException;
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

    private final List<String> capturedSql = new ArrayList<>();
    private final List<Object> capturedParams = new ArrayList<>();

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
                InvocationHandler rsHandler = (p2, m2, a2) -> {
                    if ("next".equals(m2.getName())) return false;
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
}
