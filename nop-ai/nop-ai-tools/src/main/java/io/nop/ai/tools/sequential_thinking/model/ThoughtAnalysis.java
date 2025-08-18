package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ThoughtAnalysis {
    private final CurrentThoughtInfo currentThought;
    private final AnalysisResult analysis;
    private final AnalysisContext context;

    public ThoughtAnalysis(
            @JsonProperty("currentThought") CurrentThoughtInfo currentThought,
            @JsonProperty("analysis") AnalysisResult analysis,
            @JsonProperty("context") AnalysisContext context) {
        this.currentThought = currentThought;
        this.analysis = analysis;
        this.context = context;
    }

    public CurrentThoughtInfo getCurrentThought() {
        return currentThought;
    }

    public AnalysisResult getAnalysis() {
        return analysis;
    }

    public AnalysisContext getContext() {
        return context;
    }
}