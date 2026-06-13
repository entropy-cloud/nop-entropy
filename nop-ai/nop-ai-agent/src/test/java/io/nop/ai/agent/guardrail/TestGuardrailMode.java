package io.nop.ai.agent.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGuardrailMode {

    @Test
    void hasExactlyThreeValues() {
        GuardrailMode[] values = GuardrailMode.values();
        assertEquals(3, values.length);
    }

    @Test
    void containsAllModes() {
        assertEquals(GuardrailMode.OFF, GuardrailMode.valueOf("OFF"));
        assertEquals(GuardrailMode.REPORT, GuardrailMode.valueOf("REPORT"));
        assertEquals(GuardrailMode.ENFORCE, GuardrailMode.valueOf("ENFORCE"));
    }
}
