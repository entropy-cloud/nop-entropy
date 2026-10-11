package io.nop.ai.core.search;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.exceptions.NopException;
import io.nop.search.api.ITextEmbedding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;

/**
 * N4.2（plan nop-code/23）：{@link ITextEmbedding}（nop-search SPI）的平台生产实现，
 * 委托 K1 的 {@link IEmbeddingModel}（生产实现 {@code EmbeddingServiceImpl}，bean
 * {@code nopAiEmbeddingModel}）。方向裁定：nop-search 必须保持与 nop-ai 无关（SPI
 * javadoc 契约），故桥接落 nop-ai-core（新增轻量 nop-search-api 依赖）。
 *
 * <p>失败语义 = 异常上抛（fail-loud）：不使用 SPI 允许的 null 返回——静默 null 会让
 * 索引侧悄然退化为文本-only、查询侧落入 hash 模拟兜底，违反 No Silent No-Op。
 */
public class AiModelTextEmbedding implements ITextEmbedding {
    private final IEmbeddingModel embeddingModel;
    private volatile int cachedDimension = -1;

    public AiModelTextEmbedding(IEmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "embeddingModel must not be null");
        }
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (text == null) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "embedding text must not be null");
        }
        VectorData vd = embeddingModel.embed(new AiDocument(text), null);
        return toFloatArray(vd);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "embedding texts must not be null");
        }
        List<AiDocument> docs = new ArrayList<>(texts.size());
        for (String text : texts) {
            if (text == null) {
                throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                        .param(ARG_DETAIL, "embedding text must not be null");
            }
            docs.add(new AiDocument(text));
        }
        List<VectorData> vectors = embeddingModel.embedAll(docs, null);
        List<float[]> result = new ArrayList<>(vectors.size());
        for (VectorData vd : vectors) {
            result.add(toFloatArray(vd));
        }
        return result;
    }

    @Override
    public CompletionStage<float[]> embedAsync(String text) {
        return embeddingModel.embedAsync(new AiDocument(text), null)
                .thenApply(this::toFloatArray);
    }

    @Override
    public CompletionStage<List<float[]>> embedAllAsync(List<String> texts) {
        List<AiDocument> docs = new ArrayList<>(texts.size());
        for (String text : texts) {
            docs.add(new AiDocument(text));
        }
        return embeddingModel.embedAllAsync(docs, null)
                .thenApply(vectors -> {
                    List<float[]> result = new ArrayList<>(vectors.size());
                    for (VectorData vd : vectors) {
                        result.add(toFloatArray(vd));
                    }
                    return result;
                });
    }

    /**
     * 首次成功嵌入后返回该模型的真实维度；此前 -1（接口默认，表示未知）。
     */
    @Override
    public int getDimension() {
        return cachedDimension;
    }

    private float[] toFloatArray(VectorData vd) {
        if (vd == null || vd.getVector() == null || vd.getVector().length == 0) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "embedding model returned no vector data");
        }
        double[] vector = vd.getVector();
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        cachedDimension = result.length;
        return result;
    }
}
