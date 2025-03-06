package io.nop.ai.core.api.embedding;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IEmbeddingModel {
    CompletionStage<VectorData> embedAsync(AiDocument doc, EmbeddingOptions options);

    CompletionStage<List<VectorData>> embedAllAsync(List<AiDocument> docs, EmbeddingOptions options);

    default VectorData embed(AiDocument doc, EmbeddingOptions options) {
        return FutureHelper.syncGet(embedAsync(doc, options));
    }

    default List<VectorData> embedAll(List<AiDocument> docs, EmbeddingOptions options) {
        return FutureHelper.syncGet(embedAllAsync(docs, options));
    }
}