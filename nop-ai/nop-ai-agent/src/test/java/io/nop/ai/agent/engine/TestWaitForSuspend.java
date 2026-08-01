package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.IWaitCoordinator;
import io.nop.ai.agent.reliability.NoOpWaitCoordinator;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.reliability.WaitCondition;
import io.nop.ai.agent.reliability.WaitDecision;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 integration tests for WAIT_FOR suspend semantics (design §13.1
 * Decision B): verifies that when an {@link IWaitCoordinator} returns
 * {@link WaitDecision.Action#SUSPEND}, the ReAct loop produces a WAIT_FOR
 * checkpoint, sets status=waiting, completes the future (thread released),
 * and does NOT publish EXECUTION_COMPLETED / POST_CALL events. Also verifies
 * zero regression: {@link NoOpWaitCoordinator} never suspends.
 */
public class TestWaitForSuspend {

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
        agentModel.setName("test-agent-wait");
        agentModel.setTools(Set.of("test-tool"));
        ChatOptionsModel opts = new ChatOptionsModel();
        opts.setMaxTokens(10000);
        agentModel.setChatOptions(opts);
    }

    private AgentExecutionContext buildContext() {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "wait-test-session");
        ctx.setMaxIterations(5);
        return ctx;
    }

    /**
     * A wait coordinator that returns SUSPEND on the first checkWait and
     * PROCEED afterwards (simulating a wake that marked the condition satisfied).
     */
    private static final class SuspendOnceCoordinator implements IWaitCoordinator {
        final AtomicInteger checkCount = new AtomicInteger(0);
        final WaitCondition condition = WaitCondition.event("test-event");

        @Override
        public void requestWait(String sessionId, WaitCondition condition) {
        }

        @Override
        public WaitDecision checkWait(String sessionId) {
            if (checkCount.getAndIncrement() == 0) {
                return WaitDecision.suspend(condition);
            }
            return WaitDecision.none();
        }

        @Override
        public void deliverWake(String sessionId, Object payload) {
        }

        @Override
        public boolean isWaiting(String sessionId) {
            return false;
        }
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

    @Test
    void suspendProducesWaitingStatusAndReleasesThread() {
        SuspendOnceCoordinator coordinator = new SuspendOnceCoordinator();
        ToolExecutionCheckpoint cpManager = new ToolExecutionCheckpoint();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("thinking"))
                .toolManager(dummyToolManager())
                .eventPublisher(new DefaultAgentEventPublisher())
                .waitCoordinator(coordinator)
                .checkpointManager(cpManager)
                .build();

        AgentExecutionContext ctx = buildContext();
        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();

        // The future must be completed (thread released — the suspend breaks
        // the loop and completes the future at :920).
        assertTrue(future.isDone(), "Suspended session's future must be completed (thread released)");
        AgentExecutionResult result = future.join();

        assertEquals(AgentExecStatus.waiting, result.getStatus(),
                "Suspended session must have status=waiting (not running/paused/completed)");
    }

    @Test
    void suspendProducesWaitForCheckpoint() {
        SuspendOnceCoordinator coordinator = new SuspendOnceCoordinator();
        ToolExecutionCheckpoint cpManager = new ToolExecutionCheckpoint();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("thinking"))
                .toolManager(dummyToolManager())
                .eventPublisher(new DefaultAgentEventPublisher())
                .waitCoordinator(coordinator)
                .checkpointManager(cpManager)
                .build();

        executor.execute(buildContext()).toCompletableFuture().join();

        // A WAIT_FOR checkpoint must have been persisted.
        List<Checkpoint> checkpoints = cpManager.getCheckpoints("wait-test-session");
        boolean hasWaitFor = checkpoints.stream()
                .anyMatch(cp -> cp.getType() == CheckpointType.WAIT_FOR);
        assertTrue(hasWaitFor, "A WAIT_FOR checkpoint must be produced on suspend");

        Checkpoint waitForCp = checkpoints.stream()
                .filter(cp -> cp.getType() == CheckpointType.WAIT_FOR)
                .findFirst().orElse(null);
        assertNotNull(waitForCp);
        assertNotNull(waitForCp.getWaitFor(),
                "WAIT_FOR checkpoint must carry a non-null wait_for condition JSON");
    }

    @Test
    void waitingSessionDoesNotPublishExecutionCompleted() {
        SuspendOnceCoordinator coordinator = new SuspendOnceCoordinator();
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("thinking"))
                .toolManager(dummyToolManager())
                .eventPublisher(publisher)
                .waitCoordinator(coordinator)
                .build();

        executor.execute(buildContext()).toCompletableFuture().join();

        boolean hasCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED);
        assertFalse(hasCompleted,
                "A waiting (suspended) session must NOT publish EXECUTION_COMPLETED "
                        + "(design §13.1: post-loop guard excludes waiting)");
    }

    @Test
    void noOpCoordinatorNeverSuspends() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningText("done"))
                .toolManager(dummyToolManager())
                .eventPublisher(publisher)
                .waitCoordinator(NoOpWaitCoordinator.noOp())
                .build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // With NoOp coordinator, the executor must behave identically to
        // pre-WAIT_FOR (zero regression): never waiting.
        assertTrue(result.getStatus() != AgentExecStatus.waiting,
                "NoOp coordinator must never cause a waiting status (zero regression)");
    }
}
