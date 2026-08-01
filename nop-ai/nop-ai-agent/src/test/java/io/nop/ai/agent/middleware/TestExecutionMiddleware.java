package io.nop.ai.agent.middleware;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.DefaultAgentEventPublisher;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.ErrorClassification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3-1 (Phase 1): unit tests for execution-level (per-attempt) middleware.
 * Verifies:
 * <ul>
 *   <li>Execution-level middleware is stored/retrieved separately from
 *       session-level middleware (scope isolation)</li>
 *   <li>Execution-level trigger point with no middlewares is a zero-overhead
 *       no-op (returns PassResult, no exception)</li>
 *   <li>Execution-level middleware chain fires outer-to-inner (onion order)</li>
 *   <li>Execution-level middleware Veto is surfaced to the caller (not dropped)</li>
 *   <li>AttemptContext is propagated to the middleware HookContext</li>
 *   <li>NoOpHookRegistry: getExecutionMiddlewares returns empty,
 *       registerExecutionMiddleware throws</li>
 *   <li>resolveExecutionPoint resolves snake_case + enum names, rejects unknown</li>
 *   <li>Session-level 9-point behaviour zero regression (coexistence)</li>
 * </ul>
 */
public class TestExecutionMiddleware {

    static class RecordingMiddleware implements IAgentMiddleware {
        final String name;
        final List<String> log;

        RecordingMiddleware(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            log.add(name + ".before");
            HookResult result = next.proceed(ctx);
            log.add(name + ".after");
            return result;
        }
    }

