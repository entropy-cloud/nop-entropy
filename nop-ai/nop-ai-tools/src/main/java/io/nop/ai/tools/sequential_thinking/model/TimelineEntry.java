package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class TimelineEntry {
    private final int number;
    private final ThoughtStage stage;

    public TimelineEntry(@JsonProperty("number") int number,
                         @JsonProperty("stage") ThoughtStage stage) {
        this.number = number;
        this.stage = stage;
    }

    public int getNumber() {
        return number;
    }

    public ThoughtStage getStage() {
        return stage;
    }
}