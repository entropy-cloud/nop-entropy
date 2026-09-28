package io.nop.ai.rag.search;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.rag.NopAiRagErrors;
import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.List;

/**
 * K3 RAG 检索服务：查询文本 → IEmbeddingModel 嵌入 → IVectorStore.search → top-K
 * 结果（重算 cosine score，A2 裁定——SPI 返回 VectorData 不含 score）。
 */
public class RagSearchService {
    private final IEmbeddingModel embeddingModel;
    private final IVectorStore<VectorData> vectorStore;

    public RagSearchService(IEmbeddingModel embeddingModel, IVectorStore<VectorData> vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public List<RagSearchHit> search(String indexId, String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new NopException(NopAiRagErrors.ERR_AI_RAG_EMPTY_QUERY);
        }
        int effectiveTopK = topK > 0 ? topK : 4;

        VectorData queryVec = embeddingModel.embed(
                AiDocument.fromText(query), null);
        if (queryVec == null || queryVec.getVector() == null || queryVec.getVector().length == 0) {
            throw new NopException(NopAiRagErrors.ERR_AI_RAG_EMPTY_QUERY)
                    .param(ARG_QUERY, query);
        }

        VectorQueryBean wrapper = new VectorQueryBean();
        wrapper.setVector(queryVec.getVector());
        wrapper.setMaxResults(effectiveTopK);
        wrapper.setWithVector(true);

        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName(indexId);
        List<VectorData> results = vectorStore.search(wrapper, options);

        List<RagSearchHit> hits = new ArrayList<>(results.size());
        for (VectorData data : results) {
            double score = cosine(queryVec.getVector(), data.getVector());
            hits.add(new RagSearchHit(score,
                    (String) data.getMetadata("content"),
                    (String) data.getMetadata("docId"),
                    data.getMetadata("chunkIndex") != null
                            ? ((Number) data.getMetadata("chunkIndex")).intValue() : -1));
        }
        return hits;
    }

    private static double cosine(double[] a, double[] b) {
        if (b == null || b.length != a.length) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static final String ARG_QUERY = "query";

    public static class RagSearchHit {
        private final double score;
        private final String content;
        private final String docId;
        private final int chunkIndex;

        public RagSearchHit(double score, String content, String docId, int chunkIndex) {
            this.score = score;
            this.content = content;
            this.docId = docId;
            this.chunkIndex = chunkIndex;
        }

        public double getScore() { return score; }
        public String getContent() { return content; }
        public String getDocId() { return docId; }
        public int getChunkIndex() { return chunkIndex; }
    }
}
