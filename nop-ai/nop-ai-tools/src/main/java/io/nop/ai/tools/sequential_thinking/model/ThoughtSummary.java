package io.nop.ai.tools.sequential_thinking.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

@DataBean
public class ThoughtSummary {
    private final String message;
    private final int totalThoughts;
    private final Map<ThoughtStage, Integer> stages;
    private final List<TimelineEntry> timeline;
    private final List<TagCount> topTags;
    private final boolean hasAllStages;
    private final double percentComplete;

    public ThoughtSummary(
            @JsonProperty("message") String message,
            @JsonProperty("totalThoughts") int totalThoughts,
            @JsonProperty("stages") Map<ThoughtStage, Integer> stages,
            @JsonProperty("timeline") List<TimelineEntry> timeline,
            @JsonProperty("topTags") List<TagCount> topTags,
            @JsonProperty("hasAllStages") boolean hasAllStages,
            @JsonProperty("percentComplete") double percentComplete) {
        this.message = message;
        this.totalThoughts = totalThoughts;
        this.stages = stages;
        this.timeline = timeline;
        this.topTags = topTags;
        this.hasAllStages = hasAllStages;
        this.percentComplete = percentComplete;
    }

    public static ThoughtSummary fromMessage(String message) {
        return new ThoughtSummary(message, 0, null, null, null, false, 0);
    }

    // Getters
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getMessage() {
        return message;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<ThoughtStage, Integer> getStages() {
        return stages;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<TimelineEntry> getTimeline() {
        return timeline;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<TagCount> getTopTags() {
        return topTags;
    }

    public boolean isHasAllStages() {
        return hasAllStages;
    }

    public double getPercentComplete() {
        return percentComplete;
    }
}