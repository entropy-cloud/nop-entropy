package io.nop.ai.core.api.classifier;

import io.nop.ai.core.api.document.AiDocument;

/**
 * Classifies an AI document into one or more labels.
 */
public interface IDocumentClassifier {
    /**
     * @param document the document to classify
     * @return the classification result with scored labels
     */
    ClassificationResult classifyDocument(AiDocument document);
}
