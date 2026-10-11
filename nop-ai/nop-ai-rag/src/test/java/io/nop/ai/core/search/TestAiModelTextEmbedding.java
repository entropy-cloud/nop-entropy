package io.nop.ai.core.search;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.2（plan nop-code/23）Phase 2 单测：{@link AiModelTextEmbedding} 的委托、
 * double[]→float[] 转换、批量路径与 fail-loud 语义。
 */
class TestAiModelTextEmbedding {

    @Test
    void embedDelegatesAndConvertsToFloat() {
        StubEmbeddingModel model = new StubEmbeddingModel();
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(model);

        float[] vector = embedding.embed("machine learning tutorial");

        assertNotNull(vector);
        assertEquals(StubEmbeddingModel.DIM, vector.length);
        assertEquals(1.0f, vector[0], 1e-6f);
        assertEquals(0.0f, vector[1], 1e-6f);
        assertEquals(1, model.embedCalls.get());
        assertEquals(StubEmbeddingModel.DIM, embedding.getDimension());
    }

    @Test
    void embedAllDelegatesToModelBatchOnce() {
        StubEmbeddingModel model = new StubEmbeddingModel();
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(model);

        List<float[]> vectors = embedding.embedAll(List.of("machine", "deep learning", "other"));

        assertEquals(3, vectors.size());
        assertEquals(1.0f, vectors.get(0)[0], 1e-6f);
        assertEquals(1.0f, vectors.get(1)[1], 1e-6f);
        assertEquals(1.0f, vectors.get(2)[StubEmbeddingModel.DIM - 1], 1e-6f);
        assertEquals(1, model.embedAllCalls.get(), "embedAll must delegate to the model batch API once");
        assertEquals(0, model.embedCalls.get(), "embedAll must not fan out into single embeds");
    }

    @Test
    void asyncPathsDelegate() throws Exception {
        StubEmbeddingModel model = new StubEmbeddingModel();
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(model);

        float[] vector = embedding.embedAsync("deep").toCompletableFuture().get();
        assertEquals(1.0f, vector[1], 1e-6f);

        List<float[]> vectors = embedding.embedAllAsync(List.of("machine"))
                .toCompletableFuture().get();
        assertEquals(1, vectors.size());
        assertEquals(1.0f, vectors.get(0)[0], 1e-6f);
    }

    @Test
    void nullModelResponseFailsLoud() {
        StubEmbeddingModel model = new StubEmbeddingModel();
        model.failWithNull = true;
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(model);

        NopException error = assertThrows(NopException.class, () -> embedding.embed("x"));
        assertEquals("nop.err.ai.core.invalid-argument", error.getErrorCode(),
                "null vector from the model must fail loud, not return a silent null");
    }

    @Test
    void modelFailurePropagates() {
        IEmbeddingModel failing = new IEmbeddingModel() {
            @Override
            public java.util.concurrent.CompletionStage<VectorData> embedAsync(AiDocument doc,
                    io.nop.ai.core.api.embedding.EmbeddingOptions options) {
                return CompletableFuture.failedFuture(new IllegalStateException("provider down"));
            }

            @Override
            public java.util.concurrent.CompletionStage<List<VectorData>> embedAllAsync(
                    List<AiDocument> docs, io.nop.ai.core.api.embedding.EmbeddingOptions options) {
                return CompletableFuture.failedFuture(new IllegalStateException("provider down"));
            }
        };
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(failing);

        assertThrows(RuntimeException.class, () -> embedding.embed("x"),
                "model failures must propagate (fail-loud), not degrade to null/hash paths");
    }

    @Test
    void nullModelRejected() {
        NopException error = assertThrows(NopException.class, () -> new AiModelTextEmbedding(null));
        assertEquals("nop.err.ai.core.invalid-argument", error.getErrorCode());
    }

    @Test
    void nullTextRejected() {
        AiModelTextEmbedding embedding = new AiModelTextEmbedding(new StubEmbeddingModel());
        assertThrows(NopException.class, () -> embedding.embed(null));
        assertThrows(NopException.class, () -> embedding.embedAll(null));
    }
}
