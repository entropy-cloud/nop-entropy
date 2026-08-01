package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;

import static io.nop.ai.core.NopAiCoreErrors.ARG_VALUE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_STAGE;

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
        throw new NopException(ERR_AI_TOOLS_INVALID_STAGE)
                .param(ARG_VALUE, value);
    }
}