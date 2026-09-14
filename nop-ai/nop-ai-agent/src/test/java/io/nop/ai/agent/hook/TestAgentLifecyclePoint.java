package io.nop.ai.agent.hook;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAgentLifecyclePoint {

    @Test
    void allExpectedValuesExist() {
        Set<String> expected = Set.of(
                "PRE_CALL", "PRE_REASONING", "POST_REASONING",
                "PRE_ACTING", "POST_ACTING", "ON_ERROR",
                "POST_CALL", "REASONING_CHUNK", "PRE_COMPACT",
                "POST_COMPACT", "BEFORE_TOOL_RESULT_PROCESSED",
                "AFTER_TOOL_RESULT_PROCESSED"
        );

        Set<String> actual = Arrays.stream(AgentLifecyclePoint.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }
}
