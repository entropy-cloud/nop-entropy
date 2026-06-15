package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 193 focused verification of secure-by-default Layer 1 wiring. Verifies
 * the five in-scope behaviour points:
 *
 * <ol>
 *   <li>Default engine (short constructor) denies deny-list tool ({@code bash}).</li>
 *   <li>Default engine denies sensitive path ({@code ~/.ssh/id_rsa}).</li>
 *   <li>Default engine allows safe tool / safe path to execute normally.</li>
 *   <li>Explicit {@code AllowAll*} triggers exactly one construction-time WARN.</li>
 *   <li>{@code Default*} (or default short constructor) does NOT trigger WARN.</li>
 * </ol>
 *
 * <p>Tests 1-2 are <b>end-to-end</b>: they go through the full path
 * {@code DefaultAgentEngine.execute(...) → doExecute → resolveExecutor →
 * ReActAgentExecutor.execute → tool dispatch → Layer 1 checker deny}
 * (Minimum Rules #22, #23 — wiring verification proving the default checker
 * reaches the ReAct loop, not just that the field type is correct).
 */
public class TestSecureByDefault {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ListAppender<ILoggingEvent> warnAppender;
    private Logger engineLogger;

    @BeforeEach
    void attachWarnAppender() {
        engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        warnAppender = new ListAppender<>();
        warnAppender.start();
        engineLogger.addAppender(warnAppender);
    }

    @AfterEach
    void detachWarnAppender() {
        if (engineLogger != null && warnAppender != null) {
            engineLogger.detachAppender(warnAppender);
            warnAppender.stop();
        }
    }

