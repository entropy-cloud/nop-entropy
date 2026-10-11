package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-7.1-002（plan 2398）单测判别：search 回读必须填充 content 与 metadata jsonb
 * 各键——修复前仅回读 embedding 列，hits 的 content/docId/chunkIndex 恒 null。
 */
public class TestPgVectorSearchRowMapping {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testReadSearchMetadataFillsContentAndJsonbKeys() throws Exception {
        ResultSet rs = stubResultSet("[1,2]", "chunk-content", "{\"docId\":\"doc-1\",\"chunkIndex\":\"0\"}");

        PgVectorStore store = new PgVectorStore(stubDataSource(), 3, "nop_test_mapping");
        VectorData data = new VectorData();
        store.readSearchMetadata(data, rs);

        assertEquals("chunk-content", data.getMetadata("content"),
                "content column must be read back (P1-7.1-002)");
        assertEquals("doc-1", data.getMetadata("docId"), "jsonb keys must be parsed back");
        assertEquals("0", data.getMetadata("chunkIndex"));
    }

    @Test
    public void testNullColumnsTolerated() throws Exception {
        ResultSet rs = stubResultSet("[1,2]", null, null);
        PgVectorStore store = new PgVectorStore(stubDataSource(), 3, "nop_test_mapping");
        VectorData data = new VectorData();
        store.readSearchMetadata(data, rs);

        assertNull(data.getMetadata("content"), "null columns must not add metadata entries");
        assertNull(data.getMetadata("docId"));
    }

    /** 最小 DataSource stub：readSearchMetadata 不触达连接面。 */
    static javax.sql.DataSource stubDataSource() {
        return (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                TestPgVectorSearchRowMapping.class.getClassLoader(),
                new Class[]{javax.sql.DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("unwrap"))
                        return null;
                    return defaultFor(method.getReturnType());
                });
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        return null;
    }

    /** 最小 ResultSet stub：getString(1/2/3) 按序返回三列值。 */
    static ResultSet stubResultSet(String c1, String c2, String c3) {
        String[] values = {c1, c2, c3};
        java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getString":
                    return values[(Integer) args[0] - 1];
                case "next":
                    return true;
                default:
                    return null;
            }
        };
        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                TestPgVectorSearchRowMapping.class.getClassLoader(),
                new Class[]{ResultSet.class}, handler);
    }
}
