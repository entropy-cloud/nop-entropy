package io.nop.ai.core.api.embedding;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.support.VectorData;

import java.util.concurrent.CompletionStage;

public interface IEmbeddingModel {
    CompletionStage<VectorData> embedAsync(AiDocument doc, EmbeddingOptions options);
}