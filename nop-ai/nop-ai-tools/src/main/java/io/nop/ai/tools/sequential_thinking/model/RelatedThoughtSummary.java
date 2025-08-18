package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class RelatedThoughtSummary {
    private final int thoughtNumber;
    private final ThoughtStage stage;
    private final String snippet;

    public RelatedThoughtSummary(
            @JsonProperty("thoughtNumber") int thoughtNumber,
            @JsonProperty("stage") ThoughtStage stage,
            @JsonProperty("snippet") String snippet) {
        this.thoughtNumber = thoughtNumber;
        this.stage = stage;
        this.snippet = snippet;
    }

    public int getThoughtNumber() {
        return thoughtNumber;
    }

    public ThoughtStage getStage() {
        return stage;
    }

    public String getSnippet() {
        return snippet;
    }
}