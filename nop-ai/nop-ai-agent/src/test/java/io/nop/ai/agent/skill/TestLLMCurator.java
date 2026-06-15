package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LLMCurator} with a test {@link IChatService}. Covers
 * scenarios (a) through (j) as defined in plan 167 Phase 2 Exit Criteria.
 */
public class TestLLMCurator {

    // ===== helpers =====

    private static SkillModel skill(String name, String goal) {
        SkillModel s = new SkillModel();
        s.setName(name);
        s.setGoal(goal);
        return s;
    }

    private static ChatAssistantMessage msgWith(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    private static String curationJson(String skillName, String rating) {
        return "{\"assessments\":[{\"name\":\"" + skillName + "\","
                + "\"rating\":\"" + rating + "\","
                + "\"recommendation\":\"test rec\","
                + "\"rationale\":\"test rationale\"}],"
                + "\"coverageGaps\":[],\"redundancies\":[]}";
    }

    private static String curationJsonMulti(String... names) {
        StringBuilder sb = new StringBuilder("{\"assessments\":[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(names[i]).append("\",")
                    .append("\"rating\":\"WELL_DEFINED\",")
                    .append("\"recommendation\":\"ok\",")
                    .append("\"rationale\":\"good\"}");
        }
        sb.append("],\"coverageGaps\":[\"gap1\"],\"redundancies\":[\"red1\"]}");
        return sb.toString();
    }

    /**
     * Mock IChatService that returns a fixed response on every call, with
     * configurable token usage.
     */
    private static final class FixedResponseChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final ChatResponse response;
        private final RuntimeException failure;

        private FixedResponseChatService(ChatResponse response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
        }

        static FixedResponseChatService returning(String content) {
            return new FixedResponseChatService(ChatResponse.success(msgWith(content)), null);
        }

        static FixedResponseChatService returningWithTokens(String content, int promptTokens, int completionTokens) {
            ChatResponse resp = ChatResponse.success(msgWith(content));
            resp.setUsage(new ChatUsage(promptTokens, completionTokens));
            return new FixedResponseChatService(resp, null);
        }

        static FixedResponseChatService throwing(RuntimeException toThrow) {
            return new FixedResponseChatService(null, toThrow);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Mock IChatService that returns a null response (not null message —
     * the call() itself returns null).
     */
    private static final class NullResponseChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return null;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Mock IChatService that returns an error response (isSuccess()==false).
     */
    private static final class ErrorChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return ChatResponse.error("ERR", "provider error");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Mock IChatService that returns a null message in the response.
     */
    private static final class NullMessageChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return new ChatResponse();
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Mock IChatService that returns different responses based on the call
     * number (1-based). Used for batched and partial-failure tests.
     */
    private static final class PerCallChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final IntFunction<ChatResponse> responseFactory;

        PerCallChatService(IntFunction<ChatResponse> responseFactory) {
            this.responseFactory = responseFactory;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int n = callCount.incrementAndGet();
            return responseFactory.apply(n);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static LLMCurator curator(IChatService svc) {
        return new LLMCurator(CuratorConfig.defaults(svc));
    }

    // ===== (a) Normal curation =====

    @Test
    void normalCurationProducesCorrectAssessments() {
        String json = curationJsonMulti("web-search", "log-analysis");
        FixedResponseChatService svc = FixedResponseChatService.returning(json);
        LLMCurator curator = curator(svc);

        Collection<SkillModel> skills = Arrays.asList(
                skill("web-search", "Search the web"),
                skill("log-analysis", "Analyze logs"));
        SkillCurationResult result = curator.curate(skills);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAssessments().size());
        assertEquals("web-search", result.getAssessments().get(0).getSkillName());
        assertEquals(SkillQualityRating.WELL_DEFINED, result.getAssessments().get(0).getRating());
        assertEquals("log-analysis", result.getAssessments().get(1).getSkillName());
        assertEquals(1, result.getCoverageGaps().size());
        assertEquals("gap1", result.getCoverageGaps().get(0));
        assertEquals(1, result.getRedundancies().size());
        assertEquals("red1", result.getRedundancies().get(0));
        assertEquals("llm", result.getMetadata().getCuratorType());
        assertEquals(1, svc.callCount.get());
    }

    // ===== (b) Empty registry → empty success, no LLM call =====

    @Test
    void emptyRegistryReturnsEmptySuccessWithoutLlmCall() {
        FixedResponseChatService svc = FixedResponseChatService.returning(curationJson("x", "WELL_DEFINED"));
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(Collections.emptyList());

        assertTrue(result.isSuccess());
        assertTrue(result.getAssessments().isEmpty());
        assertEquals(0, svc.callCount.get(), "No LLM call must be made on empty registry");
    }

    @Test
    void nullRegistryReturnsEmptySuccessWithoutLlmCall() {
        FixedResponseChatService svc = FixedResponseChatService.returning(curationJson("x", "WELL_DEFINED"));
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(null);

        assertTrue(result.isSuccess());
        assertTrue(result.getAssessments().isEmpty());
        assertEquals(0, svc.callCount.get());
    }

    // ===== (c) LLM call throws → fail-open with fail marker =====

    @Test
    void llmCallThrowsReturnsFailOpenWithFailMarker() {
        FixedResponseChatService svc = FixedResponseChatService.throwing(new RuntimeException("LLM down"));
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess(), "RuntimeException must produce fail marker");
        assertTrue(result.getAssessments().isEmpty());
        assertEquals("llm", result.getMetadata().getCuratorType());
        assertNotNull(result.getMetadata().getFailureDetail());
        assertTrue(result.getMetadata().getFailureDetail().contains("LLM down"),
                "Failure detail must contain error message");
        assertEquals(1, svc.callCount.get());
    }

