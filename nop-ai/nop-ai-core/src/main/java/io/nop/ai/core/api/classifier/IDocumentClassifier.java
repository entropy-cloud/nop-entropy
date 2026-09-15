package io.nop.ai.core.api.classifier;

import io.nop.ai.core.api.document.AiDocument;

/**
 * Classifies an AI document into one or more labels.
 *
 * <p><b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），分类器 SPI 契约预留面（与 {@code IEmbeddingModel}/{@code IVectorStore} 的
 * P1-MA5-003 SPI 裁定同族）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
public interface IDocumentClassifier {
    /**
     * @param document the document to classify
     * @return the classification result with scored labels
     */
    ClassificationResult classifyDocument(AiDocument document);
}
