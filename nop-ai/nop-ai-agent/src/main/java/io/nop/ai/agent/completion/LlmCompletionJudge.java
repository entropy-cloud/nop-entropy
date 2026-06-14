package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentPlanModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM-based {@link ICompletionJudge} that semantically verifies task completion by asking a small
 * model whether the assistant's response actually addresses the task goal (design §5.3 Phase 2
 * "小模型" strategy).
 *
 * <p>Complements the deterministic {@link RuleBasedCompletionJudge}: the rule-based Judge catches
 * structural premature-completion patterns (empty/trivial/near-budget); this Judge catches
 * substantive incompleteness where the response is structurally valid but does not actually address
 * the task.
 *
 * <p>Fail-open semantics: on LLM call error, null/empty response, or unparseable verdict, the Judge
 * returns the configured {@link LlmJudgeConfig#getFallbackDecision() fallback decision} (default
 * {@link CompletionDecision.Complete}). This is consistent with design §5.3: "Judge 的裁决是'建议'
 * 不是'命令'——引擎保留最终跳出权". The engine-level dead-loop protection
 * ({@code DEFAULT_MAX_COMPLETION_CONTINUES = 3}) bounds repeated Continue decisions regardless.
 *
 * <p>Verdict output format: the Judge's LLM is instructed to output exactly one keyword on the first
 * non-empty line — {@code COMPLETE}, {@code CONTINUE}, or {@code ESCALATE} (case-insensitive). For
 * CONTINUE/ESCALATE, the remainder of the response is used as the continuation message / escalation
 * reason. Unparseable first line → fallback decision.
 */
public final class LlmCompletionJudge implements ICompletionJudge {

    private static final Logger LOG = LoggerFactory.getLogger(LlmCompletionJudge.class);

    static final String META_KEY_VERDICT = "completion.llmJudgeVerdict";
    static final String META_KEY_FALLBACK = "completion.llmJudgeFallback";
    static final String FALLBACK_VERDICT_LABEL = "FALLBACK";

    private final LlmJudgeConfig config;

