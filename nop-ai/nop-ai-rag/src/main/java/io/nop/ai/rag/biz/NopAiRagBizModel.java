package io.nop.ai.rag.biz;

import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.commons.splitter.SimpleTextSplitter;
import io.nop.ai.rag.ingest.RagIngestService;
import io.nop.ai.rag.search.RagSearchService;
import io.nop.ai.rag.synthesize.RagSynthesizeService;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;

import java.util.List;
import java.util.Map;

/**
 * K3 RAG 管线 BizModel：摄取/检索/合成三 action。
 * InMemoryVectorStore 缺省装配（开发/测试），生产通过 Delta 替换为 PgVectorStore。
 */
@BizModel("/NopAiRag")
public class NopAiRagBizModel {
    private final RagIngestService ingestService;
    private final RagSearchService searchService;
    private final RagSynthesizeService synthesizeService;

    public NopAiRagBizModel(IChatService chatService, IEmbeddingModel embeddingModel,
                            IVectorStore<VectorData> vectorStore) {
        this.ingestService = new RagIngestService(
                new SimpleTextSplitter(), embeddingModel, vectorStore);
        this.searchService = new RagSearchService(embeddingModel, vectorStore);
        this.synthesizeService = new RagSynthesizeService(chatService, searchService);
    }

    @BizMutation
    public Map<String, Object> ingestDocument(
            @Name("indexId") String indexId,
            @Name("docId") String docId,
            @Name("content") String content) {
        int chunkCount = ingestService.ingest(indexId, docId, content);
        return Map.of("docId", docId, "chunkCount", chunkCount);
    }

    @BizQuery
    public List<Map<String, Object>> search(
            @Name("indexId") String indexId,
            @Name("query") String query,
            @Name("topK") @Optional Integer topK) {
        List<RagSearchService.RagSearchHit> hits = searchService.search(
                indexId, query, topK != null ? topK : 4);
        return hits.stream().map(h -> Map.<String, Object>of(
                "score", h.getScore(),
                "content", h.getContent() != null ? h.getContent() : "",
                "docId", h.getDocId() != null ? h.getDocId() : "",
                "chunkIndex", h.getChunkIndex())).toList();
    }

    @BizQuery
    public String synthesize(
            @Name("indexId") String indexId,
            @Name("query") String query,
            @Name("topK") @Optional Integer topK) {
        return synthesizeService.synthesize(indexId, query, topK != null ? topK : 4);
    }
}
