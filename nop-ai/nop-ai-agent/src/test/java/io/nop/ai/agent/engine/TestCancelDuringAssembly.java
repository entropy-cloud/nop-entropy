package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 3.1-MA-J (plan 2555 裁定 J / C1-010) carrier — cancel-during-assembly
 * sentinel mutex:
 * <ul>
 *   <li>an execution parked inside {@code registerExecutionSlot} (after the
 *       takeover-lock tryAcquire, before the {@code putIfAbsent}) while a
 *       cancel wins the sentinel: the loser consumes the sentinel, marks its
 *       context cancel-requested, and the duplicate submission fails fast —
 *       the cancel intent is never silently lost;</li>
 *   <li>a second cancel while the sentinel is held is an idempotent
 *       success;</li>
 *   <li>the {@code TestDefaultAgentEngineCancel} ghost semantics (cancel on a
 *       non-existent session throws, idle-session cancel sets cancelled)
 *       remain green with the sentinel path in place (asserted there —
 *       zero assertion changes).</li>
 * </ul>
 */
public class TestCancelDuringAssembly {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Session store that lets the test gate specific calls: getOrCreate can
     * be released through a latch; get can block while the cancel's
     * sentinel-critical section is under observation.
     */
    static final class GatedSessionStore implements ISessionStore {
        final InMemorySessionStore delegate = new InMemorySessionStore();
        final CountDownLatch blockGet = new CountDownLatch(1);
        final AtomicInteger getCalls = new AtomicInteger();

        @Override
        public AgentSession getOrCreate(String sessionId, String agentName) {
            return delegate.getOrCreate(sessionId, agentName);
        }

        @Override
        public AgentSession get(String sessionId) {
            getCalls.incrementAndGet();
            // Block the FIRST get issued by the cancel thread (it enters with
            // the sentinel already placed) until the test releases it.
            if (getCalls.get() == 1) {
                try {
                    blockGet.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted waiting on gated store");
                }
            }
            return delegate.get(sessionId);
        }

        @Override
        public void remove(String sessionId) {
            delegate.remove(sessionId);
        }

        @Override
        public java.util.Collection<AgentSession> getAll() {
            return delegate.getAll();
        }
    }

    /**
     * Takeover lock that parks the execution thread INSIDE tryAcquire (i.e.
     * between the synchronous assembly and the runningExecutions putIfAbsent
     * — exactly the C1-010 assembly window).
     */
    static final class GatedTakeoverLock implements ISessionTakeoverLock {
        final CountDownLatch parkInTryAcquire = new CountDownLatch(1);
        volatile boolean enteredTryAcquire;

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            enteredTryAcquire = true;
            try {
                parkInTryAcquire.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return true;
        }

        @Override
        public boolean isHeld(String sessionId) {
            return false;
        }
    }

    private ListAppender<ILoggingEvent> logAppender;
    private Logger lifecycleLogger;

    private final ExecutorService testPool = Executors.newCachedThreadPool();

    @BeforeEach
    void attachAppender() {
        lifecycleLogger = (Logger) LoggerFactory.getLogger(AgentSessionLifecycle.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        lifecycleLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        lifecycleLogger.detachAppender(logAppender);
        logAppender.stop();
        testPool.shutdownNow();
    }

    private DefaultAgentEngine newEngine(ISessionStore store, ISessionTakeoverLock lock) {
        // The assembly path resolves the executor from the chat service — a
        // trivially-completing stub suffices (never invoked: the execution
        // fails fast at registration in the assembly-window scenario).
        io.nop.ai.api.chat.IChatService chatService = new io.nop.ai.api.chat.IChatService() {
            @Override
            public java.util.concurrent.CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(
                        io.nop.ai.agent.support.ChatResponseFixtures.assistantText("ok"));
            }

            @Override
            public io.nop.ai.api.chat.ChatResponse call(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return io.nop.ai.agent.support.ChatResponseFixtures.assistantText("ok");
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
                io.nop.ai.toolkit.api.IToolManager toolManager = new io.nop.ai.toolkit.api.IToolManager() {
            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                    String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                    io.nop.ai.toolkit.api.IToolExecuteContext context) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        io.nop.ai.toolkit.model.AiToolCallResult.successResult(0, ""));
            }

            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
                return null;
            }