    public LlmCompletionJudge(LlmJudgeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("LlmJudgeConfig must not be null");
        }
        this.config = config;
    }

    public static ICompletionJudge llm(LlmJudgeConfig config) {
        return new LlmCompletionJudge(config);
    }

    public static ICompletionJudge llm(IChatService chatService) {
        return new LlmCompletionJudge(LlmJudgeConfig.defaults(chatService));
    }

    public LlmJudgeConfig getConfig() {
        return config;
    }

    @Override
    public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
        String content = extractContent(assistantMessage);

        // Null/blank guard: structural emptiness is the RuleBased Judge's domain; calling an LLM on
        // empty content wastes tokens. Return Complete without calling the LLM.
        if (content == null || content.trim().isEmpty()) {
            return CompletionDecision.Complete.instance();
        }

        // Cancel guard: if cancelled, return the fallback decision without calling the LLM.
        if (ctx != null && ctx.isCancelRequested()) {
            recordFallbackMetadata(ctx, "CANCEL_REQUESTED");
            return config.getFallbackDecision();
        }

        ChatRequest request = buildRequest(content, ctx);

        ChatResponse response;
        try {
            response = config.getChatService().call(request, null);
        } catch (RuntimeException e) {
            LOG.warn("LlmCompletionJudge: chatService.call() threw, using fallback decision. error={}", e.toString());
            recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
            return config.getFallbackDecision();
        }

        if (response == null || !response.isSuccess() || response.getMessage() == null) {
            recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
            return config.getFallbackDecision();
        }

        accumulateTokens(ctx, response);

        String verdictContent = response.getMessage().getContent();
        if (verdictContent == null || verdictContent.trim().isEmpty()) {
            recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
            return config.getFallbackDecision();
        }

        return parseVerdict(verdictContent, ctx);
    }

    private CompletionDecision parseVerdict(String verdictContent, AgentExecutionContext ctx) {
        // Extract the first non-empty line, trimmed, upper-cased for case-insensitive match.
        String firstLine = firstNonEmptyLine(verdictContent);
        if (firstLine == null) {
            recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
            return config.getFallbackDecision();
        }

        String keyword = firstLine.trim().toUpperCase();
        String remainder = remainderAfterFirstLine(verdictContent);
        String trimmedRemainder = remainder != null ? remainder.trim() : "";

        switch (keyword) {
            case "COMPLETE":
                recordVerdictMetadata(ctx, firstLine.trim(), false);
                return CompletionDecision.Complete.instance();
            case "CONTINUE": {
                String message = trimmedRemainder.isEmpty() ? config.getDefaultContinuationMessage() : trimmedRemainder;
                recordVerdictMetadata(ctx, firstLine.trim(), false);
                return new CompletionDecision.Continue(message);
            }
            case "ESCALATE": {
                String reason = trimmedRemainder.isEmpty() ? config.getDefaultContinuationMessage() : trimmedRemainder;
                recordVerdictMetadata(ctx, firstLine.trim(), false);
                return new CompletionDecision.Escalate(reason);
            }
            default:
                recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
                return config.getFallbackDecision();
        }
    }

    private ChatRequest buildRequest(String assistantContent, AgentExecutionContext ctx) {
        String goal = resolveGoal(ctx);
        String contextBlock = buildConversationContext(ctx);

        StringBuilder user = new StringBuilder();
        user.append("Task goal:\n").append(goal).append("\n\n");
        if (!contextBlock.isEmpty()) {
            user.append("Conversation context (most recent turns):\n")
                    .append(contextBlock)
                    .append("\n\n");
        }
        user.append("Assistant response to judge:\n")
                .append(assistantContent)
                .append("\n\nNow output your verdict (COMPLETE / CONTINUE / ESCALATE).");

        ChatRequest request = ChatRequest.systemAndUserPrompt(config.getSystemPrompt(), user.toString());
        request.withTemperature(config.getTemperature()).withMaxTokens(config.getMaxTokens());
        if (config.getModel() != null) {
            request.makeOptions().setModel(config.getModel());
        }
        return request;
    }

    private String resolveGoal(AgentExecutionContext ctx) {
        if (ctx != null) {
            AgentPlanModel plan = ctx.getPlan();
            if (plan != null && plan.getGoal() != null && !plan.getGoal().trim().isEmpty()) {
                return plan.getGoal();
            }
            AgentModel agentModel = ctx.getAgentModel();
            if (agentModel != null && agentModel.getDescription() != null
                    && !agentModel.getDescription().trim().isEmpty()) {
                return agentModel.getDescription();
            }
        }
        return "N/A";
    }

    /**
     * Take the last {@code maxContextMessages} messages regardless of type, exclude any system-role
     * message (to avoid duplicating the Judge's own system prompt), and concatenate the remaining
     * ones as prior-turn context (role + content) into a single text block.
     */
    private String buildConversationContext(AgentExecutionContext ctx) {
        if (ctx == null || ctx.getMessages() == null || ctx.getMessages().isEmpty()) {
            return "";
        }
        int limit = config.getMaxContextMessages();
        if (limit <= 0) {
            return "";
        }
        List<ChatMessage> all = ctx.getMessages();
        int from = Math.max(0, all.size() - limit);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (m == null) {
                continue;
            }
            String role = m.getRole();
            if ("system".equals(role)) {
                continue;
            }
            String c = m.getContent();
            if (c == null) {
                c = "";
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('[').append(role != null ? role : "unknown").append("] ").append(c);
        }
        return sb.toString();
    }

    private void accumulateTokens(AgentExecutionContext ctx, ChatResponse response) {
        if (ctx == null) {
            return;
        }
        ChatUsage usage = response.getUsage();
        if (usage == null) {
            return;
        }
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        if (promptTokens > 0 || completionTokens > 0) {
            ctx.setTokensUsed(ctx.getTokensUsed() + promptTokens + completionTokens);
        }
    }

    private void recordVerdictMetadata(AgentExecutionContext ctx, String rawVerdict, boolean fallback) {
        if (ctx == null || ctx.getMetadata() == null) {
            return;
        }
        ctx.getMetadata().put(META_KEY_VERDICT, rawVerdict);
        ctx.getMetadata().put(META_KEY_FALLBACK, fallback);
    }

    private void recordFallbackMetadata(AgentExecutionContext ctx, String label) {
        recordVerdictMetadata(ctx, label, true);
    }

    private static String extractContent(ChatAssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return null;
        }
        return assistantMessage.getContent();
    }

    private static String firstNonEmptyLine(String text) {
        if (text == null) {
            return null;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? len : nl;
            String line = text.substring(start, end).trim();
            if (!line.isEmpty()) {
                return line;
            }
            if (nl < 0) {
                return null;
            }
            start = nl + 1;
        }
        return null;
    }

    private static String remainderAfterFirstLine(String text) {
        if (text == null) {
            return "";
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int nl = text.indexOf('\n', start);
            if (nl < 0) {
                return "";
            }
            String line = text.substring(start, nl).trim();
            if (!line.isEmpty()) {
                return text.substring(nl + 1);
            }
            start = nl + 1;
        }
        return "";
    }
}
