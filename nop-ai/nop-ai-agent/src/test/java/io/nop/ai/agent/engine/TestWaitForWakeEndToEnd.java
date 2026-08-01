package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.DefaultWaitCoordinator;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.reliability.WaitCondition;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ChatOptionsModel;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 end-to-end tests for the WAIT_FOR wake mechanism (design §13.1
 * Decisions C/H): verifies that wake re-entry does not re-suspend (anti-
 * re-suspend), the session proceeds to completion, and no second WAIT_FOR
 * checkpoint is produced on wake re-entry.
 */
public class TestWaitForWakeEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel;

    @BeforeEach
    void setUp() {
        agentModel = new AgentModel();
        agentModel.setName("test-agent-wake");
        agentModel.setTools(Set.of("test-tool"));
        ChatOptionsModel opts = new ChatOptionsModel();
        opts.setMaxTokens(10000);
        agentModel.setChatOptions(opts);
    }

    private AgentExecutionContext buildContext() {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "wake-test-session");
        ctx.setMaxIterations(5);
        return ctx;
    }

    private IChatService chatServiceReturningText(String content) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent(content);
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager dummyToolManager() {
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Test");
                return m;
            }
        };
    }

    /**
     * Full end-to-end: register wait → execute (suspend) → deliver wake →
     * execute again (proceed, no re-suspend) → reaches a non-waiting terminal
     * status. Verifies Decision H anti-re-suspend.
     */
    @Test
    void wakeReentryDoesNotReSuspend() {
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator();
        ToolExecutionCheckpoint cpManager = new ToolExecutionCheckpoint();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("done"))
                .toolManager(dummyToolManager())
                .eventPublisher(new DefaultAgentEventPublisher())
                .waitCoordinator(coordinator)
                .checkpointManager(cpManager)
                .build();

        // Register a wait condition
        coordinator.requestWait("wake-test-session", WaitCondition.event("test-event"));

        // First execute: should suspend (WAIT_FOR)
        AgentExecutionResult result1 = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.waiting, result1.getStatus(),
                "First execute must suspend with status=waiting");

        // Verify exactly one WAIT_FOR checkpoint
        List<Checkpoint> cps1 = cpManager.getCheckpoints("wake-test-session");
        long waitForCount1 = cps1.stream()
                .filter(cp -> cp.getType() == CheckpointType.WAIT_FOR).count();
        assertEquals(1, waitForCount1,
                "First execute must produce exactly one WAIT_FOR checkpoint");

        // Deliver wake
        coordinator.deliverWake("wake-test-session", "wake-payload");

        // Second execute: should NOT re-suspend (Decision H)
        AgentExecutionResult result2 = executor.execute(buildContext()).toCompletableFuture().join();
        assertTrue(result2.getStatus() != AgentExecStatus.waiting,
                "Second execute after wake must NOT re-suspend (anti-re-suspend, Decision H)");

        // Verify no second WAIT_FOR checkpoint was produced
        List<Checkpoint> cps2 = cpManager.getCheckpoints("wake-test-session");
        long waitForCount2 = cps2.stream()
                .filter(cp -> cp.getType() == CheckpointType.WAIT_FOR).count();
        assertEquals(1, waitForCount2,
                "Wake re-entry must NOT produce a second WAIT_FOR checkpoint (anti-re-suspend)");
    }

    /**
     * Timeout condition with injectable clock: suspend when deadline not yet
     * passed, proceed after clock advances past deadline.
     */
    @Test
    void timeoutConditionAdvancesToProceedOnClockAdvance() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000L);
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator(clock::get, null);
        ToolExecutionCheckpoint cpManager = new ToolExecutionCheckpoint();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("done"))
                .toolManager(dummyToolManager())
                .eventPublisher(new DefaultAgentEventPublisher())
                .waitCoordinator(coordinator)
                .checkpointManager(cpManager)
                .build();

        // Register timeout at deadline=2000
        coordinator.requestWait("wake-test-session", WaitCondition.timeout(2000L));

        // First execute: suspend (deadline not reached, clock=1000)
        AgentExecutionResult result1 = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.waiting, result1.getStatus(),
                "Before deadline, session must suspend");

        // Advance clock past deadline
        clock.set(2001L);

        // Second execute: proceed (deadline passed → checkWait returns PROCEED)
        AgentExecutionResult result2 = executor.execute(buildContext()).toCompletableFuture().join();
        assertTrue(result2.getStatus() != AgentExecStatus.waiting,
                "After deadline passed, session must NOT re-suspend");
    }

    /**
     * Without wake, repeated execute continues to suspend (condition stays
     * unsatisfied). This verifies the session stays resident (no premature
     * wake).
     */
    @Test
    void withoutWakeSessionStaysWaitingOnReentry() {
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator();
        ToolExecutionCheckpoint cpManager = new ToolExecutionCheckpoint();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("thinking"))
                .toolManager(dummyToolManager())
                .eventPublisher(new DefaultAgentEventPublisher())
                .waitCoordinator(coordinator)
                .checkpointManager(cpManager)
                .build();

        coordinator.requestWait("wake-test-session", WaitCondition.event("test-event"));

        // First execute: suspend
        AgentExecutionResult r1 = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.waiting, r1.getStatus());

        // Second execute WITHOUT wake: still suspend (condition not satisfied)
        AgentExecutionResult r2 = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.waiting, r2.getStatus(),
                "Without wake, session must stay waiting on re-entry");
    }
}
