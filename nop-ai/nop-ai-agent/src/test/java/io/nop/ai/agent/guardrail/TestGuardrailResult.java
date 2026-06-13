package io.nop.ai.agent.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGuardrailResult {

    @Test
    void passResultIsSingleton() {
        GuardrailResult.PassResult a = GuardrailResult.PassResult.instance();
        GuardrailResult.PassResult b = GuardrailResult.PassResult.instance();
        assertSame(a, b);
    }

    @Test
    void passResultReportsCorrectType() {
        GuardrailResult result = GuardrailResult.PassResult.instance();
        assertTrue(result.isPass());
        assertFalse(result.isBlock());
        assertFalse(result.isModify());
    }

    @Test
    void blockResultCarriesReason() {
        GuardrailResult.BlockResult result = new GuardrailResult.BlockResult("unsafe content");
        assertEquals("unsafe content", result.getReason());
        assertTrue(result.isBlock());
        assertFalse(result.isPass());
        assertFalse(result.isModify());
    }

    @Test
    void blockResultNullReason() {
        GuardrailResult.BlockResult result = new GuardrailResult.BlockResult(null);
        assertEquals(null, result.getReason());
        assertTrue(result.isBlock());
    }

    @Test
    void blockResultEquality() {
        GuardrailResult.BlockResult a = new GuardrailResult.BlockResult("r");
        GuardrailResult.BlockResult b = new GuardrailResult.BlockResult("r");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void modifyResultCarriesContent() {
        GuardrailResult.ModifyResult result = new GuardrailResult.ModifyResult("sanitized content");
        assertEquals("sanitized content", result.getContent());
        assertTrue(result.isModify());
        assertFalse(result.isPass());
        assertFalse(result.isBlock());
    }

    @Test
    void modifyResultNullContent() {
        GuardrailResult.ModifyResult result = new GuardrailResult.ModifyResult(null);
        assertEquals(null, result.getContent());
        assertTrue(result.isModify());
    }

    @Test
    void modifyResultEquality() {
        GuardrailResult.ModifyResult a = new GuardrailResult.ModifyResult("c");
        GuardrailResult.ModifyResult b = new GuardrailResult.ModifyResult("c");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
