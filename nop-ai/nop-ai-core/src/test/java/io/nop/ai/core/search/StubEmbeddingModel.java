package io.nop.ai.core.search;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * N4.2 测试桩：确定性关键词向量（dim=8，"machine"→e0，"deep"→e1，其余→e7），
 * 供单测/wiring/e2e 三处复用。IoC 装配场景经 no-arg 构造 + getBean 后取用。
 */
public class StubEmbeddingModel implements IEmbeddingModel {
    public static final int DIM = 8;

    public final AtomicInteger embedCalls = new AtomicInteger();
    public final AtomicInteger embedAllCalls = new AtomicInteger();
    public boolean failWithNull = false;

    @Override
    public CompletionStage<VectorData> embedAsync(AiDocument doc, EmbeddingOptions options) {
        embedCalls.incrementAndGet();
        if (failWithNull) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(vectorFor(doc == null ? null : doc.getContent()));
    }

    @Override
    public CompletionStage<List<VectorData>> embedAllAsync(List<AiDocument> docs, EmbeddingOptions options) {
        embedAllCalls.incrementAndGet();
        List<VectorData> result = new java.util.ArrayList<>(docs.size());
        for (AiDocument doc : docs) {
            result.add(failWithNull ? null : vectorFor(doc == null ? null : doc.getContent()));
        }
        return CompletableFuture.completedFuture(result);
    }

    public static VectorData vectorFor(String text) {
        String t = text == null ? "" : text.toLowerCase();
        double[] v = new double[DIM];
        if (t.contains("machine")) {
            v[0] = 1;
        } else if (t.contains("deep")) {
            v[1] = 1;
        } else {
            v[DIM - 1] = 1;
        }
        VectorData vd = new VectorData();
        vd.setVector(v);
        return vd;
    }
}
