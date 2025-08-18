package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AnalysisContext {
    private final int thoughtHistoryLength;
    private final ThoughtStage currentStage;

    public AnalysisContext(
            @JsonProperty("thoughtHistoryLength") int thoughtHistoryLength,
            @JsonProperty("currentStage") ThoughtStage currentStage) {
        this.thoughtHistoryLength = thoughtHistoryLength;
        this.currentStage = currentStage;
    }

    public int getThoughtHistoryLength() {
        return thoughtHistoryLength;
    }

    public ThoughtStage getCurrentStage() {
        return currentStage;
    }
}