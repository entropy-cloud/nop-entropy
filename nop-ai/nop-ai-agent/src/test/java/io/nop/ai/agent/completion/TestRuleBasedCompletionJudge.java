package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRuleBasedCompletionJudge {

    private static AgentExecutionContext ctxWith(int currentIteration, int maxIterations) {
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s1");
        ctx.setMaxIterations(maxIterations);
        ctx.setCurrentIteration(currentIteration);
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    private static ChatAssistantMessage msgWith(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    // (a) null content -> Continue
    @Test
    void nullContentReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(null);

        CompletionDecision result = judge.decide(msg, ctxWith(0, 10));

        assertTrue(result.isContinue());
        assertNotNull(((CompletionDecision.Continue) result).getMessage());
    }

    @Test
    void nullAssistantMessageReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(null, ctxWith(0, 10));

        assertTrue(result.isContinue());
        assertNotNull(((CompletionDecision.Continue) result).getMessage());
    }

    // (b) empty string content -> Continue
    @Test
    void emptyContentReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(msgWith(""), ctxWith(0, 10));

        assertTrue(result.isContinue());
    }

    // (c) whitespace-only content -> Continue
    @Test
    void whitespaceOnlyContentReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(msgWith("   \t\n  "), ctxWith(0, 10));

        assertTrue(result.isContinue());
    }

    // (d) content shorter than minResponseLength -> Continue
    @Test
    void contentShorterThanMinReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(msgWith("hi"), ctxWith(0, 10));

        assertTrue(result.isContinue(), "Content shorter than default minResponseLength=10 must Continue");
    }

    // (e) content at exactly minResponseLength boundary -> Complete
    @Test
    void contentAtMinBoundaryReturnsComplete() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();
        String exact = "0123456789";

        assertEquals(10, judge.getConfig().getMinResponseLength());
        assertEquals(10, exact.trim().length());

        CompletionDecision result = judge.decide(msgWith(exact), ctxWith(0, 10));

        assertTrue(result.isComplete(), "Content length == minResponseLength must Complete (boundary inclusive)");
    }

    // (f) substantive content, iterations well below budget -> Complete
    @Test
    void substantiveContentBelowBudgetReturnsComplete() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(
                msgWith("The task is complete. Here is the final answer."),
                ctxWith(1, 10));

        assertTrue(result.isComplete());
    }

    // (g) substantive content, currentIteration >= maxIterations * escalationRatio -> Escalate
    @Test
    void substantiveContentNearBudgetReturnsEscalateWithReason() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(
                msgWith("The task is complete. Here is the final answer."),
                ctxWith(9, 10));

        assertTrue(result.isEscalate(), "currentIteration 9 >= maxIterations 10 * ratio 0.9 must Escalate");
        String reason = ((CompletionDecision.Escalate) result).getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("9"), "Escalate reason should reference currentIteration");
        assertTrue(reason.contains("10"), "Escalate reason should reference maxIterations");
        assertTrue(reason.contains("0.9"), "Escalate reason should reference escalationRatio");
    }

    // (h) escalation ratio boundary: just below threshold -> Complete, at threshold -> Escalate
    @Test
    void escalationRatioBoundary() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();
        String substantive = "Substantive answer text here.";

        // maxIterations=10, ratio=0.9 -> threshold=9.0
        // iteration 8 < 9.0 -> Complete
        CompletionDecision belowThreshold = judge.decide(msgWith(substantive), ctxWith(8, 10));
        assertTrue(belowThreshold.isComplete(), "iteration 8 < threshold 9.0 must Complete");

        // iteration 9 >= 9.0 -> Escalate
        CompletionDecision atThreshold = judge.decide(msgWith(substantive), ctxWith(9, 10));
        assertTrue(atThreshold.isEscalate(), "iteration 9 >= threshold 9.0 must Escalate");
    }

    @Test
    void escalationRatioExactlyZeroTriggersEscalateAlwaysWhenContentSubstantive() {
        CompletionRuleConfig zeroRatio = new CompletionRuleConfig(10, 0.0, null, null);
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge(zeroRatio);

        CompletionDecision result = judge.decide(
                msgWith("Substantive answer text here."),
                ctxWith(0, 10));

        assertTrue(result.isEscalate(), "escalationRatio=0.0 means currentIteration 0 >= 0 must Escalate");
    }

    @Test
    void escalationRatioOneRequiresLastIteration() {
        CompletionRuleConfig fullRatio = new CompletionRuleConfig(10, 1.0, null, null);
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge(fullRatio);
        String substantive = "Substantive answer text here.";

        // maxIterations=10, ratio=1.0 -> threshold=10.0; iteration 9 < 10 -> Complete
        assertTrue(judge.decide(msgWith(substantive), ctxWith(9, 10)).isComplete());
        // iteration 10 >= 10 -> Escalate
        assertTrue(judge.decide(msgWith(substantive), ctxWith(10, 10)).isEscalate());
    }

    @Test
    void maxIterationsZeroNeverEscalates() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision result = judge.decide(
                msgWith("Substantive answer text here."),
                ctxWith(0, 0));

        assertTrue(result.isComplete(), "maxIterations=0 disables escalation (guard against div-by-zero)");
    }

    // (i) custom configuration overrides defaults correctly
    @Test
    void customConfigurationOverridesDefaults() {
        String customContinuation = "Custom: please continue";
        String customEscalationTemplate = "Custom escalate iter=%d max=%d ratio=%s";
        CompletionRuleConfig custom = new CompletionRuleConfig(20, 0.5, customContinuation, customEscalationTemplate);
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge(custom);

        assertEquals(20, judge.getConfig().getMinResponseLength());
        assertEquals(0.5, judge.getConfig().getEscalationRatio());
        assertEquals(customContinuation, judge.getConfig().getContinuationMessage());
        assertEquals(customEscalationTemplate, judge.getConfig().getEscalationReasonTemplate());

        // minResponseLength=20: a 15-char response is "short" -> Continue
        assertTrue(judge.decide(msgWith("012345678901234"), ctxWith(0, 10)).isContinue());

        // escalationRatio=0.5, maxIterations=10 -> threshold=5.0; iteration 5 -> Escalate (with custom template)
        CompletionDecision.Escalate escalate = (CompletionDecision.Escalate)
                judge.decide(msgWith("012345678901234567890123456-char-substantive-answer"),
                        ctxWith(5, 10));
        String reason = escalate.getReason();
        assertNotNull(reason);
        assertTrue(reason.startsWith("Custom escalate iter=5 max=10 ratio=0.5"),
                "Custom escalation template must be applied. Got: " + reason);
    }

    @Test
    void defaultsConfigMatchesBareConstructor() {
        RuleBasedCompletionJudge bare = new RuleBasedCompletionJudge();
        RuleBasedCompletionJudge withDefaults = new RuleBasedCompletionJudge(CompletionRuleConfig.defaults());

        assertEquals(bare.getConfig(), withDefaults.getConfig());
    }

    // (j) Continue decision carries a non-null continuation message; Escalate carries a non-null reason
    @Test
    void continueDecisionCarriesNonNullMessage() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision.Continue cont = (CompletionDecision.Continue)
                judge.decide(msgWith(""), ctxWith(0, 10));

        assertNotNull(cont.getMessage());
        assertFalse(cont.getMessage().isEmpty());
    }

    @Test
    void escalateDecisionCarriesNonNullReason() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision.Escalate escalate = (CompletionDecision.Escalate)
                judge.decide(msgWith("Substantive answer text here."), ctxWith(9, 10));

        assertNotNull(escalate.getReason());
        assertFalse(escalate.getReason().isEmpty());
    }

    // Static factory follows NoOpCompletionJudge.noOp() convention
    @Test
    void ruleBasedFactoryReturnsImplementationWithDefaults() {
        ICompletionJudge judge = RuleBasedCompletionJudge.ruleBased();

        assertTrue(judge instanceof RuleBasedCompletionJudge);
        assertEquals(CompletionRuleConfig.DEFAULT_MIN_RESPONSE_LENGTH,
                ((RuleBasedCompletionJudge) judge).getConfig().getMinResponseLength());
        assertEquals(CompletionRuleConfig.DEFAULT_ESCALATION_RATIO,
                ((RuleBasedCompletionJudge) judge).getConfig().getEscalationRatio());
    }

    @Test
    void ruleBasedFactoryWithConfigReturnsConfiguredInstance() {
        CompletionRuleConfig custom = new CompletionRuleConfig(50, 0.7, "go on", "escalated %d/%d");
        ICompletionJudge judge = RuleBasedCompletionJudge.ruleBased(custom);

        assertTrue(judge instanceof RuleBasedCompletionJudge);
        assertSame(custom, ((RuleBasedCompletionJudge) judge).getConfig());
    }

    @Test
    void implementsICompletionJudge() {
        assertTrue(ICompletionJudge.class.isAssignableFrom(RuleBasedCompletionJudge.class));
    }

    // All three decision outcomes are producible depending on input
    @Test
    void producesAllThreeOutcomes() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        CompletionDecision cont = judge.decide(msgWith(""), ctxWith(0, 10));
        CompletionDecision complete = judge.decide(
                msgWith("Substantive answer text here."), ctxWith(0, 10));
        CompletionDecision escalate = judge.decide(
                msgWith("Substantive answer text here."), ctxWith(9, 10));

        assertTrue(cont.isContinue());
        assertTrue(complete.isComplete());
        assertTrue(escalate.isEscalate());
    }

    // Rule ordering: empty + near-budget must Continue (cheap check first), not Escalate
    @Test
    void emptyContentAtNearBudgetStillReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        // Empty content + near-budget exhaustion -> should Continue (cheap check wins over escalation)
        CompletionDecision result = judge.decide(msgWith(""), ctxWith(9, 10));

        assertTrue(result.isContinue(),
                "Empty-content rule must take priority over near-budget escalation rule");
    }

    @Test
    void trivialContentAtNearBudgetStillReturnsContinue() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        // Trivial content (5 chars < 10) + near-budget exhaustion -> should Continue
        CompletionDecision result = judge.decide(msgWith("short"), ctxWith(9, 10));

        assertTrue(result.isContinue(),
                "Trivial-content rule must take priority over near-budget escalation rule");
    }

    @Test
    void nullContextSkipsEscalation() {
        RuleBasedCompletionJudge judge = new RuleBasedCompletionJudge();

        // Substantive content + null ctx -> Complete (no escalation possible)
        CompletionDecision result = judge.decide(
                msgWith("Substantive answer text here."), null);

        assertTrue(result.isComplete(), "Null ctx must skip near-budget escalation");
    }

    // Config validation
    @Test
    void configRejectsNegativeMinResponseLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionRuleConfig(-1, 0.9, null, null));
    }

    @Test
    void configRejectsNanEscalationRatio() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionRuleConfig(10, Double.NaN, null, null));
    }

    @Test
    void configRejectsNegativeEscalationRatio() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionRuleConfig(10, -0.1, null, null));
    }

    @Test
    void configRejectsRatioAboveOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionRuleConfig(10, 1.5, null, null));
    }

    @Test
    void judgeRejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> new RuleBasedCompletionJudge(null));
    }

    @Test
    void configDefaultsAreApplied() {
        CompletionRuleConfig c = CompletionRuleConfig.defaults();

        assertEquals(CompletionRuleConfig.DEFAULT_MIN_RESPONSE_LENGTH, c.getMinResponseLength());
        assertEquals(CompletionRuleConfig.DEFAULT_ESCALATION_RATIO, c.getEscalationRatio());
        assertEquals(CompletionRuleConfig.DEFAULT_CONTINUATION_MESSAGE, c.getContinuationMessage());
        assertEquals(CompletionRuleConfig.DEFAULT_ESCALATION_REASON_TEMPLATE, c.getEscalationReasonTemplate());
    }
}
