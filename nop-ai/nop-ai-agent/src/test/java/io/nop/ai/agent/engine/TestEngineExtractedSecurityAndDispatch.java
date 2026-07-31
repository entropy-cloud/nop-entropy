package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.*;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.WriteIntent;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for the MA4.2-05 extracted classes {@link AgentSecurityConsultation},
 * {@link AgentToolDispatcher} and {@link ReActAgentExecutorBuilder}.
 */
public class TestEngineExtractedSecurityAndDispatch {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class StubChatService implements IChatService {
        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new ChatResponse());
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    static class StubToolManager implements IToolManager {
        final List<AiToolModel> tools = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile CompletableFuture<AiToolCallResult> result =
                CompletableFuture.completedFuture(successResult());

        static AiToolCallResult successResult() {
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            return r;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            calls.incrementAndGet();
            return result;
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new io.nop.ai.toolkit.model.AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return tools;
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return tools.stream().filter(t -> toolName.equals(t.getName())).findFirst().orElse(null);
        }
    }

    private static AgentExecutionContext ctx() {
        return new AgentExecutionContext(new AgentModel());
    }

    private static AgentToolPlanResolver planResolver(IToolManager tm) {
        return new AgentToolPlanResolver(tm);
    }

    private static ChatToolCall callOf(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    private static AgentHookInvoker invoker() {
        return new AgentHookInvoker(new io.nop.ai.agent.hook.DefaultHookRegistry(), null);
    }

    private static AgentSecurityConsultation consultation(IToolAccessChecker toolAccess,
                                                          IPathAccessChecker pathAccess) {
        return new AgentSecurityConsultation(
                new DefaultPostDenialGuard(),
                new Slf4jAuditLogger(),
                toolAccess,
                new AllowAllPermissionProvider(),
                pathAccess,
                new DefaultSecurityLevelResolver(),
                new DefaultPermissionMatrix(),
                new DefaultApprovalGate(),
                new DefaultDenialLedger(),
                FailFastStrategy.failFast(),
                new InMemoryWriteIntentRegistry(),
                planResolver(new StubToolManager()),
                invoker());
    }

    // ============ AgentSecurityConsultation ============

    @Test
    void checkpointChainAllowsByDefault() {
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        SecurityCheckpointChain chain = sec.buildCheckpointChain();
        AgentExecutionContext c = ctx();
        ChatToolCall call = callOf("1", "read-file");
        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "s1", "agent", call, c, null, new AgentModel());
        assertEquals(SecurityCheckpoint.Decision.ALLOW, chain.evaluate(checkCtx));
    }

