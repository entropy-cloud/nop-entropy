package io.nop.code.service;

import io.nop.api.core.annotations.core.Name;
import io.nop.search.api.ITextEmbedding;

import java.util.ArrayList;
import java.util.List;

/**
 * N4.3 测试嵌入（plan nop-code/24）：确定性词项 hash 向量（dim=16，词项 hash → 位桶累加 +
 * L2 归一化）。同词同向量、索引侧与查询侧同源语义——使 VECTOR 查询的"同名次词"命中含该词
 * 的 doc，证明向量腿真实参与检索。仅测试 classpath（autoconfig aaa- 前缀文件注册）。
 */
public class StubTestTextEmbedding implements ITextEmbedding {

    public static final int DIM = 16;

    @Override
    public float[] embed(@Name("text") String text) {
        return vectorFor(text);
    }

    @Override
    public List<float[]> embedAll(@Name("texts") List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(vectorFor(text));
        }
        return result;
    }

    @Override
    public int getDimension() {
        return DIM;
    }

    static float[] vectorFor(String text) {
        double[] buckets = new double[DIM];
        if (text != null) {
            for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
                if (token.isEmpty()) {
                    continue;
                }
                buckets[Math.floorMod(token.hashCode(), DIM)] += 1;
            }
        }
        float norm = 0;
        for (double v : buckets) {
            norm += (float) (v * v);
        }
        norm = (float) Math.sqrt(norm);
        float[] vector = new float[DIM];
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vector[i] = (float) (buckets[i] / norm);
            }
        }
        return vector;
    }
}
