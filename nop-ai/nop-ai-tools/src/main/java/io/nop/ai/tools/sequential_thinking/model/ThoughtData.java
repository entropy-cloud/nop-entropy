package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@DataBean
public class ThoughtData {
    private String id;
    private String thought;
    private int thoughtNumber;
    private int totalThoughts;
    private boolean nextThoughtNeeded;
    private ThoughtStage stage;
    private List<String> tags;
    private List<String> axiomsUsed;
    private List<String> assumptionsChallenged;
    private Instant timestamp;

    // 构造函数
    public ThoughtData() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getThought() {
        return thought;
    }

    public void setThought(String thought) {
        this.thought = Objects.requireNonNull(thought, "Thought cannot be null");
        if (thought.trim().isEmpty()) {
            throw new IllegalArgumentException("Thought cannot be empty");
        }
    }

    public int getThoughtNumber() {
        return thoughtNumber;
    }

    public void setThoughtNumber(int thoughtNumber) {
        if (thoughtNumber < 1) {
            throw new IllegalArgumentException("Thought number must be positive");
        }
        this.thoughtNumber = thoughtNumber;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public void setTotalThoughts(int totalThoughts) {
        if (totalThoughts < 1) {
            throw new IllegalArgumentException("Total thoughts must be positive");
        }
        if (totalThoughts < thoughtNumber) {
            throw new IllegalArgumentException("Total thoughts must be >= thought number");
        }
        this.totalThoughts = totalThoughts;
    }

    public boolean isNextThoughtNeeded() {
        return nextThoughtNeeded;
    }

    public void setNextThoughtNeeded(boolean nextThoughtNeeded) {
        this.nextThoughtNeeded = nextThoughtNeeded;
    }

    public ThoughtStage getStage() {
        return stage;
    }

    public void setStage(ThoughtStage stage) {
        this.stage = Objects.requireNonNull(stage, "Stage cannot be null");
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : List.of();
    }

    public List<String> getAxiomsUsed() {
        return axiomsUsed;
    }

    public void setAxiomsUsed(List<String> axiomsUsed) {
        this.axiomsUsed = axiomsUsed != null ? axiomsUsed : List.of();
    }

    public List<String> getAssumptionsChallenged() {
        return assumptionsChallenged;
    }

    public void setAssumptionsChallenged(List<String> assumptionsChallenged) {
        this.assumptionsChallenged = assumptionsChallenged != null ? assumptionsChallenged : List.of();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }
}