    // ===== (d) LLM returns null → fail-open with fail marker =====

    @Test
    void nullResponseReturnsFailOpenWithFailMarker() {
        NullResponseChatService svc = new NullResponseChatService();
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess());
        assertNotNull(result.getMetadata().getFailureDetail());
        assertEquals(1, svc.callCount.get());
    }

    // ===== (e) LLM returns unsuccessful response → fail-open =====

    @Test
    void unsuccessfulResponseReturnsFailOpenWithFailMarker() {
        ErrorChatService svc = new ErrorChatService();
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess());
        assertNotNull(result.getMetadata().getFailureDetail());
        assertEquals(1, svc.callCount.get());
    }

    @Test
    void nullMessageResponseReturnsFailOpenWithFailMarker() {
        NullMessageChatService svc = new NullMessageChatService();
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess());
        assertNotNull(result.getMetadata().getFailureDetail());
    }

    // ===== (f) LLM returns unparseable content → fail-open =====

    @Test
    void unparseableContentReturnsFailOpenWithFailMarker() {
        FixedResponseChatService svc = FixedResponseChatService.returning("This is not JSON at all.");
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess(), "Unparseable content must produce fail marker");
        assertNotNull(result.getMetadata().getFailureDetail());
    }

    @Test
    void emptyContentReturnsFailOpenWithFailMarker() {
        FixedResponseChatService svc = FixedResponseChatService.returning("");
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertFalse(result.isSuccess(), "Empty content must produce fail marker");
    }

    @Test
    void markdownFencedJsonIsParsedCorrectly() {
        String fenced = "```json\n" + curationJson("web-search", "WELL_DEFINED") + "\n```";
        FixedResponseChatService svc = FixedResponseChatService.returning(fenced);
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertTrue(result.isSuccess(), "Markdown-fenced JSON must be parsed successfully");
        assertEquals(1, result.getAssessments().size());
        assertEquals("web-search", result.getAssessments().get(0).getSkillName());
    }

    @Test
    void proseWrappedJsonIsParsedCorrectly() {
        String wrapped = "Here are my curation results:\n"
                + curationJson("web-search", "NEEDS_IMPROVEMENT")
                + "\nThat's all.";
        FixedResponseChatService svc = FixedResponseChatService.returning(wrapped);
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertTrue(result.isSuccess(), "Prose-wrapped JSON must be parsed");
        assertEquals(1, result.getAssessments().size());
        assertEquals(SkillQualityRating.NEEDS_IMPROVEMENT, result.getAssessments().get(0).getRating());
    }

    // ===== (g) Registry exceeds maxSkillsPerCall → batched curation =====

    @Test
    void batchedCurationMakesMultipleCallsAndMergesResults() {
        // 4 skills, maxSkillsPerCall=2 → 2 batches
        PerCallChatService svc = new PerCallChatService(callNum -> {
            // Each call returns an assessment for the batch index
            return ChatResponse.success(msgWith(curationJson("batch-" + callNum, "WELL_DEFINED")));
        });

        LLMCurator curator = new LLMCurator(new CuratorConfig(svc, null, null, null, null, 2));

        List<SkillModel> skills = Arrays.asList(
                skill("s1", "g1"), skill("s2", "g2"), skill("s3", "g3"), skill("s4", "g4"));
        SkillCurationResult result = curator.curate(skills);

        assertTrue(result.isSuccess());
        assertEquals(2, svc.callCount.get(), "4 skills with batch size 2 must produce 2 LLM calls");
        assertEquals(2, result.getAssessments().size(), "Merged result must have assessments from both batches");
        assertEquals("batch-1", result.getAssessments().get(0).getSkillName());
        assertEquals("batch-2", result.getAssessments().get(1).getSkillName());
    }

    // ===== (h) Token usage accumulated in metadata =====

    @Test
    void tokenUsageAccumulatedOnSuccessfulCall() {
        FixedResponseChatService svc = FixedResponseChatService.returningWithTokens(
                curationJson("web-search", "WELL_DEFINED"), 100, 50);
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));

        assertTrue(result.isSuccess());
        assertEquals(100, result.getMetadata().getPromptTokens());
        assertEquals(50, result.getMetadata().getCompletionTokens());
        assertEquals(150, result.getMetadata().getTotalTokens());
    }

    @Test
    void tokenUsageAccumulatedAcrossBatches() {
        PerCallChatService svc = new PerCallChatService(callNum -> {
            ChatResponse resp = ChatResponse.success(msgWith(curationJson("b" + callNum, "WELL_DEFINED")));
            resp.setUsage(new ChatUsage(100, 50));
            return resp;
        });

        LLMCurator curator = new LLMCurator(new CuratorConfig(svc, null, null, null, null, 1));

        List<SkillModel> skills = Arrays.asList(skill("s1", "g1"), skill("s2", "g2"));
        SkillCurationResult result = curator.curate(skills);

        assertTrue(result.isSuccess());
        assertEquals(2, svc.callCount.get());
        assertEquals(200, result.getMetadata().getPromptTokens(), "Prompt tokens must accumulate across 2 batches");
        assertEquals(100, result.getMetadata().getCompletionTokens());
        assertEquals(300, result.getMetadata().getTotalTokens());
    }

    // ===== (i) Static factory llm(IChatService) =====

    @Test
    void factoryLlmWithChatServiceProducesWorkingCurator() {
        FixedResponseChatService svc = FixedResponseChatService.returning(
                curationJson("web-search", "WELL_DEFINED"));
        ISkillCurator curator = LLMCurator.llm(svc);

        assertTrue(curator instanceof LLMCurator);
        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("web-search", "Search")));
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAssessments().size());
    }

    @Test
    void factoryLlmWithConfigReturnsConfiguredInstance() {
        FixedResponseChatService svc = FixedResponseChatService.returning(
                curationJson("x", "WELL_DEFINED"));
        CuratorConfig cfg = CuratorConfig.defaults(svc);
        ISkillCurator curator = LLMCurator.llm(cfg);

        assertTrue(curator instanceof LLMCurator);
        assertSame(cfg, ((LLMCurator) curator).getConfig());
    }

    // ===== (j) Partial batch failure =====

    @Test
    void partialBatchFailureRetainsSuccessfulAssessmentsWithOverallFailMarker() {
        // 3 skills, maxSkillsPerCall=1 → 3 batches (one skill each)
        // Call 1: success, Call 2: throw, Call 3: success
        PerCallChatService svc = new PerCallChatService(callNum -> {
            if (callNum == 2) {
                throw new NopAiAgentException("batch 2 failed");
            }
            return ChatResponse.success(msgWith(curationJson("skill-" + callNum, "WELL_DEFINED")));
        });

        LLMCurator curator = new LLMCurator(new CuratorConfig(svc, null, null, null, null, 1));

        List<SkillModel> skills = Arrays.asList(
                skill("s1", "g1"), skill("s2", "g2"), skill("s3", "g3"));
        SkillCurationResult result = curator.curate(skills);

        assertEquals(3, svc.callCount.get(), "3 skills with batch size 1 must produce 3 calls");
        assertFalse(result.isSuccess(), "Overall marker must be fail when any batch failed");

        // Successful batches (1 and 3) contribute their assessments
        assertEquals(2, result.getAssessments().size(),
                "Successful batches' assessments must be retained");
        assertEquals("skill-1", result.getAssessments().get(0).getSkillName());
        assertEquals("skill-3", result.getAssessments().get(1).getSkillName());

        // Failure detail must mention the failed batch
        assertNotNull(result.getMetadata().getFailureDetail());
        assertTrue(result.getMetadata().getFailureDetail().contains("batch"),
                "Failure detail must mention the failed batch");
    }

    // ===== config validation =====

    @Test
    void constructorRejectsNullConfig() {
        assertThrows(NopAiAgentException.class, () -> new LLMCurator(null));
    }

    @Test
    void configRejectsNullChatService() {
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(null, null, null, null, null, 20));
    }

    @Test
    void configRejectsZeroMaxTokens() {
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(new NullMessageChatService(), null, null, 0, null, 20));
    }

    @Test
    void configRejectsNegativeMaxTokens() {
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(new NullMessageChatService(), null, null, -1, null, 20));
    }

    @Test
    void configRejectsNegativeMaxSkillsPerCall() {
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(new NullMessageChatService(), null, null, null, null, -1));
    }

    @Test
    void configRejectsTemperatureOutOfRange() {
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(new NullMessageChatService(), null, null, null, -0.1f, 20));
        assertThrows(NopAiAgentException.class,
                () -> new CuratorConfig(new NullMessageChatService(), null, null, null, 2.1f, 20));
    }

    @Test
    void configDefaultsFactory() {
        NullMessageChatService svc = new NullMessageChatService();
        CuratorConfig c = CuratorConfig.defaults(svc);

        assertSame(svc, c.getChatService());
        assertEquals(CuratorConfig.DEFAULT_SYSTEM_PROMPT, c.getSystemPrompt());
        assertNull(c.getModel());
        assertEquals(CuratorConfig.DEFAULT_MAX_TOKENS, c.getMaxTokens());
        assertEquals(CuratorConfig.DEFAULT_TEMPERATURE, c.getTemperature());
        assertEquals(CuratorConfig.DEFAULT_MAX_SKILLS_PER_CALL, c.getMaxSkillsPerCall());
    }

    // ===== implements interface =====

    @Test
    void implementsISkillCurator() {
        assertTrue(ISkillCurator.class.isAssignableFrom(LLMCurator.class));
    }

    // ===== JSON extraction edge cases =====

    @Test
    void extractJsonHandlesPlainJson() {
        String json = LLMCurator.extractJson("{\"a\":1}");
        assertEquals("{\"a\":1}", json);
    }

    @Test
    void extractJsonHandlesMarkdownFence() {
        String json = LLMCurator.extractJson("```json\n{\"a\":1}\n```");
        assertEquals("{\"a\":1}", json);
    }

    @Test
    void extractJsonHandlesProseWrap() {
        String json = LLMCurator.extractJson("Here:\n{\"a\":1}\nDone.");
        assertEquals("{\"a\":1}", json);
    }

    @Test
    void extractJsonReturnsNullForNoJson() {
        assertNull(LLMCurator.extractJson("no json here"));
    }

    @Test
    void malformedAssessmentEntriesAreSkippedNotFailing() {
        // One valid, one with invalid rating, one with missing name
        String json = "{\"assessments\":["
                + "{\"name\":\"good\",\"rating\":\"WELL_DEFINED\",\"recommendation\":\"\",\"rationale\":\"\"},"
                + "{\"name\":\"bad-rating\",\"rating\":\"INVALID\",\"recommendation\":\"\",\"rationale\":\"\"},"
                + "{\"rating\":\"WELL_DEFINED\",\"recommendation\":\"\",\"rationale\":\"\"}"
                + "],\"coverageGaps\":[],\"redundancies\":[]}";
        FixedResponseChatService svc = FixedResponseChatService.returning(json);
        LLMCurator curator = curator(svc);

        SkillCurationResult result = curator.curate(
                Collections.singletonList(skill("good", "g")));

        assertTrue(result.isSuccess(), "Malformed entries must be skipped, not fail the batch");
        assertEquals(1, result.getAssessments().size(), "Only the valid entry must be parsed");
        assertEquals("good", result.getAssessments().get(0).getSkillName());
    }
}
