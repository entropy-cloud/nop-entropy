package io.nop.ai.core.api.embedding;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Embedding model SPI contract (originated per MA5.1 P1-01 / arm-index P1-MA5-003).
 * <p>
 * Since K1 (knowledge-rag roadmap, plan {@code knowledge-rag/01}, 2026-09-28) the platform
 * ships a production implementation: {@code io.nop.ai.core.service.EmbeddingServiceImpl}
 * (OpenAI-compatible {@code /embeddings} client driven by {@code /nop/ai/llm/{provider}.llm.xml},
 * bean {@code nopAiEmbeddingModel}, provider routing via {@link EmbeddingOptions#getProvider()}
 * or the {@code nop.ai.embedding.default-llm} config variable, fail-loud when neither is set).
 * Integrators may still replace it with their own implementation (the earlier
 * "no production implementation by design" adjudication is superseded by K1 on its
 * "first real consumer" trigger condition; see {@code ai-dev/design/nop-ai/embedding.md}).
 * Consumers (e.g. {@code EmbeddingModelBasedClassifier}) receive the model via constructor
 * injection and fail fast at wiring time when no implementation bean is registered.
 */
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