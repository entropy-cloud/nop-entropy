package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test (Minimum Rules #22, Anti-Hollow): loads the shipped
 * enterprise-compliance rule-set from YAML, drives the full pipeline
 * (load → resolve → evaluate → aggregate → single GuardrailResult), and
 * asserts the resolved active set is what structural convergence predicts
 * (not just that a result object exists).
 */
public class TestRuleGraphEndToEnd {

    private static GuardrailRuleSet enterpriseSet;
    private static final AgentExecutionContext CTX =
            AgentExecutionContext.create(new io.nop.ai.agent.model.AgentModel(), "e2e");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IResource res = VirtualFileSystem.instance()
                .getResource("/nop/ai/agent/guardrail-rules/enterprise-compliance.yaml");
        enterpriseSet = new RuleSetLoader().load(res);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void shipsComplianceSampleAndLoads() {
        assertEquals("enterprise-compliance", enterpriseSet.getId());
        assertTrue(enterpriseSet.contains("fin-transaction"));
        assertTrue(enterpriseSet.contains("audit-trail"));
        assertTrue(enterpriseSet.contains("general-chat"));
        assertTrue(enterpriseSet.contains("redact-secret-token"));
    }

    @Test
    void finTransactionResolvesActiveSetWithDependsOnAndExcludes() {
        RuleGraphResolver resolver = new RuleGraphResolver(enterpriseSet);
        // seed: only fin-transaction matches "transfer funds"
        Set<String> active = resolver.resolve(java.util.Collections.singleton("fin-transaction"));

        assertTrue(active.contains("fin-transaction"), "seed rule is in active set");
        assertTrue(active.contains("audit-trail"),
                "dependsOn pulled audit-trail in (structural convergence)");
        assertFalse(active.contains("general-chat"),
                "general-chat not pulled in (no relationship)");
    }

    @Test
    void endToEndBlockWhenFinTransactionMatchedAndAuditExtended() {
        RuleGraphGuardrail guardrail = new RuleGraphGuardrail(enterpriseSet);

        // "transfer funds audit" — matches fin-transaction AND audit-trail (pulled via dependsOn)
        GuardrailResult result = guardrail.check(GuardrailDirection.INPUT,
                "Please transfer funds to the audit ledger", CTX);
        assertTrue(result.isBlock());
        String reason = ((GuardrailResult.BlockResult) result).getReason();
        assertTrue(reason.contains("FIN_COMPLIANCE"));
        assertTrue(reason.contains("AUDIT"),
                "audit-trail was pulled in via dependsOn AND matched -> contributes to block");
    }

    @Test
    void endToEndExcludesNarrowsWhenBothFinAndChatMatch() {
        RuleGraphGuardrail guardrail = new RuleGraphGuardrail(enterpriseSet);

        // content matches BOTH fin-transaction ("transfer") and general-chat
        // ("transfer ... ") — fin excludes chat, so only fin contributes.
        // Construct content that matches fin's pattern; general-chat's pattern
        // is greetings so it won't match here, but the excludes relationship is
        // still proven by resolving the active set when both are seeded:
        RuleGraphResolver resolver = new RuleGraphResolver(enterpriseSet);
        Set<String> active = resolver.resolve(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList("fin-transaction", "general-chat")));
        assertTrue(active.contains("fin-transaction"));
        assertFalse(active.contains("general-chat"),
                "fin-transaction excludes general-chat -> structural narrowing removes it");

        // and the guardrail produces a clean single block from fin only
        GuardrailResult result = guardrail.check(GuardrailDirection.INPUT,
                "transfer funds", CTX);
        assertTrue(result.isBlock());
    }

    @Test
    void endToEndModifyRedactsSecretTokenInOutput() {
        RuleGraphGuardrail guardrail = new RuleGraphGuardrail(enterpriseSet);
        GuardrailResult result = guardrail.check(GuardrailDirection.OUTPUT,
                "the value is secret-token-deadbeef here", CTX);
        assertTrue(result.isModify());
        assertEquals("the value is [REDACTED] here",
                ((GuardrailResult.ModifyResult) result).getContent());
    }

    @Test
    void endToEndBenignInputPasses() {
        RuleGraphGuardrail guardrail = new RuleGraphGuardrail(enterpriseSet);
        assertTrue(guardrail.check(GuardrailDirection.INPUT,
                "what is the weather today", CTX).isBlock(),
                "general-chat pattern matches 'weather' -> block (general-chat is a real rule)");
        // content that matches NO rule -> pass
        assertTrue(guardrail.check(GuardrailDirection.INPUT,
                "show me the quarterly revenue chart", CTX).isPass());
    }

    @Test
    void resolverOutputIsDeterministicAcrossCalls() {
        RuleGraphResolver resolver = new RuleGraphResolver(enterpriseSet);
        Set<String> a = resolver.resolve(java.util.Collections.singleton("fin-transaction"));
        Set<String> b = resolver.resolve(java.util.Collections.singleton("fin-transaction"));
        assertEquals(a, b, "same seed must resolve to the same active set (deterministic)");
    }
}
