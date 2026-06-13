package io.nop.ai.agent.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGuardrailDirection {

    @Test
    void hasExactlyTwoValues() {
        GuardrailDirection[] values = GuardrailDirection.values();
        assertEquals(2, values.length);
    }

    @Test
    void containsInputAndOutput() {
        assertEquals(GuardrailDirection.INPUT, GuardrailDirection.valueOf("INPUT"));
        assertEquals(GuardrailDirection.OUTPUT, GuardrailDirection.valueOf("OUTPUT"));
    }
}
