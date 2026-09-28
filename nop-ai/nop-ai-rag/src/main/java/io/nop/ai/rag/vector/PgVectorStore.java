package io.nop.ai.rag.vector;

import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.core.api.vectorstore.VectorStoreResult;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.UUID;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;

/**
 * K2（plan knowledge-rag/02）：{@code IVectorStore} 的 pgvector JDBC 驱动。
 *
 * <p>契约裁定（owner doc {@code vector-store.md} A1-A4）：search 要求
 * {@code query.vector} 必填（缺失 fail-loud）；租户隔离 = UNIQUE(tenant_id,id)
 * 复合唯一 + 全语句 tenant_id 谓词（空租户归一 {@link #DEFAULT_TENANT}）；
 * condition TreeBean 记 WARN 后忽略；collection 名白名单
 * {@code [a-zA-Z_][a-zA-Z0-9_]*}（表名拼接，参数化不可用，fail-loud）。
 *
 * <p>DDL 惰性幂等（首次访问执行 CREATE TABLE IF NOT EXISTS）——替代
 * nop-db-migration（偏离裁定登记 owner doc）。集成方注入其平台
 * {@link DataSource}（本模块不耦合 nop-dao，README 使用约束）。
 */
public class PgVectorStore extends IVectorStore<VectorData> {

    private static final Logger LOG = LoggerFactory.getLogger(PgVectorStore.class);

    public static final String DEFAULT_TENANT = "_default_";
    public static final String META_ID = "id";
    private static final Pattern COLLECTION_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final DataSource dataSource;
    private final int dimension;
    private final String defaultCollection;
    private volatile boolean schemaReady;

