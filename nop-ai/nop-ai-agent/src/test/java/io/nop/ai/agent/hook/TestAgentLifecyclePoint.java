package io.nop.ai.agent.hook;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestAgentLifecyclePoint {

    @Test
    void enumHasExactly12Values() {
        AgentLifecyclePoint[] values = AgentLifecyclePoint.values();
        assertEquals(12, values.length, "AgentLifecyclePoint should have exactly 12 values");
    }

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

    @Test
    void coreLayer1PointsExist() {
        assertNotNull(AgentLifecyclePoint.PRE_REASONING);
        assertNotNull(AgentLifecyclePoint.POST_REASONING);
        assertNotNull(AgentLifecyclePoint.PRE_ACTING);
        assertNotNull(AgentLifecyclePoint.POST_ACTING);
        assertNotNull(AgentLifecyclePoint.ON_ERROR);
    }

    @Test
    void extensionLayer2PointsExist() {
        assertNotNull(AgentLifecyclePoint.PRE_CALL);
        assertNotNull(AgentLifecyclePoint.POST_CALL);
        assertNotNull(AgentLifecyclePoint.REASONING_CHUNK);
        assertNotNull(AgentLifecyclePoint.PRE_COMPACT);
        assertNotNull(AgentLifecyclePoint.POST_COMPACT);
    }

    @Test
    void reentrantPointsExist() {
        assertNotNull(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED);
        assertNotNull(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED);
    }
}
