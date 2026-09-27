package io.nop.code.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;
import java.util.List;

/**
 * A "surprising" connection discovered by scoring the typed relation graph
 * (graph-discovery-and-export-design.md §3.1).
 */
@DataBean
public class SurprisingConnectionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sourceSymbolId;
    private String targetSymbolId;
    private String sourceLabel;
    private String targetLabel;
    private String sourceFilePath;
    private String targetFilePath;
    private String relation;
    private String confidence;
    private int score;
    private List<String> reasons;

    public String getSourceSymbolId() { return sourceSymbolId; }
    public void setSourceSymbolId(String sourceSymbolId) { this.sourceSymbolId = sourceSymbolId; }
    public String getTargetSymbolId() { return targetSymbolId; }
    public void setTargetSymbolId(String targetSymbolId) { this.targetSymbolId = targetSymbolId; }
    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }
    public String getSourceFilePath() { return sourceFilePath; }
    public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }
    public String getTargetFilePath() { return targetFilePath; }
    public void setTargetFilePath(String targetFilePath) { this.targetFilePath = targetFilePath; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
}
