package io.nop.ai.agent.tool;

import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.AgentSecurityConsultation;
import io.nop.ai.agent.engine.AgentToolDispatcher;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.AgentToolPlanResolver;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-C4-011 regression (plan 2498 Phase 4): the engine's tool dispatch
 * executor must not route blocking tool bodies to the JVM-wide ForkJoin
 * common pool. Pre-fix all three {@code AgentDispatchExecutor} methods used
 * the one-arg {@code CompletableFuture.runAsync} — Bash {@code waitFor} (up
 * to 300s), blocking HTTP and file I/O occupied commonPool workers and
 * starved every other common-pool consumer platform-wide. Post-fix the
 * singleton owns a named bounded module pool ({@code nop-ai-agent-dispatch}).
 */
public class TestAgentDispatchExecutor_C4_011 {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static final class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            IToolExecuteContext context) {
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls,
                                                                IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return List.of();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    /** Mirrors the real-assembly helper of {@code TestToolPhaseSession}. */
    private static AgentToolDispatcher newDispatcher() {
        IToolManager tm = new StubToolManager();
        AgentHookInvoker hookInvoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new AgentToolDispatcher(tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(),
                        new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(),
                        new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(),
                        new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(),
                        new DefaultApprovalGate(),
                        new DefaultDenialLedger(),
                        FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(),
                        new AgentToolPlanResolver(tm),
                        hookInvoker));
    }

    private static String runOnDispatchPool(IThreadPoolExecutor executor) throws Exception {
        CompletableFuture<String> future = executor.submit(() -> Thread.currentThread().getName());
        return future.get(10, TimeUnit.SECONDS);
    }

    private static void assertModulePoolThread(String threadName) {
        assertNotNull(threadName);
        assertTrue(threadName.startsWith("nop-ai-agent-dispatch"),
                "tool body must run on the named module pool, got: " + threadName);
        assertFalse(threadName.contains("ForkJoinPool"),
                "tool body must never land on the JVM common pool, got: " + threadName);
    }

    @Test
    void toolBodiesRunOnModulePoolNotCommonPool_C4_011() throws Exception {
        AgentDispatchExecutor executor = AgentDispatchExecutor.INSTANCE;

        // all three entry points (execute / submit(Callable) / submit(Runnable, result))
        // must route to the module pool, not the common pool
        CompletableFuture<String> viaExecute = new CompletableFuture<>();
        executor.execute(() -> viaExecute.complete(Thread.currentThread().getName()));
        assertModulePoolThread(viaExecute.get(10, TimeUnit.SECONDS));

        assertModulePoolThread(runOnDispatchPool(executor));

        AtomicReference<String> submitRunnableThread = new AtomicReference<>();
        CompletableFuture<String> viaRunnableSubmit = executor.submit(
                () -> submitRunnableThread.set(Thread.currentThread().getName()), "ignored");
        viaRunnableSubmit.get(10, TimeUnit.SECONDS);
        assertModulePoolThread(submitRunnableThread.get());
    }

    @Test
    void dispatchExecutorIsBoundedNamedPool_C4_011() {
        ThreadPoolConfig config = AgentDispatchExecutor.INSTANCE.getConfig();
        assertNotNull(config, "the module pool must expose its bounded config");
        assertEquals("nop-ai-agent-dispatch", config.getName());
        assertTrue(config.getCorePoolSize() >= 1 && config.getMaxPoolSize() >= 1,
                "pool width must be bounded >= 1 (R1-02 factory contract)");
        assertTrue(config.getQueueCapacity() >= 1,
                "queue must be bounded >= 1 (R1-02 factory contract)");
    }

    /**
     * Rule 23 wiring proof: the real dispatch-context assembly point
     * ({@code AgentToolDispatcher.prepareDispatchContext} — the production
     * wiring at :560) hands toolkit tools exactly the module-pool adapter, and
     * bodies submitted through the obtained executor run on
     * {@code nop-ai-agent-dispatch} threads.
     */
    @Test
    void dispatchContextAssemblyWiresModulePool_C4_011() throws Exception {
        AgentToolDispatcher dispatcher = newDispatcher();
        AgentExecutionContext ctx = new AgentExecutionContext(new AgentModel());
        ctx.setStatus(AgentExecStatus.running);

        AgentToolExecuteContext toolExecCtx = dispatcher.prepareDispatchContext(
                ctx, new AgentModel(), null, "c4-011-probe-agent");

        IThreadPoolExecutor executor = toolExecCtx.getExecutor();
        assertNotNull(executor, "the dispatch context executor slot must be non-null (WI25)");
        assertSame(AgentDispatchExecutor.INSTANCE, executor,
                "prepareDispatchContext must wire the module-pool dispatch executor");

        assertModulePoolThread(runOnDispatchPool(executor));
    }
}
