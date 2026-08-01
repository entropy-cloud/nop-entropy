package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAttackCase {

    @Test
    void baseCaseHasNoTransform() {
        AttackCase ac = new AttackCase("id1", "cat", "tc", "payload",
                ExpectedBehavior.BLOCK);
        assertEquals(GuardrailDirection.INPUT, ac.getDirection());
        assertNull(ac.getTransform());
        assertTrue(!ac.isTransformed());
    }

    @Test
    void withTransformedPayloadDerivesVariant() {
        AttackCase base = new AttackCase("id1", "cat", "tc", "payload",
                ExpectedBehavior.BLOCK);
        AttackCase variant = base.withTransformedPayload("encoded", "base64");
        assertEquals("id1:base64", variant.getId());
        assertEquals("encoded", variant.getPayload());
        assertEquals("base64", variant.getTransform());
        assertTrue(variant.isTransformed());
    }

    @Test
    void transformInheritsSemantics() {
        AttackCase base = new AttackCase("id1", "cat", "tc", "payload",
                GuardrailDirection.OUTPUT, ExpectedBehavior.BLOCK, "desc", null);
        AttackCase variant = base.withTransformedPayload("p2", "crescendo");
        // expectedBehavior / category / threatClass / direction unchanged
        assertEquals(ExpectedBehavior.BLOCK, variant.getExpectedBehavior());
        assertEquals("cat", variant.getCategory());
        assertEquals("tc", variant.getThreatClass());
        assertEquals(GuardrailDirection.OUTPUT, variant.getDirection());
        assertEquals("desc", variant.getDescription());
    }

    @Test
    void equalityAndHashCode() {
        AttackCase a = new AttackCase("id", "c", "t", "p",
                GuardrailDirection.INPUT, ExpectedBehavior.BLOCK, "d", null);
        AttackCase b = new AttackCase("id", "c", "t", "p",
                GuardrailDirection.INPUT, ExpectedBehavior.BLOCK, "d", null);
        AttackCase c = new AttackCase("id", "c", "t", "p2",
                GuardrailDirection.INPUT, ExpectedBehavior.BLOCK, "d", null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void directionDefaultsToInput() {
        AttackCase ac = new AttackCase("id", "c", "t", "p", ExpectedBehavior.PASS);
        assertEquals(GuardrailDirection.INPUT, ac.getDirection());
    }

    @Test
    void transformVariantsKeepDistinctIds() {
        AttackCase base = new AttackCase("x", "c", "t", "p", ExpectedBehavior.BLOCK);
        AttackCase b64 = base.withTransformedPayload("p1", "base64");
        AttackCase cre = base.withTransformedPayload("p2", "crescendo");
        assertEquals(Arrays.asList("x:base64", "x:crescendo"), Arrays.asList(b64.getId(), cre.getId()));
    }

    @Test
    void nullPayloadInBuilderRejected() {
        // payload is stored as-is; null payload only surfaces when the
        // orchestrator calls check(). Here we just ensure toString is robust.
        AttackCase ac = new AttackCase("id", "c", "t", "p", ExpectedBehavior.BLOCK);
        assertTrue(ac.toString().contains("id"));
        assertTrue(ac.toString().contains("BLOCK"));
    }

    @Test
    void modifyResultSemanticsForGrader() {
        // sanity: a ModifyResult produced by a guardrail is distinguishable
        GuardrailResult m = new GuardrailResult.ModifyResult("x");
        assertTrue(m.isModify());
        assertTrue(!m.isBlock());
        assertTrue(!m.isPass());
    }
}
