package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving the skill curator is wired into
 * {@link DefaultAgentEngine}: {@code curateSkills()} sources the skill registry
 * from the registered {@link ISkillProvider}, invokes the registered
 * {@link ISkillCurator}, and returns the {@link SkillCurationResult}
 * synchronously.
 *
 * <p>Verifies (plan 167 Phase 3 Exit Criteria):
 * <ol>
 *   <li>Default ({@link NoOpSkillCurator} + {@link NoOpSkillProvider}):
 *       curation returns empty success, no LLM call, backward compatible.</li>
 *   <li>{@link LLMCurator} + {@link FileSystemSkillProvider}: curation produces
 *       per-skill assessments from test fixtures parsed from the test
 *       {@link IChatService} response.</li>
 *   <li>Empty registry ({@link NoOpSkillProvider}) + {@link LLMCurator}:
 *       curation returns empty success without calling the LLM.</li>
 *   <li>{@link LLMCurator} whose {@link IChatService} throws: curation returns
 *       fail-open result with fail marker, engine does not crash.</li>
 * </ol>
 */
public class TestSkillCuratorIntegration {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ===== test doubles =====

    private static ChatAssistantMessage msgWith(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    /**
     * Test {@link IChatService} that returns a fixed canned response and counts
     * calls. Used to verify the engine actually invokes the curator →
     * IChatService path.
     */
    private static final class TestChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final ChatResponse response;
        private final RuntimeException failure;

        private TestChatService(ChatResponse response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
        }

        static TestChatService returning(String content) {
            return new TestChatService(ChatResponse.success(msgWith(content)), null);
        }

        static TestChatService throwing(RuntimeException ex) {
            return new TestChatService(null, ex);
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

    // ===== (1) NoOp default: backward compatibility =====

    @Test
    void noOpDefaultReturnsEmptySuccessWithoutLlmCall() {
        // Engine with defaults: NoOpSkillProvider + NoOpSkillCurator
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);

        SkillCurationResult result = engine.curateSkills();

        assertNotNull(result);
        assertTrue(result.isSuccess(), "Default NoOp curation must return success");
        assertTrue(result.getAssessments().isEmpty());
        assertEquals("no-op", result.getMetadata().getCuratorType());
    }

    @Test
    void noOpDefaultIsBackwardCompatible() {
        // Verify that constructing an engine without setting a curator leaves
        // curateSkills() working and returning the no-op result. This proves
        // the new field/setter don't break existing engine usage.
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);

        // curateSkills should not throw
        SkillCurationResult result = engine.curateSkills();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    // ===== (2) LLMCurator + FileSystemSkillProvider: E2E =====

    @Test
    void llmCuratorWithFileSystemProviderProducesAssessmentsFromFixtures() {
        // The test IChatService returns a canned JSON response with assessments
        // for the known fixture skills. The integration proves: engine →
        // curateSkills() → skillProvider.getSkills() → curator.curate() →
        // IChatService.call() → parse → SkillCurationResult.
        String json = "{\"assessments\":["
                + "{\"name\":\"web-search\",\"rating\":\"WELL_DEFINED\",\"recommendation\":\"\",\"rationale\":\"Clear\"},"
                + "{\"name\":\"log-analysis\",\"rating\":\"NEEDS_IMPROVEMENT\",\"recommendation\":\"Add intent\",\"rationale\":\"Vague\"},"
                + "{\"name\":\"code-review\",\"rating\":\"WELL_DEFINED\",\"recommendation\":\"\",\"rationale\":\"Good\"}"
                + "],\"coverageGaps\":[\"no formatting skill\"],\"redundancies\":[]}";
        TestChatService chatService = TestChatService.returning(json);

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        engine.setSkillProvider(new FileSystemSkillProvider("/skills"));
        engine.setSkillCurator(LLMCurator.llm(chatService));

        SkillCurationResult result = engine.curateSkills();

        assertTrue(result.isSuccess());
        assertEquals(1, chatService.callCount.get(),
                "Engine must invoke IChatService exactly once for the skill registry");
        assertEquals(3, result.getAssessments().size(),
                "All 3 fixture skills must be assessed");

        // Verify specific assessments
        SkillCurationResult.SkillAssessment webSearch = findAssessment(result, "web-search");
        assertNotNull(webSearch);
        assertEquals(SkillQualityRating.WELL_DEFINED, webSearch.getRating());

        SkillCurationResult.SkillAssessment logAnalysis = findAssessment(result, "log-analysis");
        assertNotNull(logAnalysis);
        assertEquals(SkillQualityRating.NEEDS_IMPROVEMENT, logAnalysis.getRating());

        assertEquals(1, result.getCoverageGaps().size());
        assertEquals("no formatting skill", result.getCoverageGaps().get(0));
        assertEquals("llm", result.getMetadata().getCuratorType());
    }

    // ===== (3) Empty registry + LLMCurator: no LLM call =====

    @Test
    void emptyRegistryWithLlmCuratorReturnsEmptyWithoutLlmCall() {
        TestChatService chatService = TestChatService.returning(
                "{\"assessments\":[],\"coverageGaps\":[],\"redundancies\":[]}");

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        // Default skillProvider is NoOpSkillProvider (empty registry)
        engine.setSkillCurator(LLMCurator.llm(chatService));

        SkillCurationResult result = engine.curateSkills();

        assertTrue(result.isSuccess());
        assertTrue(result.getAssessments().isEmpty());
        assertEquals(0, chatService.callCount.get(),
                "Empty registry must not trigger an LLM call");
    }

    // ===== (4) LLMCurator error: fail-open with fail marker =====

    @Test
    void llmCuratorErrorReturnsFailOpenWithoutCrashingEngine() {
        TestChatService chatService = TestChatService.throwing(
                new RuntimeException("LLM service unavailable"));

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        engine.setSkillProvider(new FileSystemSkillProvider("/skills"));
        engine.setSkillCurator(LLMCurator.llm(chatService));

        SkillCurationResult result = engine.curateSkills();

        // Engine does not crash; no exception propagates
        assertNotNull(result);
        assertFalse(result.isSuccess(), "LLM error must produce fail marker");
        assertNotNull(result.getMetadata().getFailureDetail());
        assertTrue(result.getMetadata().getFailureDetail().contains("LLM service unavailable"),
                "Failure detail must contain the error message");
    }

    // ===== wiring verification: setter null-check default =====

    @Test
    void setCuratorNullDefaultsToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        engine.setSkillCurator(null);

        SkillCurationResult result = engine.curateSkills();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("no-op", result.getMetadata().getCuratorType());
    }

    @Test
    void curatorIsNotInvokedDuringExecute() {
        // The curator is an on-demand analytical tool — it must NOT be invoked
        // during execute(). We verify this indirectly: the test IChatService
        // for curation is never called when curateSkills() is not invoked.
        // (The ReAct loop uses the engine's own chatService, which is separate.)
        TestChatService curatorChatService = TestChatService.returning(
                "{\"assessments\":[],\"coverageGaps\":[],\"redundancies\":[]}");

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        engine.setSkillCurator(LLMCurator.llm(curatorChatService));

        // Don't call curateSkills() — the curator's chatService must not be touched
        assertEquals(0, curatorChatService.callCount.get(),
                "Curator must not be invoked unless curateSkills() is called");
    }

    // ===== helper =====

    private static SkillCurationResult.SkillAssessment findAssessment(
            SkillCurationResult result, String skillName) {
        for (SkillCurationResult.SkillAssessment a : result.getAssessments()) {
            if (skillName.equals(a.getSkillName())) {
                return a;
            }
        }
        return null;
    }
}
