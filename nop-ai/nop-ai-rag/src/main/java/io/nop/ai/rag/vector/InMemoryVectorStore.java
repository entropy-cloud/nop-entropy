package io.nop.ai.rag.vector;

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

import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;

/**
 * K2（plan knowledge-rag/02）：{@code IVectorStore} 的内存参考实现——线程安全、
 * cosine 相似度、tenant+collection 组合键隔离（C1-003，plan 2520：同 id upsert
 * 单语句原子，并发同 id 摄取不产生重复条目）。定位：K3 RAG 管线的确定性测试
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

    /**
     * C1-003（plan 2520）：bucket 值域 CHM 化——{@code put} 单语句天然原子
     * upsert（与 PgVectorStore 的 ON CONFLICT(tenant_id,id) DO UPDATE 范式对齐），
     * 并发同 docId 摄取不再产生重复条目。store 路径 id 非空由 UUID 回退保证；
     * delete 对 null id 显式 no-op（保持旧 removeIf 形态的边际语义）。
     */
    private final Map<String, ConcurrentHashMap<Object, VectorData>> buckets = new ConcurrentHashMap<>();

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

    private ConcurrentHashMap<Object, VectorData> bucket(VectorStoreOptions options) {
        return buckets.computeIfAbsent(bucketKey(options), k -> new ConcurrentHashMap<>());
    }

    @Override
    public VectorStoreResult store(List<VectorData> vectorDataList, VectorStoreOptions options) {
        ConcurrentHashMap<Object, VectorData> bucket = bucket(options);
        List<Object> ids = new ArrayList<>(vectorDataList.size());
        for (VectorData data : vectorDataList) {
            requireVector(data);
            Object existingId = data.getMetadata(META_ID);
            final Object id = existingId != null ? existingId : UUID.randomUUID().toString();
            if (existingId == null) {
                data.addMetadata(META_ID, id);
            }
            bucket.put(id, data);
            ids.add(id);
        }
        VectorStoreResult result = new VectorStoreResult();
        result.setIds(ids);
        return result;
    }

    @Override
    public VectorStoreResult delete(Collection<Object> ids, VectorStoreOptions options) {
        ConcurrentHashMap<Object, VectorData> bucket = bucket(options);
        for (Object id : ids) {
            if (id == null) {
                continue;
            }
            bucket.remove(id);
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
        ConcurrentHashMap<Object, VectorData> bucket = bucket(options);
        int maxResults = query.getMaxResults() != null && query.getMaxResults() > 0
                ? query.getMaxResults() : VectorQueryBean.DEFAULT_MAX_RESULTS;

        record Hit(VectorData data, double score) {
        }
        // snapshot iteration: CHM values() is a weakly consistent view — the copy
        // removes visibility jitter from puts landing mid-iteration; ordering is
        // score-determined below (stable sort, fixture scores are distinct)
        List<VectorData> snapshot = new ArrayList<>(bucket.values());
        List<Hit> hits = new ArrayList<>();
        for (VectorData data : snapshot) {
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
