package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.middleware.AttemptContext;
import io.nop.ai.agent.middleware.ExecutionPoint;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3-1 (Phase 3): tests for execution-level (per-tool-call) middleware inside
 * {@link AgentToolDispatcher#executeAllowedCalls}. Verifies (items 3.1-3.3):
 * <ul>
 *   <li>3.1: PRE/POST_TOOL_ATTEMPT fire around each individual tool call (per
 *       tool, not per batch; D4 thread model = sync before dispatch + after
 *       join before commit)</li>
 *   <li>3.2: each tool call triggers execution middleware (before/after)</li>
 *   <li>3.3 (Anti-Hollow): execution middleware Veto aborts a single tool call
 *       — return value IS checked (not dropped like the existing PRE_ACTING
 *       path), produces an error result, does NOT affect other tools in the
 *       same batch</li>
 * </ul>
 *
 * <p>End-to-end (Rule #22) + wiring (Rule #23): the dispatcher actually invokes
 * the execution middleware per tool call (proven by the recording middleware
 * observing per-tool firing and the tool-call count reflecting vetoes).
 */
public class TestExecutionMiddlewareToolDispatch {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    /** Tool manager that counts calls per tool name and returns success. */
    static class PerToolStubToolManager implements IToolManager {
        final AtomicInteger totalCalls = new AtomicInteger();
        final List<String> calledTools = new ArrayList<>();

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            totalCalls.incrementAndGet();
            calledTools.add(toolName);
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            io.nop.ai.toolkit.model.AiToolOutput out = new io.nop.ai.toolkit.model.AiToolOutput();
            out.setBody("result-of-" + toolName);
            r.setOutput(out);
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new io.nop.ai.toolkit.model.AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return new ArrayList<>();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    /** Records every PRE/POST_TOOL_ATTEMPT firing with the tool name. */
    static class RecordingExecMiddleware implements IAgentMiddleware {
        final ExecutionPoint point;
        final List<String> log;

        RecordingExecMiddleware(ExecutionPoint point, List<String> log) {
            this.point = point;
            this.log = log;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            log.add(point.name() + ":" + ctx.getToolName());
            return next.proceed(ctx);
        }
    }

    /** Vetoes tools whose name is in the veto set. */
    static class VetoToolsMiddleware implements IAgentMiddleware {
        final ExecutionPoint point;
        final java.util.Set<String> vetoTools;

        VetoToolsMiddleware(ExecutionPoint point, java.util.Set<String> vetoTools) {
            this.point = point;
            this.vetoTools = vetoTools;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            if (vetoTools.contains(ctx.getToolName())) {
                return new HookResult.VetoResult("vetoed-" + ctx.getToolName());
            }
            return next.proceed(ctx);
        }
    }

    private static ChatToolCall callOf(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext c = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        c.setStatus(AgentExecStatus.running);
        return c;
    }

    private static AgentToolDispatcher dispatcherWith(DefaultHookRegistry registry, PerToolStubToolManager tm) {
        AgentHookInvoker hookInvoker = new AgentHookInvoker(registry, null);
        AgentToolPlanResolver plan = new AgentToolPlanResolver(tm);
        AgentSecurityConsultation sec = new AgentSecurityConsultation(
                new io.nop.ai.agent.security.DefaultPostDenialGuard(),
                new io.nop.ai.agent.security.Slf4jAuditLogger(),
                new DefaultToolAccessChecker(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DefaultPathAccessChecker(),
                new io.nop.ai.agent.security.DefaultSecurityLevelResolver(),
                new io.nop.ai.agent.security.DefaultPermissionMatrix(),
                new io.nop.ai.agent.security.DefaultApprovalGate(),
                new io.nop.ai.agent.security.DefaultDenialLedger(),
                io.nop.ai.agent.conflict.FailFastStrategy.failFast(),
                new io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry(),
                plan, hookInvoker);
        return new AgentToolDispatcher(
                tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), hookInvoker, plan, sec);
    }

    // ============ 3.1 + 3.2: PRE/POST fire around each individual tool call ============

    @Test
    void preAndPostToolAttemptFireAroundEachToolCallInBatch() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_TOOL_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.PRE_TOOL_ATTEMPT, log));
        registry.registerExecutionMiddleware(ExecutionPoint.POST_TOOL_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.POST_TOOL_ATTEMPT, log));

        PerToolStubToolManager tm = new PerToolStubToolManager();
        AgentToolDispatcher dispatcher = dispatcherWith(registry, tm);
        AgentExecutionContext c = ctx();
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        AgentToolExecuteContext execCtx = dispatcher.prepareDispatchContext(c, model, "s1", "agent");

        List<ChatToolCall> allowed = List.of(callOf("1", "read-file"), callOf("2", "write-file"));
        dispatcher.executeAllowedCalls(c, "agent", "s1", allowed, execCtx, 0L, new int[]{0});

        // Both tools executed (no veto)
        assertEquals(2, tm.totalCalls.get());
        // Wiring (Rule #23): PRE and POST fired once per tool (per tool call, not per batch)
        // Order per tool: PRE:read-file, POST:read-file, PRE:write-file, POST:write-file
        // (result processing is sequential in join order)
        assertEquals(4, log.size(), "PRE+POST must fire per tool call (2 tools x 2 = 4 firings)");
        assertTrue(log.contains("PRE_TOOL_ATTEMPT:read-file"));
        assertTrue(log.contains("POST_TOOL_ATTEMPT:read-file"));
        assertTrue(log.contains("PRE_TOOL_ATTEMPT:write-file"));
        assertTrue(log.contains("POST_TOOL_ATTEMPT:write-file"));
    }

    // ============ 3.3 (Anti-Hollow): PRE_TOOL_ATTEMPT Veto aborts one tool, not the batch ============

    @Test
    void preToolAttemptVetoAbortsSingleToolAndDoesNotAffectBatch() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        // Veto only "dangerous-tool"; allow "safe-tool"
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_TOOL_ATTEMPT,
                new VetoToolsMiddleware(ExecutionPoint.PRE_TOOL_ATTEMPT, java.util.Set.of("dangerous-tool")));

        PerToolStubToolManager tm = new PerToolStubToolManager();
        AgentToolDispatcher dispatcher = dispatcherWith(registry, tm);
        AgentExecutionContext c = ctx();
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        AgentToolExecuteContext execCtx = dispatcher.prepareDispatchContext(c, model, "s1", "agent");

        List<ChatToolCall> allowed = List.of(
                callOf("1", "dangerous-tool"),
                callOf("2", "safe-tool"));
        dispatcher.executeAllowedCalls(c, "agent", "s1", allowed, execCtx, 0L, new int[]{0});

        // Anti-Hollow: veto return value IS checked — dangerous-tool was NOT executed
        // (future never submitted), but safe-tool WAS executed. Batch not aborted.
        assertEquals(1, tm.totalCalls.get(), "only the non-vetoed tool must run");
        assertFalse(tm.calledTools.contains("dangerous-tool"),
                "vetoed tool must NOT be dispatched to toolManager");
        assertTrue(tm.calledTools.contains("safe-tool"),
                "non-vetoed tool in same batch must still run");

        // Both tools produce a tool-response message (vetoed one = error result,
        // non-vetoed = success) — pairing intact, no result dropped.
        List<ChatToolResponseMessage> toolMsgs = messagesOf(c);
        assertEquals(2, toolMsgs.size(), "both tools must produce a tool-response message (no drop)");
        // The vetoed tool's response carries the veto error
        boolean hasVetoError = toolMsgs.stream().anyMatch(m ->
                m.getContent() != null && m.getContent().contains("vetoed"));
        assertTrue(hasVetoError, "vetoed tool must surface a veto error result to the LLM");
    }

    // ============ 3.3 (Anti-Hollow): POST_TOOL_ATTEMPT Veto replaces result, return value checked ============

    @Test
    void postToolAttemptVetoReplacesResultAndReturnValueIsChecked() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        // Veto "filter-tool" at POST (after it ran) — replaces its success result with error
        registry.registerExecutionMiddleware(ExecutionPoint.POST_TOOL_ATTEMPT,
                new VetoToolsMiddleware(ExecutionPoint.POST_TOOL_ATTEMPT, java.util.Set.of("filter-tool")));

        PerToolStubToolManager tm = new PerToolStubToolManager();
        AgentToolDispatcher dispatcher = dispatcherWith(registry, tm);
        AgentExecutionContext c = ctx();
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        AgentToolExecuteContext execCtx = dispatcher.prepareDispatchContext(c, model, "s1", "agent");

        dispatcher.executeAllowedCalls(c, "agent", "s1",
                List.of(callOf("1", "filter-tool")), execCtx, 0L, new int[]{0});

        // POST fires AFTER the tool ran (D4 option a), so the call count is 1.
        assertEquals(1, tm.totalCalls.get(), "POST fires after the tool executed");

        // Anti-Hollow: POST veto return value IS checked — the success result is
        // replaced by a veto error result, surfaced to the LLM.
        List<ChatToolResponseMessage> toolMsgs = messagesOf(c);
        assertEquals(1, toolMsgs.size());
        assertTrue(toolMsgs.get(0).getContent().contains("vetoed"),
                "POST veto must replace the success result with a veto error");
    }

    // ============ zero-overhead: no execution middleware => existing dispatch unchanged ============

    @Test
    void noExecutionMiddlewareMeansExistingDispatchUnchanged() {
        DefaultHookRegistry registry = new DefaultHookRegistry(); // no execution middleware
        PerToolStubToolManager tm = new PerToolStubToolManager();
        AgentToolDispatcher dispatcher = dispatcherWith(registry, tm);
        AgentExecutionContext c = ctx();
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        AgentToolExecuteContext execCtx = dispatcher.prepareDispatchContext(c, model, "s1", "agent");

        dispatcher.executeAllowedCalls(c, "agent", "s1",
                List.of(callOf("1", "read-file")), execCtx, 0L, new int[]{0});

        assertEquals(1, tm.totalCalls.get(), "dispatch path unchanged when no execution middleware");
        assertEquals(1, messagesOf(c).size());
    }

    private static List<ChatToolResponseMessage> messagesOf(AgentExecutionContext c) {
        List<ChatToolResponseMessage> out = new ArrayList<>();
        c.getMessages().forEach(m -> {
            if (m instanceof ChatToolResponseMessage) {
                out.add((ChatToolResponseMessage) m);
            }
        });
        return out;
    }
}
