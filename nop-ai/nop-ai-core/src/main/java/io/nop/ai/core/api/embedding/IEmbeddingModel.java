package io.nop.ai.core.api.embedding;

import io.nop.ai.core.api.support.VectorData;

import javax.swing.text.Document;
import java.util.concurrent.CompletionStage;

public interface IEmbeddingModel {
    CompletionStage<VectorData> embedAsync(Document doc, EmbeddingOptions options);
}