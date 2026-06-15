package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.IChatService;

import java.util.Objects;

/**
 * Configuration object for {@link LlmCompletionJudge}. Carries the {@link IChatService} used for
 * judgment calls, the judge system prompt, optional model-name override, max response tokens,
 * temperature, the fallback decision used on error/unparseable response, and the max number of
 * conversation messages included as context.
 *
 * <p>This is the "小模型" half of design §5.3 Phase 2 Completion Gate strategy. The deterministic
 * sibling {@link RuleBasedCompletionJudge} catches structural premature-completion patterns; this
 * config enables semantic verification via a small model call.
 */
public final class LlmJudgeConfig {

    public static final int DEFAULT_MAX_TOKENS = 200;
    public static final float DEFAULT_TEMPERATURE = 0.0f;
    public static final int DEFAULT_MAX_CONTEXT_MESSAGES = 20;

    /**
     * Default continuation message used when the Judge LLM returns CONTINUE but supplies no
     * explicit continuation guidance text.
     */
    public static final String DEFAULT_CONTINUATION_MESSAGE =
            "The completion judge requested a continuation. Please continue working on the task.";

    /**
     * Default judge system prompt. Explains the Judge role, the three verdicts, and the required
     * output format (exactly one keyword on the first non-empty line).
     */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are a strict completion judge for an autonomous agent. Your sole job is to decide "
                    + "whether the assistant's latest response actually completes the stated task goal.\n\n"
                    + "Verdicts (output EXACTLY ONE keyword on the FIRST NON-EMPTY LINE):\n"
                    + "- COMPLETE: the response substantively addresses the task goal and no obvious requirement is left undone.\n"
                    + "- CONTINUE: the response is incomplete or partially addresses the goal. On the lines following the "
                    + "keyword, state concisely what is still missing so the agent can continue (continuation guidance).\n"
                    + "- ESCALATE: the task cannot be completed autonomously (ambiguous requirements, missing information, "
                    + "or a hard blocker). On the lines following the keyword, state the escalation reason.\n\n"
                    + "Rules:\n"
                    + "1. Output only one verdict keyword on the first non-empty line, nothing else on that line.\n"
                    + "2. The keyword is case-insensitive but must be spelled exactly as above.\n"
                    + "3. Be strict but fair: a confident-sounding partial answer is CONTINUE, not COMPLETE.\n"
                    + "4. Do not repeat the assistant's response; only the verdict and (for CONTINUE/ESCALATE) the reason.";

    private final IChatService chatService;
    private final String systemPrompt;
    private final String model;
    private final Integer maxTokens;
    private final Float temperature;
    private final CompletionDecision fallbackDecision;
    private final int maxContextMessages;
    private final String defaultContinuationMessage;

    public LlmJudgeConfig(IChatService chatService,
                          String systemPrompt,
                          String model,
                          Integer maxTokens,
                          Float temperature,
                          CompletionDecision fallbackDecision,
                          int maxContextMessages,
                          String defaultContinuationMessage) {
        if (chatService == null) {
            throw new NopAiAgentException("chatService must not be null");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new NopAiAgentException("maxTokens must be > 0: " + maxTokens);
        }
        if (temperature != null && (Float.isNaN(temperature) || temperature < 0.0f || temperature > 2.0f)) {
            throw new NopAiAgentException(
                    "temperature must be in [0.0, 2.0] range, got: " + temperature);
        }
        if (maxContextMessages < 0) {
            throw new NopAiAgentException(
                    "maxContextMessages must be >= 0: " + maxContextMessages);
        }
        this.chatService = chatService;
        this.systemPrompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.model = model;
        this.maxTokens = maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;
        this.temperature = temperature != null ? temperature : DEFAULT_TEMPERATURE;
        this.fallbackDecision = fallbackDecision != null ? fallbackDecision : CompletionDecision.Complete.instance();
        this.maxContextMessages = maxContextMessages;
        this.defaultContinuationMessage = defaultContinuationMessage != null
                ? defaultContinuationMessage
                : DEFAULT_CONTINUATION_MESSAGE;
    }

    /**
     * Zero-tuning factory: returns a config with all defaults and fail-open (Complete) fallback.
     */
    public static LlmJudgeConfig defaults(IChatService chatService) {
        return new LlmJudgeConfig(chatService, null, null, null, null, null, DEFAULT_MAX_CONTEXT_MESSAGES, null);
    }

    public IChatService getChatService() {
        return chatService;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getModel() {
        return model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Float getTemperature() {
        return temperature;
    }

    public CompletionDecision getFallbackDecision() {
        return fallbackDecision;
    }

    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public String getDefaultContinuationMessage() {
        return defaultContinuationMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmJudgeConfig that = (LlmJudgeConfig) o;
        return maxContextMessages == that.maxContextMessages
                && Objects.equals(chatService, that.chatService)
                && Objects.equals(systemPrompt, that.systemPrompt)
                && Objects.equals(model, that.model)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(fallbackDecision, that.fallbackDecision)
                && Objects.equals(defaultContinuationMessage, that.defaultContinuationMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatService, systemPrompt, model, maxTokens, temperature,
                fallbackDecision, maxContextMessages, defaultContinuationMessage);
    }

    @Override
    public String toString() {
        return "LlmJudgeConfig{" +
                "systemPrompt='" + (systemPrompt != null ? systemPrompt.length() + " chars" : "null") + '\'' +
                ", model='" + model + '\'' +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", fallbackDecision=" + fallbackDecision +
                ", maxContextMessages=" + maxContextMessages +
                '}';
    }
}
