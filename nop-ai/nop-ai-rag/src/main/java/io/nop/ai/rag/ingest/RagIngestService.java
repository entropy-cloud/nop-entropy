package io.nop.ai.rag.ingest;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.ai.core.commons.splitter.SimpleTextSplitter;
import io.nop.ai.rag.NopAiRagErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static io.nop.ai.rag.NopAiRagErrors.ARG_DOC_ID;

/**
 * K3 RAG 摄取服务：文档 → splitter 切分 → IEmbeddingModel 批量嵌入 → IVectorStore 存储。
 * 每个 chunk 的 Metadata 记录 docId / chunkIndex / content（检索命中后回传来源）。
 */
@Singleton
public class RagIngestService {
    private static final int DEFAULT_CHUNK_SIZE = 512;

    private final IAiTextSplitter splitter;
    private final IEmbeddingModel embeddingModel;
    private final IVectorStore<VectorData> vectorStore;

    @Inject
    public RagIngestService(IAiTextSplitter splitter, IEmbeddingModel embeddingModel,
                            IVectorStore<VectorData> vectorStore) {
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    /**
     * @return 摄取的 chunk 数量
     */
    public int ingest(String indexId, String docId, String content) {
        if (docId == null || docId.isEmpty()) {
            throw new NopException(NopAiRagErrors.ERR_AI_RAG_EMPTY_DOCUMENT)
                    .param(ARG_DOC_ID, docId);
        }
        if (content == null || content.isBlank()) {
            throw new NopException(NopAiRagErrors.ERR_AI_RAG_EMPTY_DOCUMENT)
                    .param(ARG_DOC_ID, docId);
        }

        List<IAiTextSplitter.SplitChunk> chunks = splitter.split(
                SourceLocation.UNKNOWN, content,
                IAiTextSplitter.SplitOptions.create(DEFAULT_CHUNK_SIZE));
        if (chunks.isEmpty()) {
            throw new NopException(NopAiRagErrors.ERR_AI_RAG_EMPTY_DOCUMENT)
                    .param(ARG_DOC_ID, docId);
        }

        List<VectorData> vectors = new ArrayList<>(chunks.size());
        List<AiDocument> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i).getContent();
            docs.add(AiDocument.fromText(chunkContent));
        }
        List<VectorData> embedded = embeddingModel.embedAll(docs, null);

        for (int i = 0; i < embedded.size(); i++) {
            VectorData vd = embedded.get(i);
            vd.addMetadata("docId", docId);
            vd.addMetadata("chunkIndex", i);
            vd.addMetadata("content", docs.get(i).getContent());
            vd.addMetadata("indexId", indexId);
            vectors.add(vd);
        }

        VectorStoreOptions options = new VectorStoreOptions();
        options.setCollectionName(indexId);
        vectorStore.store(vectors, options);
        return vectors.size();
    }
}
