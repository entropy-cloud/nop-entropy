package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_THOUGHT_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER;

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

    /**
     * Epoch millis of creation. Stored as {@code long} (not {@code Instant}) because
     * JsonTool strict mode ({@code nop.core.json.serialize-only-data-bean}) has no
     * serializer/converter for {@code java.time.Instant} — {@code ThoughtStorage}
     * round-trips this bean through JsonTool (P3-MA1-013 live defect fix).
     */
    private long timestamp;

    // 构造函数
    public ThoughtData() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
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
            throw new NopException(ERR_AI_TOOLS_THOUGHT_EMPTY);
        }
    }

    public int getThoughtNumber() {
        return thoughtNumber;
    }

    public void setThoughtNumber(int thoughtNumber) {
        if (thoughtNumber < 1) {
            throw new NopException(ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER);
        }
        this.thoughtNumber = thoughtNumber;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public void setTotalThoughts(int totalThoughts) {
        if (totalThoughts < 1) {
            throw new NopException(ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS);
        }
        if (totalThoughts < thoughtNumber) {
            throw new NopException(ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER);
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}