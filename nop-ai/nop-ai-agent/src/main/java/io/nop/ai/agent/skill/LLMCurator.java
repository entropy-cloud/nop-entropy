package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM-backed {@link ISkillCurator} that evaluates registered skill definitions
 * via {@link IChatService} and produces structured curation recommendations
 * (design {@code skill-system-design.md} §5.5).
 *
 * <p>Follows the {@code LlmCompletionJudge} pattern: a {@link CuratorConfig}
 * carries the chat service + system prompt + model override + tuning
 * parameters; the curator constructs a {@link ChatRequest}, calls
 * {@code IChatService.call()}, parses the response, and fail-opens on error.
 *
 * <p>Fail-open semantics with explicit marking: on LLM call
 * {@code RuntimeException}, null response, unsuccessful response, null/empty
 * message content, or unparseable content → returns a
 * {@link SkillCurationResult} with a <b>"curation failed" fail marker</b> in
 * metadata (NOT a silent empty success — the fail marker distinguishes "LLM
 * error" from "zero skills assessed").
 *
 * <p>Output format contract: the curator and its default system prompt agree
 * on a JSON format (see {@link CuratorConfig#DEFAULT_SYSTEM_PROMPT}). The LLM
 * response must carry, per assessed skill, a name reference plus the
 * assessment fields (quality rating, recommendation, rationale). The parser
 * extracts JSON from the response (handling markdown code fences), parses it,
 * and skips malformed per-skill entries rather than failing the whole batch.
 * If the entire response is unparseable, the batch produces a fail marker.
 *
 * <p>Batched curation: if the registry exceeds {@code maxSkillsPerCall}, the
 * curator makes sequential LLM calls per batch and merges results. Partial-
 * failure semantics: successful batches contribute their assessments; failed
 * batches contribute a fail marker; the merged result's overall marker is
 * "fail" if any batch failed, with successful partial assessments retained.
 */
public final class LLMCurator implements ISkillCurator {

    private static final Logger LOG = LoggerFactory.getLogger(LLMCurator.class);

    private final CuratorConfig config;

    public LLMCurator(CuratorConfig config) {
        if (config == null) {
            throw new NopAiAgentException("CuratorConfig must not be null");
        }
        this.config = config;
    }

    public static ISkillCurator llm(CuratorConfig config) {
        return new LLMCurator(config);
    }

    public static ISkillCurator llm(IChatService chatService) {
        return new LLMCurator(CuratorConfig.defaults(chatService));
    }

    public CuratorConfig getConfig() {
        return config;
    }

    @Override
    public SkillCurationResult curate(Collection<SkillModel> skills) {
        if (skills == null || skills.isEmpty()) {
            return SkillCurationResult.empty();
        }

        List<SkillModel> all = new ArrayList<>(skills);
        int batchSize = config.getMaxSkillsPerCall();

        if (all.size() <= batchSize) {
            return curateBatch(all);
        }

        return curateInBatches(all, batchSize);
    }

    // ===== single-batch curation =====

    private SkillCurationResult curateBatch(List<SkillModel> batch) {
        ChatRequest request = buildRequest(batch);

        ChatResponse response;
        try {
            response = config.getChatService().call(request, null);
        } catch (RuntimeException e) {
            LOG.warn("LLMCurator: chatService.call() threw for batch of {} skills",
                    batch.size(), e);
            return SkillCurationResult.failed("llm", "chatService.call() threw: " + e);
        }

        if (response == null) {
            LOG.warn("LLMCurator: null response from chatService");
            return SkillCurationResult.failed("llm", "null response from chatService");
        }
        if (!response.isSuccess()) {
            LOG.warn("LLMCurator: unsuccessful response: errorCode={}, error={}",
                    response.getErrorCode(), response.getError());
            return SkillCurationResult.failed("llm",
                    "unsuccessful response: " + response.getErrorCode() + " " + response.getError());
        }
        if (response.getMessage() == null) {
            LOG.warn("LLMCurator: null message in response");
            return SkillCurationResult.failed("llm", "null message in response");
        }

        String content = response.getMessage().getContent();
        if (content == null || content.trim().isEmpty()) {
            LOG.warn("LLMCurator: empty response content");
            return SkillCurationResult.failed("llm", "empty response content");
        }

        int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
        int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;

        return parseCurationResponse(content, promptTokens, completionTokens);
    }

    // ===== batched curation =====

    private SkillCurationResult curateInBatches(List<SkillModel> all, int batchSize) {
        List<SkillCurationResult.SkillAssessment> mergedAssessments = new ArrayList<>();
        List<String> mergedCoverageGaps = new ArrayList<>();
        List<String> mergedRedundancies = new ArrayList<>();
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        List<String> failureDetails = new ArrayList<>();

        for (int i = 0; i < all.size(); i += batchSize) {
            int end = Math.min(i + batchSize, all.size());
            List<SkillModel> batch = new ArrayList<>(all.subList(i, end));

            SkillCurationResult batchResult = curateBatch(batch);

            if (batchResult.isSuccess()) {
                mergedAssessments.addAll(batchResult.getAssessments());
                mergedCoverageGaps.addAll(batchResult.getCoverageGaps());
                mergedRedundancies.addAll(batchResult.getRedundancies());
                totalPromptTokens += batchResult.getMetadata().getPromptTokens();
                totalCompletionTokens += batchResult.getMetadata().getCompletionTokens();
            } else {
                failureDetails.add("batch[" + i + ".." + end + "]: "
                        + batchResult.getMetadata().getFailureDetail());
            }
        }

        boolean overallSuccess = failureDetails.isEmpty();
        int totalTokens = totalPromptTokens + totalCompletionTokens;
        String failureDetail = overallSuccess ? null : String.join("; ", failureDetails);

        SkillCurationResult.SkillCurationMetadata metadata = new SkillCurationResult.SkillCurationMetadata(
                "llm", config.getModel(), totalPromptTokens, totalCompletionTokens,
                totalTokens, overallSuccess, failureDetail);

        return new SkillCurationResult(mergedAssessments, mergedCoverageGaps, mergedRedundancies, metadata);
    }

    // ===== prompt construction =====

    private ChatRequest buildRequest(List<SkillModel> batch) {
        StringBuilder user = new StringBuilder();
        user.append("Evaluate the following ").append(batch.size())
                .append(" skill definition(s). For each, provide a quality assessment.\n\n");

        for (int i = 0; i < batch.size(); i++) {
            SkillModel skill = batch.get(i);
            user.append("--- Skill ").append(i + 1).append(" ---\n");
            user.append("name: ").append(nullSafe(skill.getName())).append('\n');
            user.append("goal: ").append(nullSafe(skill.getGoal())).append('\n');
            user.append("dependencies: ").append(listToString(skill.getDependencies())).append('\n');
            user.append("tags: ").append(setToString(skill.getTags())).append('\n');
            user.append("resourceScope: ").append(setToString(skill.getResourceScope())).append('\n');
            user.append("topPattern: ").append(skill.getTopPattern() != null ? skill.getTopPattern() : "unspecified").append('\n');
            user.append("intentSignature: ").append(listToString(skill.getIntentSignature())).append('\n');
            user.append('\n');
        }

        user.append("Now output your curation assessment as JSON (assessments array with name, ")
                .append("rating, recommendation, rationale; plus coverageGaps and redundancies arrays).");

        ChatRequest request = ChatRequest.systemAndUserPrompt(config.getSystemPrompt(), user.toString());
        request.withTemperature(config.getTemperature()).withMaxTokens(config.getMaxTokens());
        if (config.getModel() != null) {
            request.makeOptions().setModel(config.getModel());
        }
        return request;
    }

    // ===== response parsing =====

    @SuppressWarnings("unchecked")
    private SkillCurationResult parseCurationResponse(String content, int promptTokens, int completionTokens) {
        String json = extractJson(content);
        if (json == null) {
            LOG.warn("LLMCurator: no JSON object found in response (content length={})", content.length());
            return SkillCurationResult.failed("llm", "unparseable response: no JSON object found");
        }

        Object parsed;
        try {
            parsed = JsonTool.parseYaml(null, json);
        } catch (Exception e) {
            LOG.warn("LLMCurator: JSON parse failed: {}", e.toString());
            return SkillCurationResult.failed("llm", "unparseable response: JSON parse error");
        }

        if (!(parsed instanceof Map)) {
            LOG.warn("LLMCurator: parsed root is not a JSON object: {}", parsed.getClass().getName());
            return SkillCurationResult.failed("llm", "unparseable response: root is not a JSON object");
        }

        Map<String, Object> root = (Map<String, Object>) parsed;

        List<SkillCurationResult.SkillAssessment> assessments = extractAssessments(root);
        List<String> coverageGaps = extractStringList(root, "coverageGaps");
        List<String> redundancies = extractStringList(root, "redundancies");

        int totalTokens = promptTokens + completionTokens;
        SkillCurationResult.SkillCurationMetadata metadata = new SkillCurationResult.SkillCurationMetadata(
                "llm", config.getModel(), promptTokens, completionTokens, totalTokens, true, null);

        return new SkillCurationResult(assessments, coverageGaps, redundancies, metadata);
    }

    @SuppressWarnings("unchecked")
    private static List<SkillCurationResult.SkillAssessment> extractAssessments(Map<String, Object> root) {
        Object arr = root.get("assessments");
        if (!(arr instanceof Collection)) {
            return Collections.emptyList();
        }

        List<SkillCurationResult.SkillAssessment> result = new ArrayList<>();
        for (Object item : (Collection<Object>) arr) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) item;
            Object name = m.get("name");
            if (name == null || name.toString().trim().isEmpty()) {
                continue;
            }
            SkillQualityRating rating = parseRating(m.get("rating"));
            if (rating == null) {
                continue;
            }
            String recommendation = stringOrEmpty(m.get("recommendation"));
            String rationale = stringOrEmpty(m.get("rationale"));
            result.add(new SkillCurationResult.SkillAssessment(
                    name.toString().trim(), rating, recommendation, rationale));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Map<String, Object> root, String key) {
        Object val = root.get(key);
        if (!(val instanceof Collection)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (Collection<Object>) val) {
            if (item != null && !item.toString().trim().isEmpty()) {
                result.add(item.toString().trim());
            }
        }
        return result;
    }

    private static SkillQualityRating parseRating(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return SkillQualityRating.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Extract the JSON object substring from LLM output. Handles:
     * <ul>
     *   <li>Plain JSON: {@code {...}}</li>
     *   <li>Markdown code fences: {@code ```json\n{...}\n```}</li>
     *   <li>Prose-wrapped: {@code Here are results:\n{...}}</li>
     * </ul>
     *
     * @return the extracted JSON string, or null if no JSON object is found
     */
    static String extractJson(String content) {
        String text = content.trim();

        // Strip markdown code fences
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).trim();
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    // ===== formatting helpers =====

    private static String nullSafe(String value) {
        return value != null ? value : "unspecified";
    }

    private static String listToString(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "none";
        }
        return list.toString();
    }

    private static String setToString(java.util.Set<?> set) {
        if (set == null || set.isEmpty()) {
            return "none";
        }
        return set.toString();
    }
}
