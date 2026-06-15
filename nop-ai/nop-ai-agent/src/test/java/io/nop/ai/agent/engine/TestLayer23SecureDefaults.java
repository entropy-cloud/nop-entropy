package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 199 focused verification of Layer 2/3 secure-defaults convergence.
 * Verifies the five in-scope behaviour points required by the plan:
 *
 * <ol>
 *   <li><b>DefaultApprovalGate is the engine default</b>: a default engine
 *       (short constructor) uses {@link DefaultApprovalGate}, not
 *       {@link AutoApproveGate}.</li>
 *   <li><b>WARN-on-AutoApproveGate-setter</b>: {@code setApprovalGate(
 *       AutoApproveGate.autoApprove())} triggers a WARN identifying the
 *       downgrade.</li>
 *   <li><b>WARN-on-4-NoOp/PassThrough-setters</b>: explicitly injecting
 *       NoOp/PassThrough instances via setters triggers WARN for each
 *       component.</li>
 *   <li><b>no-WARN-on-default-construction</b>: default construction does NOT
 *       trigger WARN for the 4 unchanged-insecure-default components (noise
 *       control — their defaults are intentionally NoOp/PassThrough).</li>
 *   <li><b>no-WARN-on-functional-setters</b>: injecting functional
 *       implementations via setters does NOT trigger WARN.</li>
 *   <li><b>end-to-end RESTRICTED denied (defense-in-depth)</b>: from
 *       {@code new DefaultAgentEngine(...)}, with a functional resolver
 *       returning RESTRICTED, the default {@link DefaultApprovalGate} denies
 *       the RESTRICTED operation at the approval gate.</li>
 *   <li><b>end-to-end RESTRICTED audit event</b>: the denial records an
 *       {@link AuditEvent} with matched rule {@code "layer3_approval_gate"}.</li>
 * </ol>
 *
 * <p>Tests 6-7 are <b>end-to-end</b> + <b>wiring verification</b>
 * (Minimum Rules #22, #23): they go through the full path
 * {@code DefaultAgentEngine.execute(...) → doExecute → resolveExecutor →
 * ReActAgentExecutor.execute → tool dispatch → Layer 2 resolver →
 * Layer 3 DefaultApprovalGate deny → auditLogger.log(...)}, proving the
 * tightened approval gate actually reaches the ReAct dispatch loop and
 * blocks RESTRICTED operations — not just that the type is correct.
 */
public class TestLayer23SecureDefaults {

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
    void attachAppender() {
        engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        warnAppender = new ListAppender<>();
        warnAppender.start();
        engineLogger.addAppender(warnAppender);
    }

    @AfterEach
    void detachAppender() {
        if (engineLogger != null && warnAppender != null) {
            engineLogger.detachAppender(warnAppender);
            warnAppender.stop();
        }
    }

    private List<ILoggingEvent> warns() {
        return warnAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    private long countWarnsMentioning(String keyword) {
        return warns().stream()
                .filter(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains(keyword))
                .count();
    }

    // ========================================================================
    // Mocks (following TestAuditLoggerDefault patterns)
    // ========================================================================

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
     * Functional resolver: {@code network.fetch} → RESTRICTED, everything else
     * → STANDARD. Isolates the Layer 3 gate test (PassThrough matrix allows
     * all, so only the approval gate decides).
     */
    static final class RestrictedResolver implements ISecurityLevelResolver {
        @Override
        public SecurityLevel resolve(String actionKind, LevelHints hints) {
            String kind = actionKind == null ? "" : actionKind.replace('_', '.').toLowerCase(Locale.ROOT);
            if ("network.fetch".equals(kind)) {
                return SecurityLevel.RESTRICTED;
            }
            return SecurityLevel.STANDARD;
        }
    }

    /**
     * Functional resolver that always returns STANDARD (non-NoOp, used to
     * verify no-WARN-on-functional-setter).
     */
    static final class StandardResolver implements ISecurityLevelResolver {
        @Override
        public SecurityLevel resolve(String actionKind, LevelHints hints) {
            return SecurityLevel.STANDARD;
        }
    }

    /**
     * Collecting audit logger for end-to-end audit verification.
     */
    static final class CollectingAuditLogger implements IAuditLogger {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void log(AuditEvent event) {
            events.add(event);
        }

        boolean hasDenialFor(String toolName, String matchedRule) {
            for (AuditEvent e : events) {
                if (e.getDecision() == AuditDecision.DENY
                        && toolName.equals(e.getToolName())
                        && matchedRule.equals(e.getMatchedRule())) {
                    return true;
                }
            }
            return false;
        }
    }

    // A functional IPermissionMatrix stub (non-PassThrough) for no-WARN tests
    private static final io.nop.ai.agent.security.IPermissionMatrix FUNCTIONAL_MATRIX =
            (channel, principal, level) -> io.nop.ai.agent.security.MatrixDecision.allow();
    // A functional IDenialLedger stub (non-NoOp) for no-WARN tests
    private static final io.nop.ai.agent.security.IDenialLedger FUNCTIONAL_LEDGER =
            new io.nop.ai.agent.security.IDenialLedger() {
                @Override
                public io.nop.ai.agent.security.DenialRecordOutcome recordDenial(io.nop.ai.agent.security.DenialRecord record) {
                    return io.nop.ai.agent.security.DenialRecordOutcome.of(0, false);
                }

                @Override
                public boolean isPaused(String sessionId) {
                    return false;
                }

                @Override
                public int getDenialCount(String sessionId) {
                    return 0;
                }

                @Override
                public void reset(String sessionId) {
                }
            };
    // A functional IPostDenialGuard stub (non-PassThrough) for no-WARN tests
    private static final io.nop.ai.agent.security.IPostDenialGuard FUNCTIONAL_GUARD =
            new io.nop.ai.agent.security.IPostDenialGuard() {
                @Override
                public io.nop.ai.agent.security.DenialResult checkBeforeDispatch(
                        String sessionId, String toolName, Map<String, Object> arguments, String workDir) {
                    return null;
                }

                @Override
                public void recordDeniedAction(
                        String sessionId, String toolName, Map<String, Object> arguments, String workDir) {
                }

                @Override
                public void reset(String sessionId) {
                }
            };

    // ========================================================================
    // Behaviour 1: DefaultApprovalGate is the engine default
    // ========================================================================

    @Test
    void defaultEngineUsesDefaultApprovalGate() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        assertTrue(engine.getApprovalGate() instanceof DefaultApprovalGate,
                "Short constructor must install DefaultApprovalGate, got: "
                        + engine.getApprovalGate().getClass().getName());
        assertFalse(engine.getApprovalGate() instanceof AutoApproveGate,
                "Short constructor must NOT install AutoApproveGate (plan 199 changed the default)");
    }

    // ========================================================================
    // Behaviour 2: WARN on AutoApproveGate setter injection
    // ========================================================================

    @Test
    void setAutoApproveGateTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        // Default construction — no AutoApproveGate WARN
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        assertEquals(0, countWarnsMentioning("AutoApproveGate"),
                "Default construction must NOT emit AutoApproveGate WARN. Got: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // Setter injection of AutoApproveGate → WARN
        engine.setApprovalGate(AutoApproveGate.autoApprove());

        assertTrue(countWarnsMentioning("AutoApproveGate") >= 1,
                "setApprovalGate(AutoApproveGate) must emit a WARN identifying the downgrade. Messages: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    // ========================================================================
    // Behaviour 3: WARN on 4 NoOp/PassThrough setter injections
    // ========================================================================

    @Test
    void setNoOpResolverTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        engine.setSecurityLevelResolver(NoOpSecurityLevelResolver.noOp());

        assertTrue(countWarnsMentioning("NoOpSecurityLevelResolver") >= 1,
                "setSecurityLevelResolver(NoOp) must emit a WARN. Messages: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    @Test
    void setPassThroughMatrixTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        engine.setPermissionMatrix(PassThroughPermissionMatrix.passThrough());

        assertTrue(countWarnsMentioning("PassThroughPermissionMatrix") >= 1,
                "setPermissionMatrix(PassThrough) must emit a WARN. Messages: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    @Test
    void setNoOpDenialLedgerTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        engine.setDenialLedger(NoOpDenialLedger.noOp());

        assertTrue(countWarnsMentioning("NoOpDenialLedger") >= 1,
                "setDenialLedger(NoOp) must emit a WARN. Messages: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    @Test
    void setPassThroughPostDenialGuardTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        engine.setPostDenialGuard(PassThroughPostDenialGuard.passThrough());

        assertTrue(countWarnsMentioning("PassThroughPostDenialGuard") >= 1,
                "setPostDenialGuard(PassThrough) must emit a WARN. Messages: "
                        + warns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    // ========================================================================
    // Behaviour 4: no-WARN on default construction (noise control)
    // ========================================================================

    @Test
    void defaultConstructionProducesNoWarnForUnchangedInsecureDefaults() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        // The 4 unchanged-insecure-default components must NOT trigger WARN
        // at construction time (noise control — their defaults are
        // intentionally NoOp/PassThrough).
        assertEquals(0, countWarnsMentioning("NoOpSecurityLevelResolver"),
                "Default construction must NOT warn about NoOpSecurityLevelResolver");
        assertEquals(0, countWarnsMentioning("PassThroughPermissionMatrix"),
                "Default construction must NOT warn about PassThroughPermissionMatrix");
        assertEquals(0, countWarnsMentioning("NoOpDenialLedger"),
                "Default construction must NOT warn about NoOpDenialLedger");
        assertEquals(0, countWarnsMentioning("PassThroughPostDenialGuard"),
                "Default construction must NOT warn about PassThroughPostDenialGuard");
        // AutoApproveGate also must NOT warn (default is now DefaultApprovalGate)
        assertEquals(0, countWarnsMentioning("AutoApproveGate"),
                "Default construction must NOT warn about AutoApproveGate (default is DefaultApprovalGate)");
    }

    // ========================================================================
    // Behaviour 5: no-WARN on functional setter injections
    // ========================================================================

    @Test
    void functionalSettersDoNotTriggerWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(toolCall("x", "echo", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        // Functional implementations must NOT trigger WARN
        engine.setSecurityLevelResolver(new StandardResolver());
        engine.setPermissionMatrix(FUNCTIONAL_MATRIX);
        engine.setDenialLedger(FUNCTIONAL_LEDGER);
        engine.setPostDenialGuard(FUNCTIONAL_GUARD);
        engine.setApprovalGate(new DefaultApprovalGate());

        assertEquals(0, countWarnsMentioning("NoOpSecurityLevelResolver"),
                "Functional resolver must NOT trigger WARN");
        assertEquals(0, countWarnsMentioning("PassThroughPermissionMatrix"),
                "Functional matrix must NOT trigger WARN");
        assertEquals(0, countWarnsMentioning("NoOpDenialLedger"),
                "Functional ledger must NOT trigger WARN");
        assertEquals(0, countWarnsMentioning("PassThroughPostDenialGuard"),
                "Functional guard must NOT trigger WARN");
        assertEquals(0, countWarnsMentioning("AutoApproveGate"),
                "DefaultApprovalGate must NOT trigger AutoApproveGate WARN");
    }

    // ========================================================================
    // Behaviour 6: end-to-end RESTRICTED denied (defense-in-depth)
    // ========================================================================

    /**
     * End-to-end + wiring verification (Minimum Rules #22, #23): from
     * {@code new DefaultAgentEngine(...)}, with a functional resolver
     * returning RESTRICTED for {@code network.fetch}, through the ReAct loop,
     * to the default {@link DefaultApprovalGate} denying the RESTRICTED
     * operation at the Layer 3 approval gate. The tool must NOT be invoked
     * (the gate blocks it before dispatch), and an "Approval denied" tool
     * response must be produced.
     */
    @Test
    void endToEndRestrictedDeniedByDefaultApprovalGate() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "fetch data");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-1", "network.fetch", Map.of("url", "http://example.com")));
        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        // Inject a functional resolver that returns RESTRICTED for network.fetch.
        // DefaultApprovalGate (engine default) denies RESTRICTED.
        // Use PassThroughPermissionMatrix to isolate the Layer 3 approval gate
        // (DefaultPermissionMatrix would deny RESTRICTED at Layer 2 first for
        // null channel — this test specifically verifies the Layer 3 path).
        engine.setSecurityLevelResolver(new RestrictedResolver());
        engine.setPermissionMatrix(PassThroughPermissionMatrix.passThrough());

        assertTrue(engine.getApprovalGate() instanceof DefaultApprovalGate,
                "Engine must use DefaultApprovalGate by default");

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolInvoked.get(),
                "network.fetch (RESTRICTED) must NOT reach the tool manager — "
                        + "DefaultApprovalGate must deny it end-to-end (defense-in-depth)");

        // An "Approval denied" tool response must be produced
        boolean hasApprovalDenied = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(tr -> tr.getContent() != null && tr.getContent().contains("Approval denied"));
        assertTrue(hasApprovalDenied,
                "DefaultApprovalGate must produce an 'Approval denied' tool response for RESTRICTED");
    }

    // ========================================================================
    // Behaviour 7: end-to-end RESTRICTED audit event
    // ========================================================================

    /**
     * End-to-end + wiring verification + Anti-Hollow check: the RESTRICTED
     * denial by the default {@link DefaultApprovalGate} records an
     * {@link AuditEvent} with decision DENY and matched rule
     * {@code "layer3_approval_gate"}, proving the full dispatch path is
     * connected from engine entry to audit logger.
     */
    @Test
    void endToEndRestrictedDenialRecordsAuditEvent() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "fetch data");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-audit", "network.fetch", Map.of("url", "http://example.com")));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setSecurityLevelResolver(new RestrictedResolver());
        engine.setPermissionMatrix(PassThroughPermissionMatrix.passThrough());
        CollectingAuditLogger audit = new CollectingAuditLogger();
        engine.setAuditLogger(audit);

        engine.execute(request).toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertTrue(audit.hasDenialFor("network.fetch", "layer3_approval_gate"),
                "End-to-end audit must record a DENY audit event for network.fetch (RESTRICTED) "
                        + "with matched rule 'layer3_approval_gate'. Events: " + audit.events);
    }
}
