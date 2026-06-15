package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentPlanModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LlmCompletionJudge} with a mocked {@link IChatService}. Covers all three
 * verdicts, parse-failure/call-error/empty-response fallback paths, null/blank/cancel guards,
 * custom config overrides, context-limit truncation, config validation, and metadata recording.
 */
public class TestLlmCompletionJudge {

    // ===== helpers =====

    private static ChatAssistantMessage msgWith(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return msg;
    }

    private static AgentExecutionContext ctxWithGoal(String goal) {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        model.setDescription("Test agent description");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s1");
        ctx.setMaxIterations(10);
        ctx.setCurrentIteration(0);
        ctx.setStatus(AgentExecStatus.running);
        if (goal != null) {
            AgentPlanModel plan = new AgentPlanModel();
            plan.setGoal(goal);
            ctx.setPlan(plan);
        }
        return ctx;
    }

    private static AgentExecutionContext ctxNoGoal() {
        return ctxWithGoal(null);
    }

    /**
     * A mock {@link IChatService} that returns a fixed assistant message on {@code call()} and
     * records the request (and invocation count) for assertions. Construct via the static factories.
     */
    private static final class MockChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();
        private final ChatResponse response;
        private final RuntimeException failure;

        private MockChatService(ChatResponse response, RuntimeException failure) {
            this.response = response;
            this.failure = failure;
        }

        static MockChatService returning(String assistantContent) {
            return new MockChatService(ChatResponse.success(msgWith(assistantContent)), null);
        }

        static MockChatService errorResponse(String errorCode, String errorMessage) {
            return new MockChatService(ChatResponse.error(errorCode, errorMessage), null);
        }

        static MockChatService throwing(RuntimeException toThrow) {
            return new MockChatService(null, toThrow);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callAsync not used in LlmCompletionJudge tests");
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            lastRequest.set(request);
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

    /** Special mock that returns a null message on the response. */
    private static final class NullMessageChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            ChatResponse resp = new ChatResponse();
            // message stays null, no error → isSuccess() true but getMessage() null
            return resp;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static LlmCompletionJudge judge(MockChatService svc) {
        return new LlmCompletionJudge(LlmJudgeConfig.defaults(svc));
    }

    private static String extractUserPrompt(ChatRequest req) {
        assertNotNull(req);
        // The user message is the last non-system message.
        for (int i = req.getMessages().size() - 1; i >= 0; i--) {
            ChatMessage m = req.getMessages().get(i);
            if (m instanceof ChatUserMessage) {
                return m.getContent();
            }
        }
        return null;
    }

    private static String extractSystemPrompt(ChatRequest req) {
        assertNotNull(req);
        for (ChatMessage m : req.getMessages()) {
            if (m instanceof ChatSystemMessage) {
                return m.getContent();
            }
        }
        return null;
    }

    // ===== (a) COMPLETE =====

    @Test
    void completeVerdictReturnsComplete() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("Final answer here."), ctxWithGoal("do task"));

