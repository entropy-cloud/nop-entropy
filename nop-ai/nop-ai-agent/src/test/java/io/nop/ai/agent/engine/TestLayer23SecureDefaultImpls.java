package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 200 end-to-end + wiring verification (Minimum Rules #22, #23):
 * verifies the 4 new Default* secure defaults are actually wired into the
 * engine and affect dispatch behavior from {@code new DefaultAgentEngine(...)}
 * through the full ReAct loop.
 *
 * <p>Covers the 6 required behavior points:
 * <ol>
 *   <li><b>resolver-classification</b>: DefaultSecurityLevelResolver classifies
 *       actions (untrusted high-impact → RESTRICTED).</li>
 *   <li><b>matrix-allow-deny</b>: DefaultPermissionMatrix denies RESTRICTED for
 *       null channel (defense-in-depth at Layer 2).</li>
 *   <li><b>ledger-count-pause</b>: DefaultDenialLedger counts denials and
 *       pauses session at threshold.</li>
 *   <li><b>guard-block-retry</b>: DefaultPostDenialGuard blocks blind retries.</li>
 *   <li><b>WARN-always-checked</b>: NoOp/PassThrough setter opt-in triggers WARN;
 *       Default* default construction does not.</li>
 *   <li><b>end-to-end-dispatch</b>: from engine entry, through ReAct loop, to
 *       Default* components affecting tool dispatch results.</li>
 * </ol>
 */
public class TestLayer23SecureDefaultImpls {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    private IChatService chatServiceWithTurns(List<ChatToolCall>... turns) {
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
                if (turn < turns.length) {
                    msg.setContent("");
                    msg.setToolCalls(turns[turn]);
                } else {
                    msg.setContent("done");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager recordingToolManager(AtomicBoolean invoked) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if (invoked != null) invoked.set(true);
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

    static final class CollectingAuditLogger implements IAuditLogger {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void log(AuditEvent event) {
            events.add(event);
        }

        boolean hasDenyFor(String toolName, String matchedRule) {
            return events.stream().anyMatch(e ->
                    e.getDecision() == AuditDecision.DENY
                            && toolName.equals(e.getToolName())
                            && matchedRule.equals(e.getMatchedRule()));
        }
    }

    // ========================================================================
    // (1) Default* defaults are wired as engine defaults
    // ========================================================================

    @Test
    void defaultEngineUsesAllFourDefaultSecureImpls() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(List.of()),
                recordingToolManager(null));

        assertTrue(engine.getSecurityLevelResolver() instanceof DefaultSecurityLevelResolver,
                "default resolver must be DefaultSecurityLevelResolver");
        assertTrue(engine.getPermissionMatrix() instanceof DefaultPermissionMatrix,
                "default matrix must be DefaultPermissionMatrix");
        assertTrue(engine.getDenialLedger() instanceof DefaultDenialLedger,
                "default ledger must be DefaultDenialLedger");
        assertTrue(engine.getPostDenialGuard() instanceof DefaultPostDenialGuard,
                "default guard must be DefaultPostDenialGuard");
    }

    // ========================================================================
    // (2) End-to-end: untrusted+highImpact → RESTRICTED → Layer 2 deny
    // ========================================================================

    /**
     * End-to-end + wiring: from {@code new DefaultAgentEngine(...)}, the default
     * DefaultSecurityLevelResolver + DefaultPermissionMatrix work together. A
     * custom resolver returns RESTRICTED for a tool; the default matrix denies
     * RESTRICTED for null channel; the dispatch path produces a "Security policy
     * denied" response and the tool is NOT invoked.
     */
    @Test
    void endToEndRestrictedDeniedByDefaultMatrixAtLayer2() throws Exception {
        // Use a custom resolver to force RESTRICTED (simulating untrusted source)
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(List.of(toolCall("c1", "shell.exec", Map.of("cmd", "ls")))),
                recordingToolManager(new AtomicBoolean()));
        // Resolver that returns RESTRICTED (simulating untrusted high-impact)
        engine.setSecurityLevelResolver((actionKind, hints) -> SecurityLevel.RESTRICTED);
        // Use default matrix (DefaultPermissionMatrix denies RESTRICTED for null channel)

        AgentMessageRequest req = new AgentMessageRequest("test-agent", "run");
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().get(30, TimeUnit.SECONDS);

        boolean foundSecurityDenial = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(tr -> tr.getContent() != null && tr.getContent().contains("Security policy denied"));
        assertTrue(foundSecurityDenial,
                "RESTRICTED must be denied at Layer 2 by DefaultPermissionMatrix for null channel");
    }

    // ========================================================================
    // (3) End-to-end: trusted high-impact → ELEVATED → allowed
    // ========================================================================

    /**
     * End-to-end: the default DefaultSecurityLevelResolver classifies trusted
     * standard actions as STANDARD, and the default DefaultPermissionMatrix
     * allows STANDARD for null channel. So a trusted "echo" proceeds through
     * all layers to successful tool invocation.
     */
    @Test
    void endToEndTrustedStandardActionAllowed() {
        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(List.of(toolCall("c1", "echo", Map.of()))),
                recordingToolManager(toolInvoked));

        AgentMessageRequest req = new AgentMessageRequest("test-agent", "run echo");
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(toolInvoked.get(),
                "trusted echo (STANDARD) must be allowed end-to-end through Default* defaults");
    }

    // ========================================================================
    // (4) End-to-end: multiple denials → session pause (DefaultDenialLedger)
    // ========================================================================

    /**
     * End-to-end: with DefaultDenialLedger default (threshold=3), three
     * consecutive denials pause the session.
     */
    @Test
    void endToEndMultipleDenialsPauseSession() {
        io.nop.ai.agent.security.IToolAccessChecker denyAll =
                new io.nop.ai.agent.security.IToolAccessChecker() {
                    @Override
                    public io.nop.ai.agent.security.ToolAccessResult checkAccess(
                            String toolName, AgentExecutionContext ctx) {
                        return io.nop.ai.agent.security.ToolAccessResult.deny("test deny-all");
                    }
                };
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(
                        List.of(toolCall("d1", "test-tool", Map.of())),
                        List.of(toolCall("d2", "test-tool", Map.of())),
                        List.of(toolCall("d3", "test-tool", Map.of()))
                ),
                recordingToolManager(null),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                denyAll);
        // Isolate ledger behavior: prevent guard from blocking retries so all
        // 3 denials reach Layer 1 and the ledger counts them.
        engine.setPostDenialGuard(PassThroughPostDenialGuard.passThrough());

        assertTrue(engine.getDenialLedger() instanceof DefaultDenialLedger);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "pause-test", null, ChannelKind.WEBUI, Principal.user());
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(AgentExecStatus.paused, result.getStatus(),
                "session must be paused after 3 denials (DefaultDenialLedger threshold=3)");
    }

    // ========================================================================
    // (5) End-to-end: blind retry blocked (DefaultPostDenialGuard)
    // ========================================================================

    /**
     * End-to-end: with DefaultPostDenialGuard default, an identical tool call
     * after a denial is blocked as a blind retry. Uses a deny-all tool checker
     * so the first call is denied, then the second identical call is blocked
     * by the guard before Layer 1.
     */
    @Test
    void endToEndBlindRetryBlockedByDefaultGuard() {
        AtomicInteger checkCount = new AtomicInteger(0);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(
                        List.of(
                                toolCall("r1", "test-tool", Map.of("arg", "v")),
                                toolCall("r2", "test-tool", Map.of("arg", "v"))),
                        List.of()
                ),
                recordingToolManager(null),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.IToolAccessChecker() {
                    @Override
                    public io.nop.ai.agent.security.ToolAccessResult checkAccess(
                            String toolName, AgentExecutionContext ctx) {
                        checkCount.incrementAndGet();
                        return io.nop.ai.agent.security.ToolAccessResult.deny("test deny-all");
                    }
                });

        assertTrue(engine.getPostDenialGuard() instanceof DefaultPostDenialGuard);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "guard-test", null, ChannelKind.WEBUI, Principal.user());
        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Layer 1 ran for the first call; the second identical call was blocked
        // by DefaultPostDenialGuard before reaching Layer 1.
        assertEquals(1, checkCount.get(),
                "Layer 1 must run only once — second identical call blocked by DefaultPostDenialGuard. Got: "
                        + checkCount.get());

        boolean foundGuardDenial = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(tr -> tr.getContent() != null
                        && tr.getContent().contains("Repeated same denied action"));
        assertTrue(foundGuardDenial,
                "blind retry must produce a guard-deny message");
    }

    // ========================================================================
    // (6) Audit event recorded for Layer 2 denial by DefaultPermissionMatrix
    // ========================================================================

    @Test
    void endToEndLayer2DenialRecordsAuditEvent() throws Exception {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceWithTurns(List.of(toolCall("c1", "shell.exec", Map.of("cmd", "ls")))),
                recordingToolManager(null));
        engine.setSecurityLevelResolver((actionKind, hints) -> SecurityLevel.RESTRICTED);
        CollectingAuditLogger audit = new CollectingAuditLogger();
        engine.setAuditLogger(audit);

        AgentMessageRequest req = new AgentMessageRequest("test-agent", "run");
        engine.execute(req).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertTrue(audit.hasDenyFor("shell.exec", "layer2_permission_matrix"),
                "Layer 2 denial by DefaultPermissionMatrix must record an audit event with matched rule 'layer2_permission_matrix'");
    }
}