    private List<ILoggingEvent> warnEvents() {
        return warnAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    private IChatService chatServiceEmittingThenFinal(ChatToolCall firstTurnCall) {
        AtomicInteger n = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(build(request));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return build(request);
            }

            private ChatResponse build(ChatRequest request) {
                int turn = n.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (turn == 0) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(firstTurnCall));
                } else {
                    msg.setContent("done");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager recordingToolManager(AtomicBoolean toolInvoked) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolInvoked.set(true);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                return m;
            }
        };
    }

    private ChatToolCall toolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(args);
        return tc;
    }

    /**
     * Behaviour 1: default engine (short constructor) denies the deny-list
     * tool {@code bash} end-to-end — the call never reaches the tool manager,
     * and a deny tool-response message is produced.
     *
     * <p>End-to-end (Minimum Rules #22) + wiring verification (#23): proves
     * the {@code DefaultToolAccessChecker} field installed by the short
     * constructor actually reaches the ReAct dispatch loop and denies the
     * call — not just that the field type is correct.
     */
    @Test
    void defaultEngineDeniesDenyListToolEndToEnd() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "do risky work");
        // NOTE: we attach the bash tool call via a chat service that emits it
        // on the first turn. The test-agent DSL has no declared tools, but
        // the LLM-emitted tool call still flows through the Layer 1 tool
        // access checker before reaching the tool manager.
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-1", "bash", Map.of("cmd", "echo hi")));

        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        // Default engine: short constructor → Default* checkers (plan 193)
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        // Field-level assertion: short constructor installs Default*
        assertTrue(engine.getToolAccessCheckerForTest() instanceof DefaultToolAccessChecker,
                "Short constructor must install DefaultToolAccessChecker, got: "
                        + engine.getToolAccessCheckerForTest().getClass().getName());

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolInvoked.get(),
                "bash must NOT reach the tool manager — default checker must deny it end-to-end");

        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());
        assertFalse(toolResponses.isEmpty(),
                "A deny tool-response message must be produced for the denied bash call");
        ChatToolResponseMessage denyResp = toolResponses.get(0);
        assertNotNull(denyResp.getContent());
        assertTrue(denyResp.getContent().toLowerCase().contains("bash")
                        || denyResp.getContent().toLowerCase().contains("denied")
                        || denyResp.getContent().toLowerCase().contains("access"),
                "Deny response should identify the denied tool / access denial. Got: "
                        + denyResp.getContent());
    }

    /**
     * Behaviour 2: default engine denies a sensitive path ({@code ~/.ssh/id_rsa})
     * end-to-end. The {@code read-file} tool itself is NOT on the deny list,
     * but its {@code path} argument is sensitive — the {@code DefaultPathAccessChecker}
     * must reject it before the tool executes.
     */
    @Test
    void defaultEngineDeniesSensitivePathEndToEnd() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "read ssh key");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-1", "read-file", Map.of("path", "~/.ssh/id_rsa")));

        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        // Field-level assertion: short constructor installs Default*
        assertTrue(engine.getPathAccessCheckerForTest() instanceof DefaultPathAccessChecker,
                "Short constructor must install DefaultPathAccessChecker, got: "
                        + engine.getPathAccessCheckerForTest().getClass().getName());

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolInvoked.get(),
                "read-file with ~/.ssh/id_rsa must NOT reach the tool manager — "
                        + "default path checker must deny it end-to-end");

        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());
        assertFalse(toolResponses.isEmpty(),
                "A deny tool-response message must be produced for the denied sensitive path");
    }

    /**
     * Behaviour 3: default engine allows a safe tool ({@code read-file} with a
     * non-sensitive path) to execute normally. Proves the default checker does
     * NOT over-block safe operations.
     */
    @Test
    void defaultEngineAllowsSafeToolAndPath() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "read safe file");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-1", "read-file", Map.of("path", "/tmp/test-data.txt")));

        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolInvoked.get(),
                "read-file with a safe path (/tmp/test-data.txt) must reach the tool manager — "
                        + "default checker must not over-block safe operations");

        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());
        assertFalse(toolResponses.isEmpty(),
                "A success tool-response message must be produced for the allowed call");
    }

    /**
     * Behaviour 4: explicit {@code AllowAll*} checkers trigger exactly one
     * construction-time WARN per checker (two WARNs when both are AllowAll*).
     * Proves the WARN path is fail-loud (Minimum Rules #24 — no silent no-op,
     * no empty body, the WARN is actually emitted via the logger).
     */
    @Test
    void explicitAllowAllTriggersConstructionWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("x", "noop", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        // Before construction: capture WARN count baseline (should be empty)
        assertEquals(0, warnEvents().size(), "No WARN should be emitted before construction");

        // Explicit opt-in to AllowAll* for BOTH checkers
        @SuppressWarnings("resource")
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker());

        List<ILoggingEvent> warns = warnEvents();
        // Both AllowAll* instances → at least 2 WARNs (one per checker)
        assertTrue(warns.size() >= 2,
                "Explicit AllowAll* must emit at least 2 WARNs (one per checker). Got: " + warns.size());

        boolean toolWarnPresent = warns.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("AllowAllToolAccessChecker"));
        assertTrue(toolWarnPresent,
                "A WARN must identify AllowAllToolAccessChecker. Messages: "
                        + warns.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        boolean pathWarnPresent = warns.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("AllowAllPathAccessChecker"));
        assertTrue(pathWarnPresent,
                "A WARN must identify AllowAllPathAccessChecker. Messages: "
                        + warns.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // Sanity: the WARN text mentions the insecure-default nature + guidance
        boolean mentionsInsecure = warns.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && (e.getFormattedMessage().toLowerCase().contains("insecure")
                                || e.getFormattedMessage().toLowerCase().contains("not blocked")));
        assertTrue(mentionsInsecure,
                "WARN must mention the insecure / not-blocked nature of the default. Messages: "
                        + warns.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // Suppress unused-variable warning for engine — its construction is the side-effect under test
        assertEquals(AllowAllToolAccessChecker.class, engine.getToolAccessCheckerForTest().getClass());
    }

    /**
     * Behaviour 5: {@code Default*} checkers (whether passed explicitly or
     * installed by the short constructor) do NOT trigger any construction WARN.
     * Proves the WARN fires only on the AllowAll* downgrade, not on the secure
     * default.
     */
    @Test
    void defaultCheckersDoNotTriggerWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("x", "noop", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        assertEquals(0, warnEvents().size(), "Baseline: no WARN before construction");

        // Short constructor → Default* installed internally
        @SuppressWarnings("resource")
        DefaultAgentEngine engineShort = new DefaultAgentEngine(chatService, toolManager);

        List<ILoggingEvent> warnsAfterShort = warnEvents();
        assertEquals(0, warnsAfterShort.size(),
                "Short constructor (Default*) must NOT emit any WARN. Got: "
                        + warnsAfterShort.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
        assertTrue(engineShort.getToolAccessCheckerForTest() instanceof DefaultToolAccessChecker);
        assertTrue(engineShort.getPathAccessCheckerForTest() instanceof DefaultPathAccessChecker);

        // Explicit Default* via 6-arg constructor — also no WARN
        @SuppressWarnings("resource")
        DefaultAgentEngine engineExplicit = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DefaultToolAccessChecker(),
                new DefaultPathAccessChecker());

        List<ILoggingEvent> warnsAfterExplicit = warnEvents();
        assertEquals(0, warnsAfterExplicit.size(),
                "Explicit Default* must NOT emit any WARN. Got: "
                        + warnsAfterExplicit.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // Single AllowAll (tool only, Default path) — exactly 1 WARN (not 2)
        @SuppressWarnings("resource")
        DefaultAgentEngine engineMixed = new DefaultAgentEngine(chatService, toolManager,
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new DefaultPathAccessChecker());

        List<ILoggingEvent> warnsAfterMixed = warnEvents();
        assertEquals(1, warnsAfterMixed.size(),
                "Mixed (AllowAll tool + Default path) must emit exactly 1 WARN. Got: "
                        + warnsAfterMixed.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    /**
     * Bonus: {@code ReActAgentExecutor.Builder.build()} null-fallback
     * consistency — when no checker is supplied to the builder, the resulting
     * executor uses {@code Default*} (not AllowAll*). Proves the builder
     * default is consistent with the engine default (plan 193 in-scope item).
     */
    @Test
    void builderNullFallbackUsesDefaultCheckers() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        ChatToolCall toolCall = toolCall("c1", "bash", Map.of("cmd", "echo hi"));
        AtomicInteger n = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest req, ICancelToken cancelToken) {
                int turn = n.getAndIncrement();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (turn == 0) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                } else {
                    msg.setContent("done");
                }
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest req, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        // Builder with NO toolAccessChecker/pathAccessChecker — null fallback
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertFalse(toolInvoked.get(),
                "Builder null-fallback must use DefaultToolAccessChecker — bash must be denied end-to-end, "
                        + "proving the builder default is consistent with the engine default");
    }
}
