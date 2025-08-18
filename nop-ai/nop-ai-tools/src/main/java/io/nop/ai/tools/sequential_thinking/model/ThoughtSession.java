package io.nop.ai.tools.sequential_thinking.model;

import java.time.Instant;
import java.util.List;

public class ThoughtSession {
    private List<ThoughtData> thoughts;
    private Instant lastUpdated;

    // 用于JSON反序列化
    public ThoughtSession() {
    }

    public ThoughtSession(List<ThoughtData> thoughts) {
        this.thoughts = thoughts;
        this.lastUpdated = Instant.now();
    }

    public List<ThoughtData> getThoughts() {
        return thoughts;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}