    static class VetoMiddleware implements IAgentMiddleware {
        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            return new HookResult.VetoResult("blocked-by-execution-middleware");
        }
    }

    /** Records the AttemptContext it sees, then passes through. */
    static class AttemptRecordingMiddleware implements IAgentMiddleware {
        AttemptContext seen;

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            seen = ctx.getAttemptContext();
            return next.proceed(ctx);
        }
    }

    private AgentExecutionContext ctx() {
        AgentExecutionContext c = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        c.setStatus(AgentExecStatus.running);
        return c;
    }

    private AgentHookInvoker invoker(IHookRegistry registry) {
        return new AgentHookInvoker(registry, new DefaultAgentEventPublisher());
    }

    @Test
    void executionMiddlewareStoredAndRetrievedSeparatelyFromSession() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        IAgentMiddleware execMw = new RecordingMiddleware("exec", new ArrayList<>());
        IAgentMiddleware sessionMw = new RecordingMiddleware("session", new ArrayList<>());

        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, execMw);
        registry.registerMiddleware(AgentLifecyclePoint.PRE_REASONING, sessionMw);

        // Execution middleware is reachable via getExecutionMiddlewares
        List<IAgentMiddleware> execList =
                registry.getExecutionMiddlewares(ExecutionPoint.PRE_LLM_ATTEMPT, "agent");
        assertEquals(1, execList.size());
        assertEquals(execMw, execList.get(0));

        // Session middleware is reachable via getMiddlewares (unchanged)
        List<IAgentMiddleware> sessionList =
                registry.getMiddlewares(AgentLifecyclePoint.PRE_REASONING, "agent");
        assertEquals(1, sessionList.size());
        assertEquals(sessionMw, sessionList.get(0));

        // Scope isolation: execution point not visible via session getter and vice-versa
        assertTrue(registry.getMiddlewares(AgentLifecyclePoint.PRE_CALL, "agent").isEmpty());
        assertTrue(registry.getExecutionMiddlewares(ExecutionPoint.POST_LLM_ATTEMPT, "agent").isEmpty());
    }

    @Test
    void executionTriggerWithNoMiddlewaresIsZeroOverheadPassThrough() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        AgentHookInvoker invoker = invoker(registry);

        // No execution middlewares registered -> returns PassResult directly,
        // never throws, never silently skips logic.
        HookResult result = invoker.executeExecutionMiddleware(
                ExecutionPoint.PRE_LLM_ATTEMPT, ctx(),
                new AttemptContext(0, null), "agent", null, null);
        assertTrue(result.isPass());
    }

    @Test
    void executionMiddlewareChainFiresOnionOrder() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new RecordingMiddleware("outer", log));
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new RecordingMiddleware("inner", log));
        AgentHookInvoker invoker = invoker(registry);

        HookResult result = invoker.executeExecutionMiddleware(
                ExecutionPoint.PRE_LLM_ATTEMPT, ctx(),
                new AttemptContext(0, null), "agent", null, null);

        assertTrue(result.isPass());
        // outer.before -> inner.before -> core(pass-through) -> inner.after -> outer.after
        assertEquals(Arrays.asList("outer.before", "inner.before", "inner.after", "outer.after"), log);
    }

    @Test
    void executionMiddlewareVetoIsSurfacedNotDropped() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new VetoMiddleware());
        AgentHookInvoker invoker = invoker(registry);

        HookResult result = invoker.executeExecutionMiddleware(
                ExecutionPoint.PRE_LLM_ATTEMPT, ctx(),
                new AttemptContext(0, null), "agent", null, null);

        // Anti-Hollow: the caller receives the Veto (it must check it).
        assertTrue(result.isVeto());
        assertEquals("blocked-by-execution-middleware", invoker.vetoReason(result));
    }

    @Test
    void attemptContextIsPropagatedToMiddleware() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        AttemptRecordingMiddleware recorder = new AttemptRecordingMiddleware();
        registry.registerExecutionMiddleware(ExecutionPoint.POST_LLM_ATTEMPT, recorder);
        AgentHookInvoker invoker = invoker(registry);

        AttemptContext attemptCtx = new AttemptContext(2, ErrorClassification.TRANSIENT);
        invoker.executeExecutionMiddleware(
                ExecutionPoint.POST_LLM_ATTEMPT, ctx(), attemptCtx, "agent", null, null);

        // D2 contract: middleware can read attempt number, retry flag, and the
        // previous attempt's error classification — the re-evaluation input.
        assertNotNull(recorder.seen);
        assertEquals(2, recorder.seen.getAttempt());
        assertTrue(recorder.seen.isRetry());
        assertEquals(ErrorClassification.TRANSIENT, recorder.seen.getLastErrorClassification());
    }

    @Test
    void attemptContextIsNullOnFirstAttempt() {
        // First attempt with no prior classification: caller may pass null
        AttemptContext first = new AttemptContext(0, null);
        assertFalse(first.isRetry());
        assertEquals(0, first.getAttempt());
        assertNull(first.getLastErrorClassification());
    }

    @Test
    void noOpRegistryExecutionMiddlewaresEmptyAndRegisterThrows() {
        // NoOpHookRegistry: getExecutionMiddlewares returns empty,
        // registerExecutionMiddleware throws (fail-fast, no silent no-op).
        assertTrue(NoOpHookRegistry.INSTANCE
                .getExecutionMiddlewares(ExecutionPoint.PRE_TOOL_ATTEMPT, "agent").isEmpty());
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class, () ->
                NoOpHookRegistry.INSTANCE.registerExecutionMiddleware(
                        ExecutionPoint.PRE_TOOL_ATTEMPT, (c, n) -> n.proceed(c)));
    }

    @Test
    void iHookRegistryDefaultExecutionMethodsAreEmptyAndThrow() {
        // A minimal registry that only implements the original two methods:
        // execution-level defaults must be empty + UnsupportedOperationException.
        IHookRegistry custom = new IHookRegistry() {
            @Override
            public List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName) {
                return List.of();
            }

            @Override
            public void register(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
            }
        };

        assertTrue(custom.getExecutionMiddlewares(ExecutionPoint.PRE_LLM_ATTEMPT, "agent").isEmpty());
        assertThrows(UnsupportedOperationException.class, () ->
                custom.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, (c, n) -> n.proceed(c)));
    }

    @Test
    void resolveExecutionPointAcceptsSnakeAndEnumNames() {
        assertEquals(ExecutionPoint.PRE_LLM_ATTEMPT, DefaultHookRegistry.resolveExecutionPoint("pre_llm_attempt"));
        assertEquals(ExecutionPoint.PRE_LLM_ATTEMPT, DefaultHookRegistry.resolveExecutionPoint("PRE_LLM_ATTEMPT"));
        assertEquals(ExecutionPoint.POST_TOOL_ATTEMPT, DefaultHookRegistry.resolveExecutionPoint("post_tool_attempt"));
        assertNull(DefaultHookRegistry.resolveExecutionPoint("pre_reasoning")); // session point, not execution
        assertNull(DefaultHookRegistry.resolveExecutionPoint("nonexistent"));
        assertNull(DefaultHookRegistry.resolveExecutionPoint(null));
        assertNull(DefaultHookRegistry.resolveExecutionPoint(""));
    }

    @Test
    void sessionLevelMiddlewareBehaviourZeroRegression() {
        // Plan-296 session-level behaviour unchanged: a session middleware at
        // a session point still fires, and execution-level registration does
        // not interfere with it.
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        registry.registerMiddleware(AgentLifecyclePoint.PRE_REASONING, new RecordingMiddleware("session", log));
        // Also register an execution middleware — must not bleed into session path
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new RecordingMiddleware("exec", log));
        AgentHookInvoker invoker = invoker(registry);
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();

        HookResult result = invoker.executeWithMiddleware(
                AgentLifecyclePoint.PRE_REASONING, ctx(), "agent", null, null);

        assertTrue(result.isPass());
        // Only the session middleware fired; execution middleware did not leak
        assertEquals(Arrays.asList("session.before", "session.after"), log);
    }
}