        assertTrue(d.isComplete());
        assertEquals(1, svc.callCount.get());
    }

    // ===== (b) CONTINUE with continuation message =====

    @Test
    void continueVerdictReturnsContinueWithContinuationMessage() {
        MockChatService svc = MockChatService.returning("CONTINUE\nPlease address requirement X");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("Partial answer."), ctxWithGoal("do task"));

        assertTrue(d.isContinue());
        String msg = ((CompletionDecision.Continue) d).getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("requirement X"), "Continuation message must include remainder. Got: " + msg);
    }

    // ===== (c) ESCALATE with reason =====

    @Test
    void escalateVerdictReturnsEscalateWithReason() {
        MockChatService svc = MockChatService.returning("ESCALATE\nAmbiguous requirements need human input");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("I'm confused."), ctxWithGoal("do task"));

        assertTrue(d.isEscalate());
        String reason = ((CompletionDecision.Escalate) d).getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("Ambiguous"), "Escalate reason must include remainder. Got: " + reason);
    }

    // ===== (d) lowercase complete (case-insensitive) =====

    @Test
    void lowercaseCompleteVerdictReturnsComplete() {
        MockChatService svc = MockChatService.returning("complete");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Verdict matching must be case-insensitive");
    }

    // ===== (e) leading/trailing whitespace trimmed =====

    @Test
    void whitespaceAroundVerdictIsTrimmed() {
        MockChatService svc = MockChatService.returning("  COMPLETE  ");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Leading/trailing whitespace must be trimmed before matching");
    }

    // ===== (f) COMPLETE with extra lines ignored =====

    @Test
    void completeVerdictWithExtraLinesIgnored() {
        MockChatService svc = MockChatService.returning("COMPLETE\n\nextra notes");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "First-line match must win; extra lines ignored for COMPLETE");
    }

    // ===== (g) unparseable text → fallback (Complete by default) =====

    @Test
    void unparseableVerdictReturnsFallbackComplete() {
        MockChatService svc = MockChatService.returning("Yes, the task appears complete.");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Unparseable verdict must fall back to default Complete");
        assertEquals(1, svc.callCount.get());
    }

    // ===== (h) empty content string → fallback =====

    @Test
    void emptyVerdictContentReturnsFallback() {
        MockChatService svc = MockChatService.returning("");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Empty verdict content must fall back to default Complete");
    }

    // ===== (i) null message → fallback =====

    @Test
    void nullMessageReturnsFallback() {
        NullMessageChatService svc = new NullMessageChatService();
        LlmCompletionJudge judge = new LlmCompletionJudge(LlmJudgeConfig.defaults(svc));

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Null response message must fall back to default Complete");
        assertEquals(1, svc.callCount.get());
    }

    // ===== (j) call throws → fallback, no exception propagates =====

    @Test
    void callExceptionReturnsFallbackWithoutPropagating() {
        MockChatService svc = MockChatService.throwing(new RuntimeException("LLM down"));
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "RuntimeException from call() must be caught → fallback Complete");
        assertEquals(1, svc.callCount.get());
    }

    // ===== (k) error response (isSuccess == false) → fallback =====

    @Test
    void errorResponseReturnsFallback() {
        MockChatService svc = MockChatService.errorResponse("ERR", "provider error");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("answer."), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Error response (isSuccess()==false) must fall back to default Complete");
    }

    // ===== (l) custom fallbackDecision = Continue =====

    @Test
    void customFallbackDecisionContinueAppliedToAllFailurePaths() {
        CompletionDecision.Continue customFallback = new CompletionDecision.Continue("custom continue");
        MockChatService unparseable = MockChatService.returning("not a keyword");
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                unparseable, null, null, null, null, customFallback, 20, null));

        // unparseable → custom Continue
        CompletionDecision d1 = judge.decide(msgWith("answer."), ctxWithGoal("g"));
        assertTrue(d1.isContinue(), "Unparseable path must use custom Continue fallback");
        assertEquals("custom continue", ((CompletionDecision.Continue) d1).getMessage());

        // call-throws path → custom Continue
        MockChatService throwing = MockChatService.throwing(new RuntimeException("x"));
        LlmCompletionJudge judge2 = new LlmCompletionJudge(new LlmJudgeConfig(
                throwing, null, null, null, null, customFallback, 20, null));
        CompletionDecision d2 = judge2.decide(msgWith("answer."), ctxWithGoal("g"));
        assertTrue(d2.isContinue(), "Call-throw path must use custom Continue fallback");
    }

    // ===== (m) null assistantMessage → Complete, no LLM call =====

    @Test
    void nullAssistantMessageReturnsCompleteWithoutLlmCall() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(null, ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Null assistantMessage must Complete (guard)");
        assertEquals(0, svc.callCount.get(), "chatService.call() must not be invoked on null message guard");
    }

    // ===== (n) blank/whitespace assistantMessage → Complete, no LLM call =====

    @Test
    void blankAssistantMessageReturnsCompleteWithoutLlmCall() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("   \t\n  "), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Whitespace-only assistantMessage must Complete (guard)");
        assertEquals(0, svc.callCount.get(), "chatService.call() must not be invoked on blank message guard");
    }

    @Test
    void nullContentAssistantMessageReturnsCompleteWithoutLlmCall() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);

        ChatAssistantMessage m = new ChatAssistantMessage();
        m.setContent(null);
        CompletionDecision d = judge.decide(m, ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Null-content assistantMessage must Complete (guard)");
        assertEquals(0, svc.callCount.get());
    }

    // ===== (o) cancel requested → fallback, no LLM call =====

    @Test
    void cancelRequestedReturnsFallbackWithoutLlmCall() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("g");
        ctx.setCancelRequested(true);

        CompletionDecision d = judge.decide(msgWith("answer."), ctx);

        assertTrue(d.isComplete(), "Default fallback is Complete on cancel");
        assertEquals(0, svc.callCount.get(), "chatService.call() must not be invoked on cancel guard");

        // metadata recorded as fallback
        assertEquals(true, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
        assertEquals("CANCEL_REQUESTED", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT));
    }

    // ===== (p) maxContextMessages = 3 with 10 messages → at most 3 in prompt =====

    @Test
    void maxContextMessagesTruncatesConversationHistory() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                svc, null, null, null, null, null, 3, null));
        AgentExecutionContext ctx = ctxWithGoal("g");
        // add 10 user messages with distinct markers
        for (int i = 0; i < 10; i++) {
            ctx.addMessage(new ChatUserMessage("msg-" + i));
        }

        judge.decide(msgWith("answer."), ctx);

        ChatRequest req = svc.lastRequest.get();
        assertNotNull(req);
        String userPrompt = extractUserPrompt(req);
        assertNotNull(userPrompt);
        // The most recent 3 user messages should appear; older ones should not.
        assertTrue(userPrompt.contains("msg-9"), "Most recent context message must be included");
        assertTrue(userPrompt.contains("msg-8"));
        assertTrue(userPrompt.contains("msg-7"));
        assertFalse(userPrompt.contains("msg-6"), "Context beyond maxContextMessages must be truncated");
        assertFalse(userPrompt.contains("msg-0"));
    }

    // ===== (q) custom systemPrompt appears in request =====

    @Test
    void customSystemPromptAppearsInRequest() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        String custom = "CUSTOM_JUDGE_PROMPT_SIGNATURE";
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                svc, custom, null, null, null, null, 20, null));

        judge.decide(msgWith("answer."), ctxWithGoal("g"));

        ChatRequest req = svc.lastRequest.get();
        assertEquals(custom, extractSystemPrompt(req),
                "Custom system prompt must be set on the ChatRequest");
    }

    // ===== (r) custom temperature carried on request =====

    @Test
    void customTemperatureCarriedOnRequest() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                svc, null, null, null, 0.7f, null, 20, null));

        judge.decide(msgWith("answer."), ctxWithGoal("g"));

        ChatRequest req = svc.lastRequest.get();
        assertEquals(0.7f, req.getTemperature(), 0.0001f,
                "Custom temperature must be carried on the ChatRequest options");
    }

    // ===== (s) metadata: successful verdict records verdict + fallback=false =====

    @Test
    void successfulVerdictRecordsMetadata() {
        MockChatService svc = MockChatService.returning("CONTINUE\nPlease add tests");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("g");

        judge.decide(msgWith("answer."), ctx);

        assertEquals("CONTINUE", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT));
        assertEquals(false, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
    }

    // ===== (t) metadata: fallback records verdict=FALLBACK + fallback=true =====

    @Test
    void fallbackRecordsMetadata() {
        MockChatService svc = MockChatService.returning("garbage unparseable");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("g");

        judge.decide(msgWith("answer."), ctx);

        assertEquals("FALLBACK", ctx.getMetadata().get(LlmCompletionJudge.META_KEY_VERDICT));
        assertEquals(true, ctx.getMetadata().get(LlmCompletionJudge.META_KEY_FALLBACK));
    }

    // ===== (u) config validation =====

    @Test
    void configRejectsNullChatService() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(null, null, null, null, null, null, 20, null));
    }

    @Test
    void configRejectsZeroMaxTokens() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, 0, null, null, 20, null));
    }

    @Test
    void configRejectsNegativeMaxTokens() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, -5, null, null, 20, null));
    }

    @Test
    void configRejectsTemperatureBelowZero() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, null, -0.1f, null, 20, null));
    }

    @Test
    void configRejectsTemperatureAboveTwo() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, null, 2.5f, null, 20, null));
    }

    @Test
    void configRejectsNanTemperature() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, null, Float.NaN, null, 20, null));
    }

    @Test
    void configRejectsNegativeMaxContextMessages() {
        assertThrows(NopAiAgentException.class,
                () -> new LlmJudgeConfig(new NullMessageChatService(), null, null, null, null, null, -1, null));
    }

    @Test
    void judgeRejectsNullConfig() {
        assertThrows(NopAiAgentException.class, () -> new LlmCompletionJudge(null));
    }

    // ===== additional behavior coverage =====

    @Test
    void defaultsFactoryAppliesDefaults() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmJudgeConfig c = LlmJudgeConfig.defaults(svc);

        assertSame(svc, c.getChatService());
        assertEquals(LlmJudgeConfig.DEFAULT_SYSTEM_PROMPT, c.getSystemPrompt());
        assertNull(c.getModel(), "model override defaults to null");
        assertEquals(LlmJudgeConfig.DEFAULT_MAX_TOKENS, c.getMaxTokens());
        assertEquals(LlmJudgeConfig.DEFAULT_TEMPERATURE, c.getTemperature());
        assertTrue(c.getFallbackDecision().isComplete());
        assertEquals(LlmJudgeConfig.DEFAULT_MAX_CONTEXT_MESSAGES, c.getMaxContextMessages());
    }

    @Test
    void goalFromPlanAppearsInRequest() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("GOAL_SIGNATURE_TEXT");

        judge.decide(msgWith("answer."), ctx);

        String userPrompt = extractUserPrompt(svc.lastRequest.get());
        assertTrue(userPrompt.contains("GOAL_SIGNATURE_TEXT"),
                "Task goal must appear in the Judge prompt. Got: " + userPrompt);
    }

    @Test
    void goalFallsBackToAgentDescriptionWhenPlanGoalAbsent() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentModel model = new AgentModel();
        model.setDescription("DESC_SIGNATURE_TEXT");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s1");

        judge.decide(msgWith("answer."), ctx);

        String userPrompt = extractUserPrompt(svc.lastRequest.get());
        assertTrue(userPrompt.contains("DESC_SIGNATURE_TEXT"),
                "When plan goal is absent, agent description must be used. Got: " + userPrompt);
    }

    @Test
    void goalFallsBackToNaWhenBothAbsent() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = AgentExecutionContext.create(null, "s1");

        judge.decide(msgWith("answer."), ctx);

        String userPrompt = extractUserPrompt(svc.lastRequest.get());
        assertTrue(userPrompt.contains("N/A"),
                "When neither plan nor agentModel has a goal, prompt must contain N/A. Got: " + userPrompt);
    }

    @Test
    void systemMessagesExcludedFromContext() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("g");
        ctx.addMessage(new ChatSystemMessage("SYSTEM_SIGNATURE_TO_EXCLUDE"));
        ctx.addMessage(new ChatUserMessage("user-msg"));

        judge.decide(msgWith("answer."), ctx);

        String userPrompt = extractUserPrompt(svc.lastRequest.get());
        assertFalse(userPrompt.contains("SYSTEM_SIGNATURE_TO_EXCLUDE"),
                "System-role context messages must be excluded from the Judge prompt to avoid duplicating the system prompt");
        assertTrue(userPrompt.contains("user-msg"));
    }

    @Test
    void modelOverrideSetOnOptions() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                svc, null, "cheap-judge-model", null, null, null, 20, null));

        judge.decide(msgWith("answer."), ctxWithGoal("g"));

        ChatRequest req = svc.lastRequest.get();
        assertNotNull(req.getOptions(), "ChatOptions must be created when model override is set");
        assertEquals("cheap-judge-model", req.getOptions().getModel(),
                "Model override must be set on ChatOptions");
    }

    @Test
    void noModelOverrideLeavesModelNull() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);

        judge.decide(msgWith("answer."), ctxWithGoal("g"));

        ChatRequest req = svc.lastRequest.get();
        // model may be null (no options) or null on options — either is fine.
        assertTrue(req.getOptions() == null || req.getOptions().getModel() == null,
                "Without model override, model must remain unset");
    }

    @Test
    void tokenUsageAccumulatedIntoCtx() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = judge(svc);
        AgentExecutionContext ctx = ctxWithGoal("g");
        long before = ctx.getTokensUsed();

        judge.decide(msgWith("answer."), ctx);

        // The MockChatService builds a ChatResponse via ChatResponse.success(msg) which has null usage,
        // so tokensUsed should not change. We verify the accumulation logic does not throw and leaves
        // the value at its prior state when usage is null.
        assertEquals(before, ctx.getTokensUsed(),
                "Null usage response must not change tokensUsed");
    }

    @Test
    void producesAllThreeOutcomesPlusFallback() {
        // All three verdicts from the same judge config
        MockChatService complete = MockChatService.returning("COMPLETE");
        MockChatService cont = MockChatService.returning("CONTINUE\nmore");
        MockChatService esc = MockChatService.returning("ESCALATE\nreason");

        assertTrue(judge(complete).decide(msgWith("a"), ctxWithGoal("g")).isComplete());
        assertTrue(judge(cont).decide(msgWith("a"), ctxWithGoal("g")).isContinue());
        assertTrue(judge(esc).decide(msgWith("a"), ctxWithGoal("g")).isEscalate());
    }

    @Test
    void implementsICompletionJudge() {
        assertTrue(ICompletionJudge.class.isAssignableFrom(LlmCompletionJudge.class));
    }

    @Test
    void factoryLlmWithChatServiceReturnsImplementation() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        ICompletionJudge j = LlmCompletionJudge.llm(svc);

        assertTrue(j instanceof LlmCompletionJudge);
        assertTrue(j.decide(msgWith("a"), ctxWithGoal("g")).isComplete());
    }

    @Test
    void factoryLlmWithConfigReturnsConfiguredInstance() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmJudgeConfig cfg = LlmJudgeConfig.defaults(svc);
        ICompletionJudge j = LlmCompletionJudge.llm(cfg);

        assertTrue(j instanceof LlmCompletionJudge);
        assertSame(cfg, ((LlmCompletionJudge) j).getConfig());
    }

    @Test
    void continueVerdictWithoutRemainderUsesDefaultContinuationMessage() {
        MockChatService svc = MockChatService.returning("CONTINUE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("a"), ctxWithGoal("g"));

        assertTrue(d.isContinue());
        String msg = ((CompletionDecision.Continue) d).getMessage();
        assertNotNull(msg);
        assertFalse(msg.isEmpty(), "CONTINUE without remainder must use default continuation message");
        assertEquals(LlmJudgeConfig.DEFAULT_CONTINUATION_MESSAGE, msg);
    }

    @Test
    void escalateVerdictWithoutRemainderUsesDefaultContinuationMessage() {
        MockChatService svc = MockChatService.returning("ESCALATE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("a"), ctxWithGoal("g"));

        assertTrue(d.isEscalate());
        String reason = ((CompletionDecision.Escalate) d).getReason();
        assertNotNull(reason);
        assertFalse(reason.isEmpty(), "ESCALATE without remainder must use default continuation message as reason");
    }

    @Test
    void maxContextZeroProducesNoContextBlock() {
        MockChatService svc = MockChatService.returning("COMPLETE");
        LlmCompletionJudge judge = new LlmCompletionJudge(new LlmJudgeConfig(
                svc, null, null, null, null, null, 0, null));
        AgentExecutionContext ctx = ctxWithGoal("g");
        ctx.addMessage(new ChatUserMessage("should-not-appear"));

        judge.decide(msgWith("answer."), ctx);

        String userPrompt = extractUserPrompt(svc.lastRequest.get());
        assertFalse(userPrompt.contains("should-not-appear"),
                "maxContextMessages=0 must exclude all conversation context");
        assertFalse(userPrompt.contains("Conversation context"),
                "Context block header must be absent when no context included");
    }

    @Test
    void verdictWithLeadingBlankLinesParsesFirstNonEmptyLine() {
        MockChatService svc = MockChatService.returning("\n\n  \nCOMPLETE");
        LlmCompletionJudge judge = judge(svc);

        CompletionDecision d = judge.decide(msgWith("a"), ctxWithGoal("g"));

        assertTrue(d.isComplete(), "Leading blank lines must be skipped when locating the verdict keyword");
    }
}
