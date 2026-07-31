package io.nop.ai.core.api.embedding;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Embedding model SPI extension contract (adjudicated per MA5.1 P1-01 / arm-index P1-MA5-003).
 * <p>
 * This is an SPI extension point: the platform ships no production implementation by
 * design — integrators provide concrete implementations (e.g. an adapter calling a real
 * LLM embedding endpoint, see {@code nop-ai-agent} {@code InMemoryEmbeddingAdapter} for a
 * deterministic test-only adapter at the agent layer). Consumers (e.g.
 * {@code EmbeddingModelBasedClassifier}) receive the model via constructor injection and
 * fail fast at wiring time when no implementation bean is registered.
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