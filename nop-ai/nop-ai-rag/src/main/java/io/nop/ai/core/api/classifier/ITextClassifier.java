package io.nop.ai.core.api.classifier;

/**
 * Classifies plain text into one or more labels.
 *
 * <p><b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），分类器 SPI 契约预留面（与 {@code IEmbeddingModel}/{@code IVectorStore} 的
 * P1-MA5-003 SPI 裁定同族）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
public interface ITextClassifier {
    /**
     * @param text the text to classify
     * @return the classification result with scored labels
     */
    ClassificationResult classifyText(String text);
}
