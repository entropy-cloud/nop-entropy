package io.nop.ai.core.api.embedding;

/**
 * Utility class for converting between cosine similarity and relevance score.
 *
 * <p><b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 仅
 * 测试 {@code TestCosineSimilarityAndRelevanceScore} 与 reserved 族内
 * {@code EmbeddingModelBasedClassifier} 引用），embedding SPI 契约族 utility。保留为公共 API
 * 预留；删除需单独 plan + 迁移评估。
 */
public class RelevanceScore {
    private RelevanceScore() {}

    /**
     * Converts cosine similarity into relevance score.
     *
     * @param cosineSimilarity Cosine similarity in the range [-1..1] where -1 is not relevant and 1 is relevant.
     * @return Relevance score in the range [0..1] where 0 is not relevant and 1 is relevant.
     */
    public static double fromCosineSimilarity(double cosineSimilarity) {
        return (cosineSimilarity + 1) / 2;
    }
}
