package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;

/**
 * Rule-based {@link ICompletionJudge} that detects the most common premature-completion patterns
 * using deterministic, configurable heuristics. Replaces the inert {@link NoOpCompletionJudge}
 * pass-through for callers that want actual completion verification (design §5.3 Phase 2).
 *
 * <p>Rules are evaluated in priority order (cheap, high-precision checks first; safety-net last):
 * <ol>
 *   <li><b>Empty/blank response</b>: assistant content is {@code null}, empty, or whitespace-only
 *       → {@link CompletionDecision.Continue} with a continuation message.</li>
 *   <li><b>Trivially short response</b>: trimmed content length &lt; {@code minResponseLength}
 *       → {@link CompletionDecision.Continue}.</li>
 *   <li><b>Near-budget exhaustion</b>: {@code currentIteration >= maxIterations * escalationRatio}
 *       → {@link CompletionDecision.Escalate} with a descriptive reason.</li>
 *   <li><b>Default</b>: all rules pass → {@link CompletionDecision.Complete}.</li>
 * </ol>
 *
 * <p>This ordering ensures an empty response triggers {@code Continue} (more appropriate than
 * escalating on an empty message), while near-budget escalation only fires when the response
 * already has substantive content but the loop is about to hit {@code maxIterations}.
 */
public final class RuleBasedCompletionJudge implements ICompletionJudge {

    private final CompletionRuleConfig config;

    public RuleBasedCompletionJudge() {
        this(CompletionRuleConfig.defaults());
    }

    public RuleBasedCompletionJudge(CompletionRuleConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("CompletionRuleConfig must not be null");
        }
        this.config = config;
    }

    public static ICompletionJudge ruleBased() {
        return new RuleBasedCompletionJudge();
    }

    public static ICompletionJudge ruleBased(CompletionRuleConfig config) {
        return new RuleBasedCompletionJudge(config);
    }

    public CompletionRuleConfig getConfig() {
        return config;
    }

    @Override
    public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
        String content = extractContent(assistantMessage);

        if (content == null || content.isEmpty()) {
            return new CompletionDecision.Continue(config.getContinuationMessage());
        }

        String trimmed = content.trim();
        if (trimmed.length() < config.getMinResponseLength()) {
            return new CompletionDecision.Continue(config.getContinuationMessage());
        }

        if (ctx != null && isNearBudgetExhaustion(ctx)) {
            String reason = config.formatEscalationReason(ctx.getCurrentIteration(), ctx.getMaxIterations());
            return new CompletionDecision.Escalate(reason);
        }

        return CompletionDecision.Complete.instance();
    }

    private boolean isNearBudgetExhaustion(AgentExecutionContext ctx) {
        int maxIterations = ctx.getMaxIterations();
        if (maxIterations <= 0) {
            return false;
        }
        double threshold = maxIterations * config.getEscalationRatio();
        return ctx.getCurrentIteration() >= threshold;
    }

    private static String extractContent(ChatAssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return null;
        }
        return assistantMessage.getContent();
    }
}
