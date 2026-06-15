package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 206 (L2-22) Phase 2 wiring + end-to-end test (Minimum Rules #22, #23,
 * #25). Covers three concerns:
 *
 * <ol>
 *   <li><b>Engine wiring</b>: {@link DefaultAgentEngine} defaults to
 *       {@link NoOpBudgetProvider}; {@code setBudgetProvider} overrides the
 *       default and is reachable from the executor (proven by the e2e path
 *       below, where the router observes the injected provider's snapshot).</li>
 *   <li><b>Snapshot refresh before route()</b>: the budget snapshot stored in
 *       {@code ctx} is non-null every time the router is invoked (the ReAct
 *       loop refreshes it before {@code IModelRouter.route()}).</li>
 *   <li><b>Budget-driven downgrade (end-to-end)</b>: with an
 *       {@link InMemoryBudgetProvider} (limit=$1.00) and a budget-aware
 *       {@link IModelRouter} stub, the router returns the premium model while
 *       under budget and downgrades to a cheaper model once the accumulated
 *       cost exceeds the limit — proving the full path
 *       DefaultAgentEngine.execute() → ReAct loop → budget refresh →
 *       ctx.getBudgetSnapshot() → router downgrade decision.</li>
 * </ol>
 */
public class TestBudgetProviderWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine wiring: default NoOp + setter override + null fallback
    // ========================================================================

    @Test
    void engineDefaultsToNoOpBudgetProvider() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IBudgetProvider provider = engine.getBudgetProvider();
        assertNotNull(provider, "Engine must default to a non-null budget provider");
        assertTrue(provider instanceof NoOpBudgetProvider,
                "Shipped default must be the NoOpBudgetProvider pass-through");
    }

    @Test
    void setBudgetProviderOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        InMemoryBudgetProvider custom = new InMemoryBudgetProvider(new BigDecimal("5.00"));
        engine.setBudgetProvider(custom);
        assertTrue(engine.getBudgetProvider() == custom,
                "getBudgetProvider must return the exact instance set via setBudgetProvider");
    }

    @Test
    void setBudgetProviderNullFallsBackToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setBudgetProvider(null);
        IBudgetProvider provider = engine.getBudgetProvider();
        assertNotNull(provider, "null setter must fall back to a non-null NoOp default");
        assertTrue(provider instanceof NoOpBudgetProvider);
    }

    // ========================================================================
    // End-to-end: budget-driven model downgrade through DefaultAgentEngine
    // ========================================================================

    @Test
    void budgetExceededTriggersModelDowngradeEndToEnd() throws Exception {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");
        assertTrue(model.getTools().contains("test-calculator"),
                "Agent model must declare test-calculator tool for this test");

        // Chat service: 3 LLM calls — two tool calls then a final answer.
        // The loop therefore runs 3 iterations, exercising 3 route() calls.
        ChatToolCall toolCallA = new ChatToolCall();
        toolCallA.setId("call_budget_1");
        toolCallA.setName("test-calculator");
        toolCallA.setArguments(Map.of("expr", "1+1"));

        ChatToolCall toolCallB = new ChatToolCall();
        toolCallB.setId("call_budget_2");
        toolCallB.setName("test-calculator");
        toolCallB.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg1 = new ChatAssistantMessage();
        toolMsg1.setContent("");
        toolMsg1.setToolCalls(List.of(toolCallA));
        ChatResponse resp1 = ChatResponse.success(toolMsg1);
        resp1.setRequestId("req-b-1");

        ChatAssistantMessage toolMsg2 = new ChatAssistantMessage();
        toolMsg2.setContent("");
        toolMsg2.setToolCalls(List.of(toolCallB));
        ChatResponse resp2 = ChatResponse.success(toolMsg2);
        resp2.setRequestId("req-b-2");

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("Done.");
        ChatResponse resp3 = ChatResponse.success(finalMsg);
        resp3.setRequestId("req-b-3");

        AtomicInteger chatCallCount = new AtomicInteger(0);
        List<ChatResponse> responses = List.of(resp1, resp2, resp3);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(chatCallCount.getAndIncrement()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(chatCallCount.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
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
                m.setDescription("Test tool: " + toolName);
                return m;
            }
        };

        // In-memory budget provider: limit = $1.00, cost starts at $0.00.
        InMemoryBudgetProvider budgetProvider = new InMemoryBudgetProvider(new BigDecimal("1.00"));

        // Budget-aware router: reads ctx.getBudgetSnapshot(). While not exceeded
        // returns the premium model; once exceeded returns the cheap (downgrade)
        // model. After each decision it accrues $0.60 of cost into the provider,
        // simulating the cost of the LLM call that is about to happen — so the
        // NEXT iteration's snapshot reflects the accumulated spend.
        List<String> routedModels = Collections.synchronizedList(new ArrayList<>());
        List<BudgetSnapshot> snapshotsSeenByRouter =
                Collections.synchronizedList(new ArrayList<>());
        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<io.nop.ai.api.chat.messages.ChatMessage> messages,
                                       ChatOptions options, io.nop.ai.agent.engine.AgentExecutionContext ctx) {
                BudgetSnapshot snapshot = ctx.getBudgetSnapshot();
                snapshotsSeenByRouter.add(snapshot);
                ChatOptions routed = options.copy();
                if (snapshot != null && snapshot.isExceeded()) {
                    routed.setProvider("cheap-provider");
                    routed.setModel("cheap-model");
                    routedModels.add("cheap-model");
                } else {
                    routed.setProvider("premium-provider");
                    routed.setModel("premium-model");
                    routedModels.add("premium-model");
                }
                // Accrue the cost of this LLM call for the next iteration.
                budgetProvider.addCost(new BigDecimal("0.60"));
                return new RoutingResult(routed, null,
                        snapshot != null && snapshot.isExceeded() ? "budget-downgrade" : "normal");
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, toolManager, new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp(),
                router);
        engine.setBudgetProvider(budgetProvider);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "Compute something");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should complete successfully");
        assertEquals(3, chatCallCount.get(),
                "LLM should be called three times (two tool calls + final)");

        // Anti-hollow check: the router was actually invoked and observed a
        // non-null budget snapshot on every iteration (the refresh happened
        // before route()).
        assertEquals(3, routedModels.size(),
                "Router must be invoked once per iteration (3 iterations)");
        assertEquals(3, snapshotsSeenByRouter.size());
        for (BudgetSnapshot snap : snapshotsSeenByRouter) {
            assertNotNull(snap,
                    "ctx.getBudgetSnapshot() must be non-null when route() is invoked (refreshed before route)");
        }

        // Budget trace:
        //   iter 0: cost=0.00, not exceeded → premium; accrue → 0.60
        //   iter 1: cost=0.60, not exceeded → premium; accrue → 1.20
        //   iter 2: cost=1.20, exceeded    → cheap (downgrade)
        assertEquals("premium-model", routedModels.get(0),
                "Under budget (cost 0.00): router must return premium model");
        assertEquals("premium-model", routedModels.get(1),
                "Under budget (cost 0.60): router must return premium model");
        assertEquals("cheap-model", routedModels.get(2),
                "Over budget (cost 1.20 >= limit 1.00): router must downgrade to cheap model");

        // The snapshot seen at the downgrade iteration must be marked exceeded.
        assertTrue(snapshotsSeenByRouter.get(2).isExceeded(),
                "The snapshot at the downgrade iteration must report exceeded=true");
        assertFalse(snapshotsSeenByRouter.get(0).isExceeded(),
                "The snapshot at iter 0 must report exceeded=false");

        // The provider's accumulated cost reflects all three accruals.
        assertEquals(0, new BigDecimal("1.80").compareTo(budgetProvider.getEstimatedTotalCost()),
                "Provider cost must have accumulated 3 * 0.60 = 1.80");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static IChatService noOpChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }
        };
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return null;
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
}
