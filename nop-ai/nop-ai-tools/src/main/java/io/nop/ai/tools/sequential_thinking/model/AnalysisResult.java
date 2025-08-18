package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import java.util.List;

@DataBean
public class AnalysisResult {
    private final int relatedThoughtsCount;
    private final List<RelatedThoughtSummary> relatedThoughtSummaries;
    private final double progress;
    private final boolean isFirstInStage;

    public AnalysisResult(
            @JsonProperty("relatedThoughtsCount") int relatedThoughtsCount,
            @JsonProperty("relatedThoughtSummaries") List<RelatedThoughtSummary> relatedThoughtSummaries,
            @JsonProperty("progress") double progress,
            @JsonProperty("isFirstInStage") boolean isFirstInStage) {
        this.relatedThoughtsCount = relatedThoughtsCount;
        this.relatedThoughtSummaries = relatedThoughtSummaries;
        this.progress = progress;
        this.isFirstInStage = isFirstInStage;
    }

    // Getters
    public int getRelatedThoughtsCount() {
        return relatedThoughtsCount;
    }

    public List<RelatedThoughtSummary> getRelatedThoughtSummaries() {
        return relatedThoughtSummaries;
    }

    public double getProgress() {
        return progress;
    }

    public boolean isFirstInStage() {
        return isFirstInStage;
    }
}