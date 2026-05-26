package io.nop.code.core.semantic;

import io.nop.api.core.annotations.data.DataBean;

/**
 * A semantic edge between two code symbols.
 * Represents relationships discovered through semantic analysis (not just AST extraction).
 */
@DataBean
public class CodeSemanticEdge {
    private String id;
    private String indexId;
    private String sourceSymbolId;
    private String targetSymbolId;
    private boolean directed;
    private SemanticRelationType relationType;
    private EdgeConfidence confidence;
    private double confidenceScore;
    private String rationale;
    private String extractorId;
    private String extData;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIndexId() { return indexId; }
    public void setIndexId(String indexId) { this.indexId = indexId; }

    public String getSourceSymbolId() { return sourceSymbolId; }
    public void setSourceSymbolId(String sourceSymbolId) { this.sourceSymbolId = sourceSymbolId; }

    public String getTargetSymbolId() { return targetSymbolId; }
    public void setTargetSymbolId(String targetSymbolId) { this.targetSymbolId = targetSymbolId; }

    public boolean isDirected() { return directed; }
    public void setDirected(boolean directed) { this.directed = directed; }

    public SemanticRelationType getRelationType() { return relationType; }
    public void setRelationType(SemanticRelationType relationType) { this.relationType = relationType; }

    public EdgeConfidence getConfidence() { return confidence; }
    public void setConfidence(EdgeConfidence confidence) { this.confidence = confidence; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getExtractorId() { return extractorId; }
    public void setExtractorId(String extractorId) { this.extractorId = extractorId; }

    public String getExtData() { return extData; }
    public void setExtData(String extData) { this.extData = extData; }
}
