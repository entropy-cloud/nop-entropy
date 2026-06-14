package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ApprovalDecision;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.IApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end tests: verifies the Layer 3 approval-gate dispatch-path
 * consultation is actually connected — from {@link AgentMessageRequest}, through
 * {@code DefaultAgentEngine.doExecute}, the {@link ReActAgentExecutor} Builder,
 * into the dispatch loop, where a functional gate approves/denies tool calls
 * based on the resolved {@link SecurityLevel}.
 *
 * <p>Includes wiring verification ({@code gate.requestApproval(...)} is actually
 * invoked in the dispatch loop), audit verification (a denial records an
 * {@link AuditEvent} with matched rule {@code "layer3_approval_gate"}), and
 * backward-compat ({@link AutoApproveGate} default produces zero spurious
 * denials).
 */
public class TestDispatchPathApprovalGate {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Functional resolver + counting gate (isolates Layer 3 from Layer 2)
    // ========================================================================

    /**
     * Test-internal functional resolver: {@code shell.exec} → ELEVATED,
     * {@code network.fetch} → RESTRICTED, everything else → STANDARD. Isolates
     * the Layer 3 gate test from the Layer 2 matrix (which is PassThrough by
     * default, so Layer 2 always allows here).
     */
    static final class TestResolver implements ISecurityLevelResolver {
        @Override
        public SecurityLevel resolve(String actionKind, LevelHints hints) {
            String kind = actionKind == null ? "" : actionKind.replace('_', '.').toLowerCase(Locale.ROOT);
            if ("shell.exec".equals(kind)) {
                return SecurityLevel.ELEVATED;
            }
            if ("network.fetch".equals(kind)) {
                return SecurityLevel.RESTRICTED;
            }
            return SecurityLevel.STANDARD;
        }
    }

    /**
     * Test-internal functional gate: denies ELEVATED and RESTRICTED, approves
     * STANDARD. Counts requestApproval() calls for wiring verification.
     */
    static final class CountingGate implements IApprovalGate {
        final AtomicInteger approvalCount = new AtomicInteger();

        @Override
        public ApprovalDecision requestApproval(SecurityLevel level, String toolName,
                                                ChannelKind channel, Principal principal,
                                                String sessionId, String agentName) {
            approvalCount.incrementAndGet();
            if (level == SecurityLevel.ELEVATED) {
                return ApprovalDecision.denyHumanRejected("elevated requires human approval");
            }
            if (level == SecurityLevel.RESTRICTED) {
                return ApprovalDecision.denyTimeout("restricted approvals timed out");
            }
            return ApprovalDecision.approve("test-approver");
        }
    }

    /**
     * Collecting audit logger: records every event so tests can assert the
     * matched rule and decision of a Layer 3 denial.
     */
    static final class CollectingAuditLogger implements IAuditLogger {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void log(AuditEvent event) {
            events.add(event);
        }

