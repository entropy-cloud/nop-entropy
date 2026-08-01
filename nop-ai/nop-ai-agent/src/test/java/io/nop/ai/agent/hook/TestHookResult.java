package io.nop.ai.agent.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHookResult {

    @Test
    void passResultIsSingleton() {
        HookResult.PassResult a = HookResult.PassResult.instance();
        HookResult.PassResult b = HookResult.PassResult.instance();
        assertSame(a, b);
    }

    @Test
    void passResultIsPass() {
        HookResult result = HookResult.PassResult.instance();
        assertTrue(result.isPass());
        assertEquals(false, result.isVeto());
        assertEquals(false, result.isReenter());
    }

    @Test
    void vetoResultCarriesReason() {
        HookResult.VetoResult result = new HookResult.VetoResult("blocked by policy");
        assertEquals("blocked by policy", result.getReason());
        assertTrue(result.isVeto());
    }

    @Test
    void vetoResultNullReason() {
        HookResult.VetoResult result = new HookResult.VetoResult(null);
        assertEquals(null, result.getReason());
        assertTrue(result.isVeto());
    }

    @Test
    void reenterResultCarriesMessage() {
        HookResult.ReenterResult result = new HookResult.ReenterResult("retry with different args");
        assertEquals("retry with different args", result.getMessage());
        assertTrue(result.isReenter());
    }

    @Test
    void reenterResultNullMessage() {
        HookResult.ReenterResult result = new HookResult.ReenterResult(null);
        assertEquals(null, result.getMessage());
        assertTrue(result.isReenter());
    }

    // ---- W5-3 BailResult (fourth state) ----

    @Test
    void bailResultCarriesReason() {
        HookResult.BailResult result = new HookResult.BailResult("guardrail blocked");
        assertEquals("guardrail blocked", result.getReason());
        assertTrue(result.isBail());
    }

    @Test
    void bailResultNullReason() {
        HookResult.BailResult result = new HookResult.BailResult(null);
        assertEquals(null, result.getReason());
        assertTrue(result.isBail());
    }

    @Test
    void bailResultIsNotPassVetoOrReenter() {
        HookResult result = new HookResult.BailResult("blocked");
        assertTrue(result.isBail());
        assertEquals(false, result.isPass());
        assertEquals(false, result.isVeto());
        assertEquals(false, result.isReenter());
    }

    @Test
    void passVetoAndReenterAreNotBail() {
        assertEquals(false, HookResult.PassResult.instance().isBail());
        assertEquals(false, new HookResult.VetoResult("r").isBail());
        assertEquals(false, new HookResult.ReenterResult("m").isBail());
    }

    @Test
    void concreteTypesExtendHookResult() {
        assertNotNull((HookResult) HookResult.PassResult.instance());
        assertNotNull((HookResult) new HookResult.VetoResult("r"));
        assertNotNull((HookResult) new HookResult.ReenterResult("m"));
        assertNotNull((HookResult) new HookResult.BailResult("b"));
    }
}