    @Test
    void checkpointChainDeniesOnToolAccess() {
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        SecurityCheckpointChain chain = sec.buildCheckpointChain();
        // default checker allows; verify the deny path with a hardcoded-deny checker
        AgentSecurityConsultation denying = new AgentSecurityConsultation(
                new DefaultPostDenialGuard(),
                new Slf4jAuditLogger(),
                new IToolAccessChecker() {
                    @Override
                    public ToolAccessResult checkAccess(String toolName, io.nop.ai.agent.engine.AgentExecutionContext ctx) {
                        return ToolAccessResult.deny("deny-all");
                    }
                },
                new AllowAllPermissionProvider(),
                new DefaultPathAccessChecker(),
                new DefaultSecurityLevelResolver(),
                new DefaultPermissionMatrix(),
                new DefaultApprovalGate(),
                new DefaultDenialLedger(),
                FailFastStrategy.failFast(),
                new InMemoryWriteIntentRegistry(),
                planResolver(new StubToolManager()),
                invoker());
        AgentExecutionContext c = ctx();
        ChatToolCall call = callOf("1", "read-file");
        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "s1", "agent", call, c, null, new AgentModel());
        SecurityCheckpoint.Decision d = denying.buildCheckpointChain().evaluate(checkCtx);
        assertEquals(SecurityCheckpoint.Decision.DENY, d);
        assertFalse(c.getMessages().isEmpty()); // error response committed
    }

    @Test
    void handleDenialAndCheckThresholdPausesOnThreshold() {
        DefaultDenialLedger ledger = new DefaultDenialLedger();
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        AgentExecutionContext c = ctx();
        ChatToolCall call = callOf("1", "read-file");
        call.setArguments(Map.of("path", "/a"));
        boolean exceeded = false;
        for (int i = 0; i < 4; i++) {
            exceeded = sec.handleDenialAndCheckThreshold(
                    "s1", "read-file", DenialLayerSource.LAYER1_TOOL_ACCESS,
                    "denied", "test", c, "agent", call, "/work");
        }
        assertTrue(exceeded);
        assertEquals(AgentExecStatus.paused, c.getStatus());
    }

    @Test
    void checkPathAccessAllowsMissingArguments() {
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        ChatToolCall call = callOf("1", "read-file");
        assertNull(sec.checkPathAccess(call, ctx(), "s1", "agent"));
    }

    @Test
    void checkLayer3ApprovalApprovesStandardLevel() {
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        assertNull(sec.checkLayer3Approval(SecurityLevel.STANDARD, "read-file", ctx(), "s1", "agent"));
    }

    // ============ AgentToolDispatcher ============

    @Test
    void executeAllowedCallsCommitsToolResults() {
        StubToolManager tm = new StubToolManager();
        AgentHookInvoker hookInvoker = invoker();
        AgentToolPlanResolver plan = planResolver(tm);
        AgentSecurityConsultation sec = consultation(
                new DefaultToolAccessChecker(), new DefaultPathAccessChecker());
        AgentToolDispatcher dispatcher = new AgentToolDispatcher(
                tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), hookInvoker, plan, sec);
        AgentExecutionContext c = ctx();
        AgentModel model = new AgentModel();
        model.setWorkDir("/tmp/w");
        AgentToolExecuteContext execCtx = dispatcher.prepareDispatchContext(c, model, "s1", "agent");
        assertNotNull(execCtx);
        assertEquals(new java.io.File("/tmp/w"), execCtx.getWorkDir());

        List<ChatToolCall> allowed = List.of(callOf("1", "read-file"));
        dispatcher.executeAllowedCalls(c, "agent", "s1", allowed, execCtx, 0L, new int[]{0});
        assertEquals(1, tm.calls.get());
        assertTrue(c.getMessages().stream().anyMatch(m -> m instanceof io.nop.ai.api.chat.messages.ChatToolResponseMessage));
        assertTrue(c.getMessages().stream().anyMatch(m ->
                m instanceof io.nop.ai.api.chat.messages.ChatToolResponseMessage
                        && ((io.nop.ai.api.chat.messages.ChatToolResponseMessage) m).getContent() != null));
    }

    @Test
    void executeAllowedCallsSurfacesToolError() {
        StubToolManager tm = new StubToolManager();
        AiToolCallResult err = new AiToolCallResult();
        err.setStatus("error");
        io.nop.ai.toolkit.model.AiToolError errorBean = new io.nop.ai.toolkit.model.AiToolError();
        errorBean.setBody("boom");
        err.setError(errorBean);
        tm.result = CompletableFuture.completedFuture(err);
        AgentToolDispatcher dispatcher = new AgentToolDispatcher(
                tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), invoker(),
                planResolver(tm), consultation(new DefaultToolAccessChecker(), new DefaultPathAccessChecker()));
        AgentExecutionContext c = ctx();
        dispatcher.executeAllowedCalls(c, "agent", "s1",
                List.of(callOf("1", "bad-tool")),
                new AgentToolExecuteContext(null, java.util.Collections.emptyMap(), 0L,
                        null, null, null, null, null, "s1", "agent",
                        Set.of(), Set.of(), null, null, null, null, null),
                0L, new int[]{0});
        assertEquals(1, tm.calls.get());
    }

    // ============ ReActAgentExecutorBuilder ============

    @Test
    void builderBuildsExecutorWithDefaults() {
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(new StubChatService())
                .toolManager(new StubToolManager())
                .build();
        assertNotNull(executor);
        assertNotNull(executor.getSandboxBackend());
    }

    @Test
    void builderRejectsNullChatServiceAndToolManager() {
        assertThrows(io.nop.api.core.exceptions.NopException.class, () ->
                ReActAgentExecutor.builder().build());
    }
}
