package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import java.time.Instant;
import java.util.List;

@DataBean
public class CurrentThoughtInfo {
    private final int thoughtNumber;
    private final int totalThoughts;
    private final boolean nextThoughtNeeded;
    private final ThoughtStage stage;
    private final List<String> tags;
    private final Instant timestamp;

    public CurrentThoughtInfo(
            @JsonProperty("thoughtNumber") int thoughtNumber,
            @JsonProperty("totalThoughts") int totalThoughts,
            @JsonProperty("nextThoughtNeeded") boolean nextThoughtNeeded,
            @JsonProperty("stage") ThoughtStage stage,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("timestamp") Instant timestamp) {
        this.thoughtNumber = thoughtNumber;
        this.totalThoughts = totalThoughts;
        this.nextThoughtNeeded = nextThoughtNeeded;
        this.stage = stage;
        this.tags = tags != null ? tags : List.of();
        this.timestamp = timestamp;
    }

    // Getters
    public int getThoughtNumber() {
        return thoughtNumber;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public boolean isNextThoughtNeeded() {
        return nextThoughtNeeded;
    }

    public ThoughtStage getStage() {
        return stage;
    }

    public List<String> getTags() {
        return tags;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}