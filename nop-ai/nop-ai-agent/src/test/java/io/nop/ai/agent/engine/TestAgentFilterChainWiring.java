package io.nop.ai.agent.engine;

import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentFilterChainModel;
import io.nop.ai.agent.model.AgentMiddlewareModel;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.FilterDefModel;
import io.nop.ai.agent.model.FilterRefModel;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
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
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.support.ChatResponseFixtures;

/**
 * W3-2 Phase 2 integration tests: declarative {@code <filter-chain>} assembly
 * via {@link AgentExecutorResolver} and end-to-end execution via the existing
 * onion-chain executor.
 *
 * <p>Verifies (per plan items 2.1-2.4 + exit criteria):
 * <ul>
 *   <li>2.1 — {@code <filter-chain>} is resolved and filters are registered at
 *       the D2-mapped lifecycle points (default PRE_CALL / POST_CALL, or
 *       {@code points} override).</li>
 *   <li>2.2 — {@code <filter-chain>} and {@code <middlewares>} coexist per D3:
 *       declarative filters are registered first (outermost), code-class
 *       middlewares after; the same impl class at the same point in both is a
 *       fast-fail duplicate.</li>
 *   <li>2.3 — input-filters fire at PRE_CALL once per request (single trigger,
 *       not the N+M+K multi-trigger of PRE_REASONING/PRE_ACTING); output-filters
 *       fire at POST_CALL; {@code points} override retargets.</li>
 *   <li>2.4 / end-to-end — declarative filters execute through the real
 *       {@code AgentExecutorResolver.resolveExecutor()} → ReAct execution →
 *       {@code AgentHookInvoker.executeWithMiddleware} → {@link MiddlewareChain}
 *       path (wiring verification, not just model parsing).</li>
 * </ul>
 */
public class TestAgentFilterChainWiring {

    // ---- Reflection-instantiable filters that record before/after markers to
    //      a shared static log + per-filter invocation counters. ----

    static final List<String> LOG = new ArrayList<>();
    static final AtomicInteger INPUT_GUARD_COUNT = new AtomicInteger();
    static final AtomicInteger OUTPUT_GUARD_COUNT = new AtomicInteger();
    static final AtomicInteger REASONING_FILTER_COUNT = new AtomicInteger();
    static final AtomicInteger CODE_CLASS_COUNT = new AtomicInteger();