    public PgVectorStore(DataSource dataSource, int dimension, String defaultCollection) {
        if (dataSource == null) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "dataSource must not be null");
        }
        if (dimension < 1) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "dimension must be >= 1");
        }
        requireValidCollection(defaultCollection);
        this.dataSource = dataSource;
        this.dimension = dimension;
        this.defaultCollection = defaultCollection;
    }

    private static void requireValidCollection(String collection) {
        if (collection == null || !COLLECTION_PATTERN.matcher(collection).matches()) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "collection name must match [a-zA-Z_][a-zA-Z0-9_]*: " + collection);
        }
    }

    private static String tenantOf(VectorStoreOptions options) {
        String tenant = options != null && options.getTenantId() != null
                && !options.getTenantId().isEmpty() ? options.getTenantId() : DEFAULT_TENANT;
        return tenant;
    }

    private void ensureSchema(Connection connection, String collection) throws SQLException {
        if (schemaReady) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "CREATE EXTENSION IF NOT EXISTS vector")) {
            stmt.execute();
        } catch (SQLException e) {
            // extension may be pre-created by the DBA without create privilege
            LOG.info("nop.ai.rag.pgvector.create-extension-skipped: {}", e.getMessage());
        }
        // table name cannot be parameterized — validated by the whitelist above
        try (PreparedStatement stmt = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + collection
                        + " (id text NOT NULL, embedding vector, "
                        + "content text, metadata jsonb, tenant_id text NOT NULL, "
                        + "UNIQUE(tenant_id, id))")) {
            stmt.execute();
        }
        schemaReady = true;
    }

    @Override
    public VectorStoreResult store(List<VectorData> vectorDataList, VectorStoreOptions options) {
        String collection = resolveCollection(options);
        String tenant = tenantOf(options);
        List<Object> ids = new ArrayList<>(vectorDataList.size());
        // validate before opening a connection (fail-fast, no side effects on invalid input)
        for (VectorData data : vectorDataList) {
            requireVector(data);
            checkDimension(data);
        }
        try (Connection connection = dataSource.getConnection()) {
            ensureSchema(connection, collection);
            String sql = "INSERT INTO " + collection
                    + " (id, embedding, content, metadata, tenant_id) VALUES (?, ?::vector, ?, ?::jsonb, ?) "
                    + "ON CONFLICT(tenant_id, id) DO UPDATE SET embedding = EXCLUDED.embedding, "
                    + "content = EXCLUDED.content, metadata = EXCLUDED.metadata";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (VectorData data : vectorDataList) {
                    Object id = resolveId(data);
                    stmt.setObject(1, id);
                    stmt.setObject(2, toVectorLiteral(data.getVector()));
                    stmt.setObject(3, data.getMetadata("content"));
                    stmt.setObject(4, toMetadataJson(data));
                    stmt.setObject(5, tenant);
                    stmt.addBatch();
                    ids.add(id);
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "pgvector store failed: " + e.getMessage());
        }
        VectorStoreResult result = new VectorStoreResult();
        result.setIds(ids);
        return result;
    }

    @Override
    public VectorStoreResult update(List<VectorData> vectorDataList, VectorStoreOptions options) {
        return store(vectorDataList, options);
    }

    @Override
    public VectorStoreResult delete(Collection<Object> ids, VectorStoreOptions options) {
        String collection = resolveCollection(options);
        String tenant = tenantOf(options);
        try (Connection connection = dataSource.getConnection()) {
            ensureSchema(connection, collection);
            String sql = "DELETE FROM " + collection
                    + " WHERE tenant_id = ? AND id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (Object id : ids) {
                    stmt.setObject(1, tenant);
                    stmt.setObject(2, id);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "pgvector delete failed: " + e.getMessage());
        }
        VectorStoreResult result = new VectorStoreResult();
        result.setIds(new ArrayList<>(ids));
        return result;
    }

    @Override
    public List<VectorData> search(VectorQueryBean query, VectorStoreOptions options) {
        double[] queryVector = requireQueryVector(query);
        String collection = resolveCollection(options);
        String tenant = tenantOf(options);
        int maxResults = query.getMaxResults() != null && query.getMaxResults() > 0
                ? query.getMaxResults() : VectorQueryBean.DEFAULT_MAX_RESULTS;
        float minScore = query.getMinScore() != null ? query.getMinScore().floatValue() : 0f;

        if (query.getCondition() != null) {
            LOG.warn("nop.ai.rag.pgvector.condition-ignored: v1 does not push down "
                    + "TreeBean conditions (owner doc A4)");
        }

        List<VectorData> out = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            ensureSchema(connection, collection);
            String sql = "SELECT embedding, content, metadata FROM " + collection
                    + " WHERE tenant_id = ? AND (1 - (embedding <=> ?::vector)) >= ? "
                    + "ORDER BY embedding <=> ?::vector LIMIT ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, tenant);
                stmt.setObject(2, toVectorLiteral(queryVector));
                stmt.setFloat(3, minScore);
                stmt.setObject(4, toVectorLiteral(queryVector));
                stmt.setInt(5, maxResults);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        VectorData data = new VectorData();
                        data.setVector(fromVectorLiteral(rs.getString(1)));
                        out.add(data);
                    }
                }
            }
        } catch (SQLException e) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "pgvector search failed: " + e.getMessage());
        }
        return out;
    }

    // ---- helpers ----

    private String resolveCollection(VectorStoreOptions options) {
        String collection = options != null && options.getCollectionName() != null
                && !options.getCollectionName().isEmpty()
                ? options.getCollectionName() : defaultCollection;
        requireValidCollection(collection);
        return collection;
    }

    private static Object resolveId(VectorData data) {
        Object id = data.getMetadata(META_ID);
        if (id == null) {
            id = UUID.randomUUID().toString();
            data.addMetadata(META_ID, id);
        }
        return id;
    }

    private void checkDimension(VectorData data) {
        if (data.getVector().length != dimension) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "vector dimension mismatch: expected " + dimension
                            + ", got " + data.getVector().length);
        }
    }

    private static double[] requireQueryVector(VectorQueryBean query) {
        if (query == null || query.getVector() == null || query.getVector().length == 0) {
            throw new NopException(io.nop.ai.rag.NopAiRagErrors.ERR_AI_RAG_QUERY_VECTOR_REQUIRED)
                    .param(ARG_DETAIL, "search requires query.vector (text->embedding "
                            + "conversion belongs to the RAG pipeline, owner doc A1)");
        }
        return query.getVector();
    }

    private static void requireVector(VectorData data) {
        if (data == null || data.getVector() == null || data.getVector().length == 0) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "vector data must carry a non-empty vector");
        }
    }

    /**
     * pgvector 向量字面量：{@code [1.0,2.0,...]}（经 ?::vector 参数传递）。
     */
    static String toVectorLiteral(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static String toMetadataJson(VectorData data) {
        // metadata map serialized as jsonb; content is a separate column
        Map<String, Object> meta = data.getMetadata();
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            if ("content".equals(entry.getKey())) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":")
                    .append('"').append(entry.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    static double[] fromVectorLiteral(String literal) {
        String inner = literal.substring(1, literal.length() - 1);
        String[] parts = inner.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i].trim());
        }
        return vector;
    }
}
