package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * Session wrapper serialized by {@code ThoughtStorage} (JSON file persistence).
 * Marked {@link DataBean} and using epoch-millis {@code long} timestamps so
 * JsonTool strict mode round-trips it (live defect fixed by P3-MA1-013 verification:
 * serialization previously always failed on the missing annotation and on
 * {@code java.time.Instant}, which has no JsonTool serializer/converter).
 */
@DataBean
public class ThoughtSession {
    private List<ThoughtData> thoughts;
    private long lastUpdated;

    // 用于JSON反序列化
    public ThoughtSession() {
    }

    public ThoughtSession(List<ThoughtData> thoughts) {
        this.thoughts = thoughts;
        this.lastUpdated = System.currentTimeMillis();
    }

    public List<ThoughtData> getThoughts() {
        return thoughts;
    }

    public void setThoughts(List<ThoughtData> thoughts) {
        this.thoughts = thoughts;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}