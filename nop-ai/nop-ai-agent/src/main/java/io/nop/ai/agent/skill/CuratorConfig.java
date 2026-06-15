package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.IChatService;

/**
 * Configuration object for {@link LLMCurator} (design
 * {@code skill-system-design.md} §5.5). Carries the {@link IChatService} used
 * for curation calls, the curation system prompt, optional model-name
 * override, max response tokens, temperature, and max skills per curation
 * call.
 *
 * <p>Follows the {@code LlmJudgeConfig} pattern: a config object carries the
 * chat service + system prompt + model override + tuning parameters with
 * constructor validation and sensible defaults.
 */
public final class CuratorConfig {

    public static final int DEFAULT_MAX_TOKENS = 1000;
    public static final float DEFAULT_TEMPERATURE = 0.0f;
    public static final int DEFAULT_MAX_SKILLS_PER_CALL = 20;

    /**
     * Default curation system prompt. Instructs the LLM to act as a skill
     * curator: evaluate each skill definition for clarity, completeness,
     * coverage, and redundancy, and output assessments in a defined JSON
     * format. The format must agree with the parser in {@link LLMCurator}.
     */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are a skill curator for an autonomous agent platform. Your job is to evaluate "
                    + "the quality of skill definitions and produce advisory curation recommendations.\n\n"
                    + "For each skill, assess:\n"
                    + "- Clarity: is the goal clear and unambiguous?\n"
                    + "- Completeness: are dependencies, resource scopes, and intent signatures "
                    + "sufficiently specified?\n"
                    + "- Coverage: does the skill fill a unique capability gap?\n"
                    + "- Redundancy: does it overlap substantially with another skill?\n\n"
                    + "Quality ratings:\n"
                    + "- WELL_DEFINED: clear, complete, and non-redundant\n"
                    + "- NEEDS_IMPROVEMENT: usable but has gaps (vague goal, missing dependencies, "
                    + "ambiguous signatures, under-specified scope)\n"
                    + "- REDUNDANT: substantially overlaps with other skills; consolidation recommended\n\n"
                    + "Output format: respond with ONLY a JSON object (no markdown fences, no prose "
                    + "before or after):\n"
                    + "{\n"
                    + "  \"assessments\": [\n"
                    + "    {\n"
                    + "      \"name\": \"<skill-name>\",\n"
                    + "      \"rating\": \"WELL_DEFINED\" | \"NEEDS_IMPROVEMENT\" | \"REDUNDANT\",\n"
                    + "      \"recommendation\": \"<concise improvement suggestion, or empty string if well-defined>\",\n"
                    + "      \"rationale\": \"<why this rating was assigned>\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"coverageGaps\": [\"<capability that no registered skill covers>\"],\n"
                    + "  \"redundancies\": [\"<description of overlapping skills>\"]\n"
                    + "}\n\n"
                    + "Rules:\n"
                    + "1. Provide an assessment for EVERY skill listed in the input.\n"
                    + "2. The \"name\" field must match the skill's name exactly.\n"
                    + "3. Output valid JSON only — no markdown code fences, no commentary.\n"
                    + "4. coverageGaps and redundancies may be empty arrays.";

    private final IChatService chatService;
    private final String systemPrompt;
    private final String model;
    private final Integer maxTokens;
    private final Float temperature;
    private final int maxSkillsPerCall;

    public CuratorConfig(IChatService chatService,
                         String systemPrompt,
                         String model,
                         Integer maxTokens,
                         Float temperature,
                         int maxSkillsPerCall) {
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
        if (maxSkillsPerCall < 0) {
            throw new NopAiAgentException(
                    "maxSkillsPerCall must be >= 0: " + maxSkillsPerCall);
        }
        this.chatService = chatService;
        this.systemPrompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.model = model;
        this.maxTokens = maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;
        this.temperature = temperature != null ? temperature : DEFAULT_TEMPERATURE;
        this.maxSkillsPerCall = maxSkillsPerCall > 0 ? maxSkillsPerCall : DEFAULT_MAX_SKILLS_PER_CALL;
    }

    /**
     * Zero-tuning factory: returns a config with all defaults.
     */
    public static CuratorConfig defaults(IChatService chatService) {
        return new CuratorConfig(chatService, null, null, null, null, DEFAULT_MAX_SKILLS_PER_CALL);
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

    public int getMaxSkillsPerCall() {
        return maxSkillsPerCall;
    }

    @Override
    public String toString() {
        return "CuratorConfig{systemPrompt=" + (systemPrompt != null ? systemPrompt.length() + " chars" : "null")
                + ", model='" + model + '\''
                + ", maxTokens=" + maxTokens
                + ", temperature=" + temperature
                + ", maxSkillsPerCall=" + maxSkillsPerCall
                + '}';
    }
}