            @Override
            public java.util.List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
                return java.util.Collections.emptyList();
            }

            @Override
            public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
                io.nop.ai.toolkit.model.AiToolModel model = new io.nop.ai.toolkit.model.AiToolModel();
                model.setName(toolName);
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chatService, toolManager)
                .sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setSessionTakeoverLock(lock);
        return engine;
    }

    @Test
    void cancelDuringAssemblyWindowIsNotSilentlyLost() throws Exception {
        GatedSessionStore store = new GatedSessionStore();
        GatedTakeoverLock lock = new GatedTakeoverLock();
        DefaultAgentEngine engine = newEngine(store, lock);

        String sessionId = "assembly-target";
        // The session must exist for the cancel to take the unregistered path
        // (pre-created the way assembleExecutionSetup would).
        store.delegate.getOrCreate(sessionId, "test-react-agent");

        // 1. Park the execution inside registerExecutionSlot's tryAcquire —
        //    the assembly is complete (session + ctx built) but NO handle is
        //    registered yet: the C1-010 TOCTOU window.
        Future<?> executeFuture = testPool.submit(() -> {
            try {
                AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi", sessionId, Map.of());
                engine.execute(request);
                return null;
            } catch (NopAiAgentException e) {
                return e;
            }
        });
        long deadline = System.currentTimeMillis() + 30_000;
        while (!lock.enteredTryAcquire && System.currentTimeMillis() < deadline
                && !executeFuture.isDone()) {
            Thread.sleep(5);
        }
        if (executeFuture.isDone() && !lock.enteredTryAcquire) {
            Object premature = executeFuture.get();
            fail("execution finished BEFORE reaching the gated tryAcquire — assembly threw: "
                    + premature);
        }
        assertTrue(lock.enteredTryAcquire, "execution must reach the gated tryAcquire");

        // 2. Cancel wins the sentinel and parks inside the gated store.get.
        Future<CompletableFuture<Void>> cancelFuture = testPool.submit(
                () -> engine.cancelSession(sessionId, "assembly-cancel", false));
        long waitUntil = System.currentTimeMillis() + 30_000;
        while (store.getCalls.get() == 0 && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(5);
        }
        assertTrue(store.getCalls.get() >= 1,
                "cancel must have entered the sentinel-critical section (store.get)");

        // 3. While the sentinel is held, a SECOND cancel must be an
        //    idempotent success (case ② — no exception, no duplicate
        //    unregistered-cancel semantics).
        CompletableFuture<Void> secondCancel = engine.cancelSession(sessionId, "second", false);
        secondCancel.get(10, TimeUnit.SECONDS);
        assertEquals(1, store.getCalls.get(),
                "the idempotent second cancel must not run its own store-get pass");

        // 4. Release the execution: putIfAbsent loses to the sentinel → the
        //    loser consumes it (ctx setCancelRequested) and fails fast.
        lock.parkInTryAcquire.countDown();
        Object executeOutcome = executeFuture.get(30, TimeUnit.SECONDS);
        assertTrue(executeOutcome instanceof NopAiAgentException,
                "the losing duplicate submission must fail fast with a NopAiAgentException");
        assertTrue(((NopAiAgentException) executeOutcome).getMessage().contains("already executing"),
                "unexpected error: " + executeOutcome);

        // The consumption is observable via the typed log marker.
        boolean consumedLogged = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("cancel-during-assembly"));
        assertTrue(consumedLogged,
                "sentinel consumption must be observable via the cancel-during-assembly marker");

        // 5. Release the cancel: the unregistered semantics run (session
        //    exists → cancelled status + terminal store removal), sentinel
        //    released in the finally.
        store.blockGet.countDown();
        CompletableFuture<Void> cancelResult = cancelFuture.get(30, TimeUnit.SECONDS);
        assertNotNull(cancelResult, "winner cancel must return a completed future");
        assertTrue(cancelResult.isDone(), "winner cancel must complete normally");

        // The cancelled status stays observable through the evicted-terminal
        // side map (the terminal cleanup removed the session from the store).
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus(sessionId),
                "cancelled status must be observable after the assembly-window cancel");
    }

    @Test
    void cancelOnIdleSessionStillSetsCancelledUnderSentinelPath() throws Exception {
        // Regression face of the ghost assertions (TestDefaultAgentEngineCancel
        // owns the unchanged originals): the sentinel winner path must behave
        // byte-identically to the old unregistered branch for the plain
        // idle-session case.
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store, new GatedTakeoverLockNoop());
        AgentSession idle = store.getOrCreate("idle-under-sentinel", "test-agent");
        assertNotNull(idle);

        CompletableFuture<Void> result = engine.cancelSession("idle-under-sentinel", "plain", false);
        result.get(10, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("idle-under-sentinel"),
                "idle-session cancel via the sentinel winner path must set cancelled");

        // Ghost semantics: non-existent session still throws not-found.
        try {
            engine.cancelSession("ghost-under-sentinel", "no-target", false);
            fail("cancelSession must fail-fast for a non-existent session");
        } catch (NopAiAgentException e) {
            // 断言 detail 参数面（lint no-log-getmessage：测试断言非日志）
            Object detail = e.getParams() == null ? null : e.getParams().get("detail");
            assertTrue(String.valueOf(detail).contains("session not found"),
                    "unexpected detail: " + detail);
        }
    }

    /** Lock double that never parks (plain no-op). */
    static final class GatedTakeoverLockNoop implements ISessionTakeoverLock {
        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            return true;
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return true;
        }

        @Override
        public boolean isHeld(String sessionId) {
            return false;
        }
    }
}
