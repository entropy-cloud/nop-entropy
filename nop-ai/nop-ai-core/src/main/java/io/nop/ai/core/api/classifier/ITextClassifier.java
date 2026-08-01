package io.nop.ai.core.api.classifier;

/**
 * Classifies plain text into one or more labels.
 */
public interface ITextClassifier {
    /**
     * @param text the text to classify
     * @return the classification result with scored labels
     */
    ClassificationResult classifyText(String text);
}
