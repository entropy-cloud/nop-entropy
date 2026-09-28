package io.nop.ai.rag.vector;

import io.nop.ai.core.api.embedding.CosineSimilarity;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.core.api.vectorstore.VectorStoreResult;
import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;

/**
 * K2（plan knowledge-rag/02）：{@code IVectorStore} 的内存参考实现——线程安全、
 * cosine 相似度、tenant+collection 组合键隔离。定位：K3 RAG 管线的确定性测试
 * 后端与无 pgvector 环境的开发后端（04-rag-module-position §四 触发条件已达成，
 * 原"拒绝 InMemory"裁定的两个前提均已按 §八 登记解除；**不注册 IoC default
 * bean**——保 SPI 边界语义，装配归 K3 plan）。
 *
 * <p>契约裁定（owner doc {@code vector-store.md} A1-A4）：search 要求
 * {@code query.vector} 必填（缺失 fail-loud——text→embedding 转换归 K3 管线）；
 * condition TreeBean 静默忽略（v1 无谓词求值）。
 */
public class InMemoryVectorStore extends IVectorStore<VectorData> {

    /** tenant 归一哨兵：null/空 tenantId 统一存取此键（与 PgVectorStore 对称）。 */
    public static final String DEFAULT_TENANT = "_default_";

    /** VectorData id 的 Metadata 约定键（K2 契约，owner doc 登记）。 */
    public static final String META_ID = "id";

    private final Map<String, CopyOnWriteArrayList<VectorData>> buckets = new ConcurrentHashMap<>();

    private static String bucketKey(VectorStoreOptions options) {
        String collection = options == null ? null : options.getCollectionName();
        if (collection == null || collection.isEmpty()) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "collectionName must be set for in-memory vector store");
        }
        String tenant = options != null && options.getTenantId() != null
                && !options.getTenantId().isEmpty() ? options.getTenantId() : DEFAULT_TENANT;
        return tenant + "/" + collection;
    }

    private CopyOnWriteArrayList<VectorData> bucket(VectorStoreOptions options) {
        return buckets.computeIfAbsent(bucketKey(options), k -> new CopyOnWriteArrayList<>());
    }

    @Override
    public VectorStoreResult store(List<VectorData> vectorDataList, VectorStoreOptions options) {
        CopyOnWriteArrayList<VectorData> bucket = bucket(options);
        List<Object> ids = new ArrayList<>(vectorDataList.size());
        for (VectorData data : vectorDataList) {
            requireVector(data);
            Object existingId = data.getMetadata(META_ID);
            final Object id = existingId != null ? existingId : UUID.randomUUID().toString();
            if (existingId == null) {
                data.addMetadata(META_ID, id);
            }
            bucket.removeIf(existing -> id.equals(existing.getMetadata(META_ID)));
            bucket.add(data);
            ids.add(id);
        }
        VectorStoreResult result = new VectorStoreResult();
        result.setIds(ids);
        return result;
    }

    @Override
    public VectorStoreResult delete(Collection<Object> ids, VectorStoreOptions options) {
        CopyOnWriteArrayList<VectorData> bucket = bucket(options);
        for (Object id : ids) {
            bucket.removeIf(existing -> id.equals(existing.getMetadata(META_ID)));
        }
        VectorStoreResult result = new VectorStoreResult();
        result.setIds(new ArrayList<>(ids));
        return result;
    }

    @Override
    public VectorStoreResult update(List<VectorData> vectorDataList, VectorStoreOptions options) {
        return store(vectorDataList, options);
    }

    @Override
    public List<VectorData> search(VectorQueryBean query, VectorStoreOptions options) {
        double[] queryVector = requireQueryVector(query);
        CopyOnWriteArrayList<VectorData> bucket = bucket(options);
        int maxResults = query.getMaxResults() != null && query.getMaxResults() > 0
                ? query.getMaxResults() : VectorQueryBean.DEFAULT_MAX_RESULTS;

        record Hit(VectorData data, double score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (VectorData data : bucket) {
            double score = cosine(queryVector, data.getVector());
            if (query.getMinScore() != null && score < query.getMinScore()) {
                continue;
            }
            hits.add(new Hit(data, score));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());

        List<VectorData> out = new ArrayList<>(Math.min(maxResults, hits.size()));
        // v1: results always carry vectors (owner doc A1 — withVector/outputVector ignored)
        for (Hit hit : hits.subList(0, Math.min(maxResults, hits.size()))) {
            out.add(hit.data());
        }
        return out;
    }

    private static void requireVector(VectorData data) {
        if (data == null || data.getVector() == null || data.getVector().length == 0) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "vector data must carry a non-empty vector");
        }
        double norm = 0;
        for (double v : data.getVector()) {
            norm += v * v;
        }
        if (norm == 0) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "zero vector has no cosine similarity");
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

    private static double cosine(double[] a, double[] b) {
        if (b == null || b.length != a.length) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "vector dimension mismatch: query=" + a.length);
        }
        // zero-vector guard (stricter than CosineSimilarity's EPSILON fallback):
        // a zero vector has no direction, so similarity is undefined here
        double normA = 0;
        double normB = 0;
        for (double v : a) {
            normA += v * v;
        }
        for (double v : b) {
            normB += v * v;
        }
        if (normA == 0 || normB == 0) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "zero vector has no cosine similarity");
        }
        return io.nop.ai.core.api.embedding.CosineSimilarity.between(
                toVectorData(a), toVectorData(b));
    }

    private static VectorData toVectorData(double[] v) {
        VectorData data = new VectorData();
        data.setVector(v);
        return data;
    }
}
