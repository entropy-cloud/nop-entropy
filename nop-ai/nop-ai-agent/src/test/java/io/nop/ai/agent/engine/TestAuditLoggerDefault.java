package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.agent.security.Slf4jAuditLogger;
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
 * Plan 194 (AUDIT-13-02) focused verification of audit-logger secure-by-default
 * wiring. Verifies the five in-scope behaviour points:
 *
 * <ol>
 *   <li><b>default-audit-to-SLF4j</b>: a default engine (short constructor)
 *       produces a visible SLF4j INFO audit log entry when a tool decision
 *       occurs (deny-list tool {@code bash} is denied end-to-end).</li>
 *   <li><b>setter-replaces-logger</b>: {@code setAuditLogger(custom)} makes the
 *       engine use the custom logger instead of the default
 *       {@code Slf4jAuditLogger}.</li>
 *   <li><b>WARN-on-NoOp</b>: {@code setAuditLogger(new NoOpAuditLogger())}
 *       triggers exactly one construction-time WARN identifying the NoOp
 *       downgrade (the constructor-time field defaults to Slf4j, so the
 *       constructor-time check never hits NoOp on the shipped default — the
 *       setter is the actual hit-path).</li>
 *   <li><b>no-WARN-on-Slf4j</b>: default construction or
 *       {@code setAuditLogger(new Slf4jAuditLogger())} does NOT trigger the
 *       NoOp audit WARN.</li>
 *   <li><b>end-to-end-audit-event</b>: from {@code new DefaultAgentEngine(...)},
 *       through the ReAct loop, to a tool being denied, an
 *       {@link AuditEvent} with the correct decision (DENY), tool name
 *       ({@code bash}) and matched rule ({@code hardcoded_deny_list}) is
 *       recorded.</li>
 * </ol>
 *
 * <p>Tests 1 and 5 are <b>end-to-end</b> + <b>wiring verification</b>
 * (Minimum Rules #22, #23): they go through the full path
 * {@code DefaultAgentEngine.execute(...) → doExecute → resolveExecutor →
 * ReActAgentExecutor.execute → tool dispatch → Layer 1 checker deny →
 * auditLogger.log(...)}, proving the default audit logger actually reaches
 * the ReAct dispatch loop and records the decision — not just that the field
 * type is correct.
 */
public class TestAuditLoggerDefault {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ListAppender<ILoggingEvent> engineWarnAppender;
    private Logger engineLogger;
    private ListAppender<ILoggingEvent> slf4jAuditAppender;
    private Logger slf4jAuditLogger;

    @BeforeEach
    void attachAppenders() {
        engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        engineWarnAppender = new ListAppender<>();
        engineWarnAppender.start();
        engineLogger.addAppender(engineWarnAppender);

        slf4jAuditLogger = (Logger) LoggerFactory.getLogger(Slf4jAuditLogger.class);
        slf4jAuditAppender = new ListAppender<>();
        slf4jAuditAppender.start();
        slf4jAuditLogger.addAppender(slf4jAuditAppender);
    }

    @AfterEach
    void detachAppenders() {
        if (engineLogger != null && engineWarnAppender != null) {
            engineLogger.detachAppender(engineWarnAppender);
            engineWarnAppender.stop();
        }
        if (slf4jAuditLogger != null && slf4jAuditAppender != null) {
            slf4jAuditLogger.detachAppender(slf4jAuditAppender);
            slf4jAuditAppender.stop();
        }
    }

    private List<ILoggingEvent> engineWarns() {
        return engineWarnAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    private List<ILoggingEvent> slf4jAuditInfos() {
        return slf4jAuditAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
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
     * Collecting audit logger: records every event so tests can assert on the
     * decision / tool name / matched rule of an end-to-end audit trail.
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

    // ========================================================================
    // Behaviour 1: default engine produces SLF4j audit log
    // ========================================================================

    /**
     * Behaviour 1 (default-audit-to-SLF4j): a default engine (short
     * constructor) produces a visible SLF4j INFO audit log entry when a
     * deny-list tool ({@code bash}) is denied end-to-end.
     *
     * <p>End-to-end + wiring verification (Minimum Rules #22, #23): the
     * default {@code Slf4jAuditLogger} field installed by the short
     * constructor reaches the ReAct dispatch loop and records the deny — not
     * just that the field type is correct.
     */
    @Test
    void defaultEngineProducesSlf4jAuditLog() throws Exception {
        slf4jAuditInfos().clear();
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "do risky work");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-1", "bash", Map.of("cmd", "echo hi")));
        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        IToolManager toolManager = recordingToolManager(toolInvoked);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        // Field-level assertion: short constructor installs Slf4jAuditLogger
        assertTrue(engine.getAuditLogger() instanceof Slf4jAuditLogger,
                "Short constructor must install Slf4jAuditLogger, got: "
                        + engine.getAuditLogger().getClass().getName());

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(toolInvoked.get(),
                "bash must NOT reach the tool manager — default checker must deny it end-to-end");

        // The Slf4jAuditLogger INFO audit trail must contain at least one
        // AUDIT entry for the denied bash call (wiring verification).
        List<ILoggingEvent> auditInfos = slf4jAuditInfos();
        assertFalse(auditInfos.isEmpty(),
                "Default engine must produce a visible SLF4j INFO audit log entry for the bash deny");
        boolean hasBashDenyAudit = auditInfos.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("AUDIT|")
                        && e.getFormattedMessage().contains("DENY")
                        && e.getFormattedMessage().toLowerCase().contains("bash"));
        assertTrue(hasBashDenyAudit,
                "SLF4j audit trail must contain a DENY audit entry for bash. Messages: "
                        + auditInfos.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    // ========================================================================
    // Behaviour 2: setter replaces the default logger
    // ========================================================================

    /**
     * Behaviour 2 (setter-replaces-logger): after
     * {@code setAuditLogger(customLogger)}, the engine uses the custom logger
     * instead of the default {@code Slf4jAuditLogger}. Verified both at the
     * field level and end-to-end (the custom logger captures the audit event
     * that the default Slf4j logger would otherwise have logged).
     */
    @Test
    void setAuditLoggerReplacesDefault() throws Exception {
        slf4jAuditInfos().clear();
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "do risky work");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-2", "bash", Map.of("cmd", "echo hi")));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        CollectingAuditLogger custom = new CollectingAuditLogger();
        engine.setAuditLogger(custom);

        assertTrue(engine.getAuditLogger() == custom,
                "setAuditLogger must replace the default — getAuditLogger() must return the custom instance");

        engine.execute(request).toCompletableFuture().get(30, TimeUnit.SECONDS);

        // The custom logger captured the deny audit event end-to-end.
        assertTrue(custom.hasDenialFor("bash", "hardcoded_deny_list"),
                "Custom audit logger must capture the bash deny audit event end-to-end. Events: "
                        + custom.events);

        // The default Slf4jAuditLogger did NOT receive the event (the custom
        // logger replaced it, not composed with it).
        boolean slf4jGotBashDeny = slf4jAuditInfos().stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().toLowerCase().contains("bash"));
        assertFalse(slf4jGotBashDeny,
                "Default Slf4jAuditLogger must NOT receive the event after setAuditLogger replaced it");
    }

    // ========================================================================
    // Behaviour 3: WARN-on-NoOpAuditLogger (via setter)
    // ========================================================================

    /**
     * Behaviour 3 (WARN-on-NoOp): {@code setAuditLogger(new NoOpAuditLogger())}
     * triggers exactly one WARN identifying the NoOp downgrade. The
     * constructor-time field defaults to {@code Slf4jAuditLogger}, so the
     * constructor-time check never hits NoOp on the shipped default — the
     * setter is the actual hit-path for a NoOp downgrade (the core of "make
     * audit downgrade visible rather than silent").
     *
     * <p>Fail-loud (Minimum Rules #24): the WARN is actually emitted via
     * {@code LOG.warn} — no empty body, no swallowed check.
     */
    @Test
    void setNoOpAuditLoggerTriggersWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("x", "noop", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        // Short constructor → default Slf4jAuditLogger → no NoOp WARN at
        // construction time (defence-in-depth: the constructor-time check
        // does not hit NoOp on the shipped default).
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        assertEquals(0, engineWarns().size(),
                "Default construction must NOT emit any WARN (field defaults to Slf4jAuditLogger). Got: "
                        + engineWarns().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // The actual NoOp hit-path: setter injection of NoOpAuditLogger.
        engine.setAuditLogger(new NoOpAuditLogger());

        List<ILoggingEvent> warns = engineWarns();
        assertFalse(warns.isEmpty(),
                "setAuditLogger(new NoOpAuditLogger()) must emit a WARN identifying the NoOp downgrade");

        boolean noOpWarnPresent = warns.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("NoOpAuditLogger"));
        assertTrue(noOpWarnPresent,
                "WARN must identify NoOpAuditLogger. Messages: "
                        + warns.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));

        // Sanity: the WARN text mentions the audit-discarding nature + guidance.
        boolean mentionsDiscardedOrGuidance = warns.stream()
                .anyMatch(e -> e.getFormattedMessage() != null
                        && (e.getFormattedMessage().toLowerCase().contains("discard")
                                || e.getFormattedMessage().toLowerCase().contains("no record")
                                || e.getFormattedMessage().toLowerCase().contains("Slf4jAuditLogger")));
        assertTrue(mentionsDiscardedOrGuidance,
                "WARN must mention the audit-discarding nature / how to restore. Messages: "
                        + warns.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    // ========================================================================
    // Behaviour 4: no-WARN-on-Slf4j (default + explicit)
    // ========================================================================

    /**
     * Behaviour 4 (no-WARN-on-Slf4j): default construction and
     * {@code setAuditLogger(new Slf4jAuditLogger())} do NOT trigger the NoOp
     * audit WARN. Proves the WARN fires only on the NoOp downgrade, not on the
     * secure default.
     */
    @Test
    void slf4jAuditLoggerDoesNotTriggerWarn() {
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("x", "noop", Map.of()));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        assertEquals(0, engineWarns().size(), "Baseline: no WARN before construction");

        // Short constructor → default Slf4jAuditLogger → no NoOp WARN
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        long warnsAfterDefault = engineWarns().stream()
                .filter(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("NoOpAuditLogger"))
                .count();
        assertEquals(0, warnsAfterDefault,
                "Default construction (Slf4jAuditLogger) must NOT emit a NoOpAuditLogger WARN");

        // Explicit Slf4jAuditLogger via setter → still no NoOp WARN
        engine.setAuditLogger(new Slf4jAuditLogger());
        long warnsAfterExplicit = engineWarns().stream()
                .filter(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains("NoOpAuditLogger"))
                .count();
        assertEquals(0, warnsAfterExplicit,
                "setAuditLogger(new Slf4jAuditLogger()) must NOT emit a NoOpAuditLogger WARN");
    }

    // ========================================================================
    // Behaviour 5: end-to-end audit event (Anti-Hollow)
    // ========================================================================

    /**
     * Behaviour 5 (end-to-end-audit-event): from
     * {@code new DefaultAgentEngine(...)}, through the ReAct loop, to a
     * deny-list tool being denied, an {@link AuditEvent} with the correct
     * decision (DENY), tool name ({@code bash}) and matched rule
     * ({@code hardcoded_deny_list}) is recorded.
     *
     * <p>End-to-end (Minimum Rules #22) + wiring verification (#23) +
     * Anti-Hollow check: the full path from the engine entry point through
     * the ReAct loop to the audit logger is connected and the recorded event
     * carries the semantically-correct fields (not just a no-op log call).
     */
    @Test
    void endToEndAuditEventFromEngineEntry() throws Exception {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "do risky work");
        IChatService chatService = chatServiceEmittingThenFinal(
                toolCall("call-e2e", "bash", Map.of("cmd", "rm -rf /")));
        IToolManager toolManager = recordingToolManager(new AtomicBoolean());

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        CollectingAuditLogger audit = new CollectingAuditLogger();
        engine.setAuditLogger(audit);

        AgentExecutionResult result = engine.execute(request).toCompletableFuture()
                .get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // The deny audit event recorded end-to-end carries the correct fields.
        assertTrue(audit.hasDenialFor("bash", "hardcoded_deny_list"),
                "End-to-end audit must record a DENY audit event for bash with matched rule "
                        + "'hardcoded_deny_list'. Events: " + audit.events);

        AuditEvent denyEvent = audit.events.stream()
                .filter(e -> e.getDecision() == AuditDecision.DENY
                        && "bash".equals(e.getToolName())
                        && "hardcoded_deny_list".equals(e.getMatchedRule()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a bash deny AuditEvent, got: " + audit.events));
        assertEquals(AuditDecision.DENY, denyEvent.getDecision(),
                "End-to-end audit event decision must be DENY");
        assertEquals("bash", denyEvent.getToolName(),
                "End-to-end audit event tool name must be 'bash'");
        assertEquals("hardcoded_deny_list", denyEvent.getMatchedRule(),
                "End-to-end audit event matched rule must be 'hardcoded_deny_list'");

        // The deny also produced a deny tool-response message visible to the LLM.
        List<ChatToolResponseMessage> toolResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .collect(Collectors.toList());
        assertFalse(toolResponses.isEmpty(),
                "A deny tool-response message must be produced for the denied bash call");
    }
}