        boolean hasDenialWithRule(String matchedRule) {
            for (AuditEvent e : events) {
                if (e.getDecision() == AuditDecision.DENY && matchedRule.equals(e.getMatchedRule())) {
                    return true;
                }
            }
            return false;
        }
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    private IChatService chatServiceReturningToolThenFinal(String toolName, String toolCallId) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(toolCallId);
        toolCall.setName(toolName);
        toolCall.setArguments(Map.of("command", "ls"));
        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("done");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        AtomicInteger count = new AtomicInteger();
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(Math.min(count.getAndIncrement(), responses.size() - 1)));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(Math.min(count.getAndIncrement(), responses.size() - 1));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
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
                return null;
            }
        };
    }

    private boolean containsMessage(AgentExecutionResult result, String needle) {
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========================================================================
    // End-to-end: ELEVATED action + deny-gate → blocked by approval gate
    // ========================================================================

    @Test
    void elevatedActionIsBlockedByApprovalGate() {
        CountingGate gate = new CountingGate();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("shell.exec", "call_shell_1"), stubToolManager());
        engine.setSecurityLevelResolver(new TestResolver());
        engine.setApprovalGate(gate);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run ls", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring verification: gate.requestApproval(...) was actually invoked
        // in the dispatch loop (Minimum Rules #23 Wiring Verification).
        assertTrue(gate.approvalCount.get() >= 1,
                "approvalGate.requestApproval(...) must be called in the dispatch loop (wiring), got: "
                        + gate.approvalCount.get());

        // The tool call was denied by the approval gate.
        assertTrue(containsMessage(result, "Approval denied"),
                "ELEVATED shell.exec must be denied by the approval gate — expected an 'Approval denied' tool response");
    }

    // ========================================================================
    // End-to-end: RESTRICTED action + deny-gate → blocked (timeout kind)
    // ========================================================================

    @Test
    void restrictedActionIsBlockedByApprovalGate() {
        CountingGate gate = new CountingGate();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("network.fetch", "call_net_1"), stubToolManager());
        engine.setSecurityLevelResolver(new TestResolver());
        engine.setApprovalGate(gate);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "fetch", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertTrue(gate.approvalCount.get() >= 1,
                "approvalGate.requestApproval(...) must be called for RESTRICTED actions too");
        assertTrue(containsMessage(result, "Approval denied"),
                "RESTRICTED network.fetch must be denied by the approval gate");
    }

    // ========================================================================
    // End-to-end: STANDARD action + functional gate → approved
    // ========================================================================

    @Test
    void standardActionIsApprovedByGate() {
        CountingGate gate = new CountingGate();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("fs.read", "call_read_1"), stubToolManager());
        engine.setSecurityLevelResolver(new TestResolver());
        engine.setApprovalGate(gate);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "read", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring: gate was consulted even for a STANDARD action.
        assertTrue(gate.approvalCount.get() >= 1,
                "approvalGate.requestApproval(...) must be called even for STANDARD actions");
        assertFalse(containsMessage(result, "Approval denied"),
                "STANDARD action (fs.read) must be approved by the gate — no 'Approval denied' expected");
    }

    // ========================================================================
    // Audit verification: denial records AuditEvent with matched rule
    // "layer3_approval_gate" (Builder-based, since engine has no auditLogger
    // setter — the audit logger is an executor-internal component)
    // ========================================================================

    @Test
    void layer3DenialRecordsAuditEventWithMatchedRule() {
        CountingGate gate = new CountingGate();
        CollectingAuditLogger audit = new CollectingAuditLogger();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolThenFinal("shell.exec", "call_audit_1"))
                .toolManager(stubToolManager())
                .securityLevelResolver(new TestResolver())
                .approvalGate(gate)
                .auditLogger(audit)
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("audit-test");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "audit-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("go"));

        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(gate.approvalCount.get() >= 1,
                "gate must be consulted via Builder wiring too");
        assertTrue(audit.hasDenialWithRule("layer3_approval_gate"),
                "An approval-gate denial must record an AuditEvent with matchedRule 'layer3_approval_gate'");

        // Also confirm no spurious layer2_permission_matrix denial (the matrix
        // is PassThrough here, so Layer 2 must not deny — only Layer 3 does).
        assertFalse(audit.hasDenialWithRule("layer2_permission_matrix"),
                "PassThrough matrix must not produce a Layer 2 denial when the gate denies");
    }

    // ========================================================================
    // Backward-compat: AutoApproveGate default → no spurious denials
    // ========================================================================

    @Test
    void defaultAutoApproveGateProducesNoSpuriousDenials() {
        // Engine with default gate (AutoApproveGate) — never explicitly
        // registered. The Layer 3 consultation must approve the tool call
        // (no spurious denial), even for an ELEVATED-level action.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("shell.exec", "call_shell_1"), stubToolManager());
        engine.setSecurityLevelResolver(new TestResolver());

        assertTrue(engine.getApprovalGate() instanceof AutoApproveGate,
                "Default approval gate must be AutoApproveGate");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run ls", null, null, ChannelKind.GROUP, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertFalse(containsMessage(result, "Approval denied"),
                "AutoApproveGate default must approve the tool call — no spurious Layer 3 denial");
    }

    // ========================================================================
    // Builder wiring: executor receives the gate; default when not set
    // ========================================================================

    @Test
    void builderDefaultsWhenGateNotSet() {
        // A Builder-built executor without the approval gate must still build
        // (AutoApproveGate default applied) and never throw during a dispatch loop.
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningToolThenFinal("shell.exec", "call_b_1"))
                .toolManager(stubToolManager())
                .build();

        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("btest");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "btest-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));

        // Default gate = AutoApproveGate → no denial, completes without throwing.
        executor.execute(ctx).toCompletableFuture().join();
        // No exception thrown = wiring default is sound.
    }

    // ========================================================================
    // Engine setter/getter wiring
    // ========================================================================

    @Test
    void engineSetGetApprovalGateAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningToolThenFinal("fs.read", "call_sg_1"), stubToolManager());

        assertTrue(engine.getApprovalGate() instanceof AutoApproveGate,
                "Engine default approval gate must be AutoApproveGate");

        CountingGate custom = new CountingGate();
        engine.setApprovalGate(custom);
        assertTrue(engine.getApprovalGate() == custom,
                "setApprovalGate must wire the exact instance");

        // Null setter must fall back to the default, not silently store null.
        engine.setApprovalGate(null);
        assertTrue(engine.getApprovalGate() instanceof AutoApproveGate,
                "setApprovalGate(null) must fall back to AutoApproveGate default");
    }
}
