package io.nop.ai.core.api.classifier;

import io.nop.ai.core.api.document.AiDocument;

public interface IDocumentClassifier {
    ClassificationResult classifyDocument(AiDocument document);
}
