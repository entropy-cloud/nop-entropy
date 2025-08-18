package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public enum ThoughtStage {
    PROBLEM_DEFINITION("Problem Definition"),
    RESEARCH("Research"),
    ANALYSIS("Analysis"),
    SYNTHESIS("Synthesis"),
    CONCLUSION("Conclusion");

    private final String value;

    ThoughtStage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ThoughtStage fromString(String value) {
        for (ThoughtStage stage : values()) {
            if (stage.value.equalsIgnoreCase(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Invalid ThoughtStage: " + value);
    }
}