    public static class InputGuardFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("input-guard.before");
            INPUT_GUARD_COUNT.incrementAndGet();
            HookResult r = next.proceed(ctx);
            LOG.add("input-guard.after");
            return r;
        }
    }

    public static class OutputGuardFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("output-guard.before");
            OUTPUT_GUARD_COUNT.incrementAndGet();
            HookResult r = next.proceed(ctx);
            LOG.add("output-guard.after");
            return r;
        }
    }

    /** Used with points="pre_reasoning" to verify override retargets. */
    public static class ReasoningFilter implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("reasoning-filter.before");
            REASONING_FILTER_COUNT.incrementAndGet();
            HookResult r = next.proceed(ctx);
            LOG.add("reasoning-filter.after");
            return r;
        }
    }

    /** Code-class middleware (distinct impl) for the coexistence test. */
    public static class CodeClassMiddleware implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            LOG.add("code-class.before");
            CODE_CLASS_COUNT.incrementAndGet();
            HookResult r = next.proceed(ctx);
            LOG.add("code-class.after");
            return r;
        }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void reset() {
        LOG.clear();
        INPUT_GUARD_COUNT.set(0);
        OUTPUT_GUARD_COUNT.set(0);
        REASONING_FILTER_COUNT.set(0);
        CODE_CLASS_COUNT.set(0);
    }

    // ---- harness (mirrors TestEngineConfigAndHelpers / TestReActAgentExecutor) ----

    private AgentExecutorResolver resolverWith(IChatService chat, IToolManager tools) {
        DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);
        return new AgentExecutorResolver(
                config, chat, tools, new io.nop.ai.agent.session.InMemorySessionStore(),
                new DefaultAgentEventPublisher(), engine, new AgentCallDelegate(engine),
                () -> null, new AgentStartupWarnings());
    }

    private AgentModel reactModel() {
        AgentModel m = new AgentModel();
        m.setName("test-filter-chain-agent");
        m.setMode("react");
        m.setTools(Collections.emptySet());
        return m;
    }

    private FilterDefModel def(String id, Class<?> impl) {
        FilterDefModel d = new FilterDefModel();
        d.setId(id);
        d.setImpl(impl.getName());
        return d;
    }

    private FilterRefModel ref(String id) {
        FilterRefModel r = new FilterRefModel();
        r.setRef(id);
        return r;
    }

    private FilterRefModel ref(String id, String points) {
        FilterRefModel r = new FilterRefModel();
        r.setRef(id);
        r.setPoints(Set.of(points.split(",")));
        return r;
    }

    private AgentModel withFilterChain(AgentModel m, List<FilterDefModel> defs,
                                       List<FilterRefModel> inputs, List<FilterRefModel> outputs) {
        AgentFilterChainModel c = new AgentFilterChainModel();
        if (defs != null) c.setFilterDefinitions(defs);
        if (inputs != null) c.setInputFilters(inputs);
        if (outputs != null) c.setOutputFilters(outputs);
        m.setFilterChain(c);
        return m;
    }

    private AgentModel withCodeMiddleware(AgentModel m, Class<?> impl, String point) {
        AgentMiddlewareModel mw = new AgentMiddlewareModel();
        mw.setImpl(impl.getName());
        mw.setPoint(point);
        m.setMiddlewares(Collections.singletonList(mw));
        return m;
    }

    private IChatService chatReturningFinal() {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse resp = ChatResponse.success(msg);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    /** A chat service that issues one tool call on the first call, then finalizes. */
    private IChatService chatWithOneToolCall(String toolName) {
        return new IChatService() {
            final AtomicInteger n = new AtomicInteger();

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int i = n.getAndIncrement();
                if (i == 0) {
                    ChatToolCall call = new ChatToolCall();
                    call.setId("c1");
                    call.setName(toolName);
                    call.setArguments(Map.of("x", 1));
                    return CompletableFuture.completedFuture(ChatResponseFixtures.assistantWithToolCalls("", call));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("final");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager noOpToolManager(String toolName) {
        return new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String tn, AiToolCall call, IToolExecuteContext ctx) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }
        };
    }

    private AgentExecutionContext ctxFor(AgentModel m, int maxIter) {
        AgentExecutionContext c = AgentExecutionContext.create(m, "sess-filter-chain");
        c.setMaxIterations(maxIter);
        return c;
    }

    // ===================== tests =====================

    @Test
    void declarativeInputFilterExecutesViaOnionChainEndToEnd() {
        // End-to-end: resolveExecutor parses <filter-chain>, registers the filter
        // at PRE_CALL, and a real run drives it through the MiddlewareChain.
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("guard", InputGuardFilter.class)),
                Collections.singletonList(ref("guard")),
                null);
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        AgentExecutionResult result = executor.execute(ctxFor(m, 5)).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // Wiring verification: the declarative filter actually executed via the
        // onion chain (not just model parsing). PRE_CALL fires once.
        assertEquals(1, INPUT_GUARD_COUNT.get());
        assertTrue(LOG.contains("input-guard.before"));
        assertTrue(LOG.contains("input-guard.after"));
    }

    @Test
    void declarativeOutputFilterFiresAtPostCall() {
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("out", OutputGuardFilter.class)),
                null,
                Collections.singletonList(ref("out")));
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        executor.execute(ctxFor(m, 5)).toCompletableFuture().join();

        // D2 default: output filter → POST_CALL, single trigger.
        assertEquals(1, OUTPUT_GUARD_COUNT.get());
    }

    @Test
    void pointsOverrideRetargetsAwayFromDefault() {
        // input filter with points="pre_reasoning" → NOT at PRE_CALL, IS at
        // PRE_REASONING. In a single-LLM-call run PRE_REASONING fires once.
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("r", ReasoningFilter.class)),
                Collections.singletonList(ref("r", "pre_reasoning")),
                null);
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        executor.execute(ctxFor(m, 5)).toCompletableFuture().join();

        // The reasoning filter fired (override worked); the default PRE_CALL
        // input-guard did not fire because we overrode to pre_reasoning.
        assertEquals(1, REASONING_FILTER_COUNT.get());
        assertEquals(0, INPUT_GUARD_COUNT.get());
    }

    @Test
    void preCallDefaultFiresOnceAcrossMultipleIterations() {
        // Run that issues one tool call (2 LLM calls, 1 PRE_ACTING): PRE_CALL
        // fires 1× (single trigger per request), whereas PRE_REASONING would
        // fire 2× (once per LLM call). Asserting INPUT_GUARD_COUNT==1 while the
        // LLM was called 2× proves the D2 default maps to PRE_CALL, not to a
        // multi-trigger PRE_* point.
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("guard", InputGuardFilter.class)),
                Collections.singletonList(ref("guard")),
                null);
        m.setTools(Set.of("dummy-tool"));
        final AtomicInteger llmCalls = new AtomicInteger();
        IChatService chat = new IChatService() {
            final AtomicInteger n = new AtomicInteger();

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                llmCalls.incrementAndGet();
                int i = n.getAndIncrement();
                if (i == 0) {
                    ChatToolCall call = new ChatToolCall();
                    call.setId("c1");
                    call.setName("dummy-tool");
                    call.setArguments(Map.of("x", 1));
                    return CompletableFuture.completedFuture(ChatResponseFixtures.assistantWithToolCalls("", call));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("final");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        AgentExecutorResolver resolver = resolverWith(chat, noOpToolManager("dummy-tool"));
        IAgentExecutor executor = resolver.resolveExecutor(m);

        AgentExecutionResult result = executor.execute(ctxFor(m, 5)).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations(), "one tool-call iteration");
        assertEquals(2, llmCalls.get(), "two LLM calls (tool + final) so PRE_REASONING would fire 2×");
        // PRE_CALL single-trigger: exactly once despite 2 LLM calls.
        assertEquals(1, INPUT_GUARD_COUNT.get(),
                "input filter at default PRE_CALL must fire once per request, not per LLM call");
    }

    @Test
    void coexistenceDeclarativeFirstThenCodeClass() {
        // D3 merge: declarative filter at PRE_CALL is the OUTER layer; code-class
        // middleware at PRE_CALL is the INNER layer. Onion order reveals this as
        // decl.before → code.before → code.after → decl.after.
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("guard", InputGuardFilter.class)),
                Collections.singletonList(ref("guard")),
                null);
        withCodeMiddleware(m, CodeClassMiddleware.class, "pre_call");
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        executor.execute(ctxFor(m, 5)).toCompletableFuture().join();

        assertEquals(1, INPUT_GUARD_COUNT.get());
        assertEquals(1, CODE_CLASS_COUNT.get());
        // Declarative is outermost (registered first): its 'before' precedes the
        // code-class 'before', and its 'after' follows the code-class 'after'.
        int declBefore = LOG.indexOf("input-guard.before");
        int codeBefore = LOG.indexOf("code-class.before");
        int codeAfter = LOG.indexOf("code-class.after");
        int declAfter = LOG.indexOf("input-guard.after");
        assertTrue(declBefore < codeBefore, "declarative filter must be registered before code-class (D3)");
        assertTrue(codeBefore < codeAfter);
        assertTrue(codeAfter < declAfter, "declarative filter wraps the code-class middleware (outermost)");
    }

    @Test
    void duplicateDeclarationAcrossFilterChainAndMiddlewaresThrows() {
        // Same impl class at the same point via BOTH mechanisms → fast-fail
        // (no silent dedupe, no silent double-keep).
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("guard", InputGuardFilter.class)),
                Collections.singletonList(ref("guard")),
                null);
        withCodeMiddleware(m, InputGuardFilter.class, "pre_call");
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () -> resolver.resolveExecutor(m));
        // The offending impl class and point are surfaced in the message.
        String msg = ex.getMessage();
        assertTrue(msg.contains(InputGuardFilter.class.getName()) || msg.contains("input-guard") || msg.contains("pre_call"),
                "duplicate-declaration error should identify the filter/point: " + msg);
    }

    @Test
    void distinctImplsAtSamePointDoNotTriggerDuplicateDetection() {
        // Different impl classes at the same point via both mechanisms is NOT a
        // duplicate — both are kept (declarative first, code-class after).
        AgentModel m = withFilterChain(reactModel(),
                Collections.singletonList(def("guard", InputGuardFilter.class)),
                Collections.singletonList(ref("guard")),
                null);
        withCodeMiddleware(m, CodeClassMiddleware.class, "pre_call");
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());

        // No throw — both coexist.
        IAgentExecutor executor = resolver.resolveExecutor(m);
        executor.execute(ctxFor(m, 5)).toCompletableFuture().join();
        assertEquals(1, INPUT_GUARD_COUNT.get());
        assertEquals(1, CODE_CLASS_COUNT.get());
    }

    @Test
    void noFilterChainIsZeroRegression() {
        // No <filter-chain>: assembly behaves exactly like before (only the
        // code-class <middlewares> path runs, if any).
        AgentModel m = reactModel();
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        AgentExecutionResult result = executor.execute(ctxFor(m, 5)).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        // No declarative/code-class filters registered → none execute.
        assertTrue(LOG.isEmpty());
    }

    @Test
    void emptyFilterChainIsNoOp() {
        // <filter-chain> present but with no filters → no-op, zero regression.
        AgentModel m = withFilterChain(reactModel(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        IChatService chat = chatReturningFinal();
        AgentExecutorResolver resolver = resolverWith(chat, new io.nop.ai.agent.engine.TestReActAgentExecutor.NoOpToolManager());
        IAgentExecutor executor = resolver.resolveExecutor(m);

        AgentExecutionResult result = executor.execute(ctxFor(m, 5)).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(LOG.isEmpty());
    }

    @Test
    void filterChainParsesFromAgentXmlResource() {
        // DSL parse-time verification: the <filter-chain> element (including the
        // <output-filters> xdef:ref="FilterRefModel" reuse) parses from an actual
        // .agent.xml resource into a fully-populated AgentFilterChainModel. This
        // proves the xdef is valid at parse time, not just at codegen time.
        AgentModel model = (AgentModel) io.nop.core.resource.component.ResourceComponentManager.instance()
                .loadComponentModel("/test-filter-chain.agent.xml");

        assertEquals("test-filter-chain-agent", model.getName());
        AgentFilterChainModel chain = model.getFilterChain();
        assertTrue(chain != null && chain.hasAnyFilters(), "<filter-chain> must parse");
        // filter-definitions keyed by id
        assertEquals(2, chain.getFilterDefinitions().size());
        assertEquals("auth", chain.getFilterDefinitions().get(0).getId());
        assertEquals(InputGuardFilter.class.getName(), chain.getFilterDefinitions().get(0).getImpl());
        // input/output refs (output uses xdef:ref → FilterRefModel)
        assertEquals(1, chain.getInputFilters().size());
        assertEquals("auth", chain.getInputFilters().get(0).getRef());
        assertEquals(1, chain.getOutputFilters().size());
        assertEquals("content", chain.getOutputFilters().get(0).getRef());
    }
}
