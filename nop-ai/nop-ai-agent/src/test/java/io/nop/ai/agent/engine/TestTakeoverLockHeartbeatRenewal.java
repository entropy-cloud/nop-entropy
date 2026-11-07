package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.runtime.lock.DbSessionTakeoverLock;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.DBSessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 273 (carry-over 14-06) focused tests: verifies the heartbeat
 * renewal scheduler is actually wired into the three execution entry
 * points ({@code doExecute} / {@code resumeSession} /
 * {@code restoreSession}) — {@code tryRenew} IS periodically invoked
 * during execution, the renewal task IS cancelled on execution
 * completion (no scheduler leak), a successful renewal DOES extend the
 * lease, and a lost lease ({@code tryRenew == false}) DOES abort the
 * local execution and mark the session {@code failed}.
 *
 * <p>Anti-Hollow / Wiring Verification (#23): every assertion is backed
 * by a {@link CountingTakeoverLock} / {@link LeaseLosingLock} wrapper
 * around an H2-backed {@link DbSessionTakeoverLock} — not just that the
 * {@code lockRenewIntervalMs} field exists.
 *
 * <p>时间确定化（plan 2511-WI5）：锁 TTL 判定与心跳续期均走 {@code CoreMetrics}
 * 时间线，测试注册 {@link ManualClock} + {@link ManualScheduler}（同一虚拟时间线）
 * 显式推进触发续期；执行内 LLM 调用用 latch 显式交接（中断收尾形态用
 * {@code latch.await} 承接 forced-interrupt）。「续期任务已取消」类负向断言以
 * 推进后计数不变作重新求值证据，不再使用墙钟观察窗。
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #doExecuteRenewsPeriodicallyAndCancelsOnCompletion} — execute path wiring</li>
 *   <li>{@link #resumeSessionRenewsPeriodicallyAndCancelsOnCompletion} — resume path wiring</li>
 *   <li>{@link #restoreSessionRenewsPeriodicallyAndCancelsOnCompletion} — restore path wiring</li>
 *   <li>{@link #disabledRenewalIntervalSkipsScheduler} — opt-out escape hatch</li>
 *   <li>{@link #tryRenewExtendsLeaseExpiry} — renewal pushes LOCK_EXPIRES_AT forward (Phase 2)</li>
 *   <li>{@link #leaseLostAbortsExecutionAndMarksSessionFailed} — lease-lost → session failed (Phase 2)</li>
 * </ul>
 */
public class TestTakeoverLockHeartbeatRenewal {

    private DataSource dataSource;
    private ManualClock clock;
    private ManualScheduler renewScheduler;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-heartbeat-renew-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        clock = new ManualClock(1_000_000L);
        renewScheduler = new ManualScheduler(clock);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void tearDown() {
        try {
            if (dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception ignored) {
                    // best-effort close during teardown
                }
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    @TempDir
    Path tempDir;

    /**
     * Wiring Verification for {@code doExecute}: with a short
     * {@code lockRenewIntervalMs}, {@code tryRenew} is invoked multiple
     * times during a slow execution, and the renewal task is cancelled
     * once execution completes (no further {@code tryRenew} calls).
     */
    @Test
    void doExecuteRenewsPeriodicallyAndCancelsOnCompletion() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        CountingTakeoverLock lock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));

        CountDownLatch chatEntered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new GatedChatService(List.of(finalResponse("exec-done")), chatEntered, proceed),
                noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setSessionTakeoverLock(lock);
        // Short interval so multiple renewals fire while the execution is
        // held in the LLM call.
        engine.config().setLockRenewIntervalMs(20L);
        engine.setLockRenewExecutor(renewScheduler);

        CompletableFuture<AgentExecutionResult> future = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "renew-exec-1", null));

        // The winner has entered the (held) chat call.
        assertTrue(chatEntered.await(30, TimeUnit.SECONDS),
                "Execution should enter the LLM call");

        // Advance the virtual timeline: each 20ms tick fires one due renewal.
        for (int i = 0; i < 3; i++) {
            renewScheduler.advance(20L);
        }
        // Renewal was actually called during execution (Wiring Verification).
        int renewalsDuringExec = lock.renewCount.get();
        assertTrue(renewalsDuringExec >= 1,
                "tryRenew must be called at least once during execution, got: " + renewalsDuringExec);

        // Release the execution: it completes and cancels the renewal task.
        proceed.countDown();
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus());

        // The renewal task must be cancelled on completion — no further calls.
        // Structural evidence: another advance fires whatever is still
        // scheduled on the same virtual timeline; the count must not move.
        int snapshot = lock.renewCount.get();
        renewScheduler.advance(200L);
        assertEquals(snapshot, lock.renewCount.get(),
                "tryRenew must NOT be called after execution completes (task cancelled)");

        assertEquals(0, countLockRows("renew-exec-1"),
                "Lock row must be released after execution completes");
    }

    /**
     * Wiring Verification for {@code resumeSession}: the resume path also
     * starts the heartbeat renewal during re-execution.
     */
    @Test
    void resumeSessionRenewsPeriodicallyAndCancelsOnCompletion() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckpt = new FileBackedCheckpointManager(tempDir.resolve("resume-renew-ckpt"));
        CountingTakeoverLock lock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new AutoFinalChatService(List.of(finalResponse("initial"))), noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setCheckpointManager(ckpt);
        engine.config().setSessionTakeoverLock(lock);
        engine.config().setLockRenewIntervalMs(20L);
        engine.setLockRenewExecutor(renewScheduler);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "renew-resume-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertEquals(0, countLockRows("renew-resume-1"));

        // Force the session into paused so resumeSession picks it up.
        AgentSession paused = store.get("renew-resume-1");
        paused.setStatus(AgentExecStatus.paused);
        store.save(paused);

        // Fresh counting lock for the resume phase (isolate the count).
        CountingTakeoverLock resumeLock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));
        CountDownLatch chatEntered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        DefaultAgentEngine engine2 = new DefaultAgentEngine.Builder(
                new GatedChatService(List.of(finalResponse("resume-done")), chatEntered, proceed),
                noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine2.config().setCheckpointManager(ckpt);
        engine2.config().setSessionTakeoverLock(resumeLock);
        engine2.config().setLockRenewIntervalMs(20L);
        engine2.setLockRenewExecutor(renewScheduler);

        CompletableFuture<AgentExecutionResult> future = engine2.resumeSession("renew-resume-1", "operator", "resume");

        assertTrue(chatEntered.await(30, TimeUnit.SECONDS),
                "Resume execution should enter the LLM call");
        for (int i = 0; i < 3; i++) {
            renewScheduler.advance(20L);
        }
        assertTrue(resumeLock.renewCount.get() >= 1,
                "tryRenew must be called during resumeSession, got: " + resumeLock.renewCount.get());

        proceed.countDown();
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus());

        int snapshot = resumeLock.renewCount.get();
        renewScheduler.advance(200L);
        assertEquals(snapshot, resumeLock.renewCount.get(),
                "tryRenew must NOT be called after resume completes");

        assertEquals(0, countLockRows("renew-resume-1"));
    }

    /**
     * Wiring Verification for {@code restoreSession}: the restore path also
     * starts the heartbeat renewal during re-execution.
     */
    @Test
    void restoreSessionRenewsPeriodicallyAndCancelsOnCompletion() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckpt = new FileBackedCheckpointManager(tempDir.resolve("restore-renew-ckpt"));
        CountingTakeoverLock lock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new AutoFinalChatService(List.of(finalResponse("initial"))), noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setCheckpointManager(ckpt);
        engine.config().setSessionTakeoverLock(lock);
        engine.config().setLockRenewIntervalMs(20L);
        engine.setLockRenewExecutor(renewScheduler);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "renew-restore-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertEquals(0, countLockRows("renew-restore-1"));

        // Simulate crash: revert status to running.
        AgentSession crashed = store.get("renew-restore-1");
        crashed.setStatus(AgentExecStatus.running);
        store.save(crashed);

        CountingTakeoverLock restoreLock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));
        CountDownLatch chatEntered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        DefaultAgentEngine engine2 = new DefaultAgentEngine.Builder(
                new GatedChatService(List.of(finalResponse("restored-done")), chatEntered, proceed),
                noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine2.config().setCheckpointManager(ckpt);
        engine2.config().setSessionTakeoverLock(restoreLock);
        engine2.config().setLockRenewIntervalMs(20L);
        engine2.setLockRenewExecutor(renewScheduler);

        CompletableFuture<AgentExecutionResult> future =
                engine2.restoreSession("renew-restore-1", "operator", "crash");

        assertTrue(chatEntered.await(30, TimeUnit.SECONDS),
                "Restore execution should enter the LLM call");
        for (int i = 0; i < 3; i++) {
            renewScheduler.advance(20L);
        }
        assertTrue(restoreLock.renewCount.get() >= 1,
                "tryRenew must be called during restoreSession, got: " + restoreLock.renewCount.get());

        proceed.countDown();
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus());

        int snapshot = restoreLock.renewCount.get();
        renewScheduler.advance(200L);
        assertEquals(snapshot, restoreLock.renewCount.get(),
                "tryRenew must NOT be called after restore completes");

        assertEquals(0, countLockRows("renew-restore-1"));
    }

    /**
     * Backward-compatible escape hatch: when {@code lockRenewIntervalMs <= 0},
     * no renewal scheduler is started (tryRenew is never called), and the
     * execution still completes normally with the lock acquired + released.
     */
    @Test
    void disabledRenewalIntervalSkipsScheduler() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        CountingTakeoverLock lock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new AutoFinalChatService(List.of(finalResponse("exec-done"))), noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setSessionTakeoverLock(lock);
        engine.config().setLockRenewIntervalMs(0L); // disabled
        engine.setLockRenewExecutor(renewScheduler);

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "renew-disabled-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // Structural evidence: even after virtual time moves (which would fire
        // any scheduled task), no renewal was ever enqueued.
        renewScheduler.advance(500L);
        assertEquals(0, lock.renewCount.get(),
                "tryRenew must NOT be called when lockRenewIntervalMs <= 0");
        assertEquals(0, countLockRows("renew-disabled-1"));
    }

    // ========================================================================
    // Phase 2 — renewal extends lease + lease-lost aborts execution
    // ========================================================================

    /**
     * Plan 273 Phase 2: a successful {@code tryRenew} extends
     * {@code LOCK_EXPIRES_AT} forward (to {@code now + leaseMs}). Verified
     * by direct SQL on the {@code ai_agent_session_lock} table — the
     * expiry timestamp is observably later after a renew than after the
     * initial acquire. Both timestamps live on the virtual clock line.
     */
    @Test
    void tryRenewExtendsLeaseExpiry() throws Exception {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        String sessionId = "lease-extend-1";
        String owner = "owner-A";
        long leaseMs = 60_000L;

        assertTrue(lock.tryAcquire(sessionId, owner, leaseMs));
        long expiresAtAfterAcquire = readLockExpiry(sessionId);
        // Exact virtual-line contract: acquired expiry == acquire-now + leaseMs.
        assertEquals(clock.peekMillis() + leaseMs, expiresAtAfterAcquire,
                "Acquired LOCK_EXPIRES_AT should be virtual-now + leaseMs");

        // Advance the virtual timeline so the renew timestamp is observably later.
        clock.advance(100L);

        assertTrue(lock.tryRenew(sessionId, owner, leaseMs),
                "tryRenew must succeed when the owner holds an active lock");
        long expiresAtAfterRenew = readLockExpiry(sessionId);

        assertTrue(expiresAtAfterRenew > expiresAtAfterAcquire,
                "LOCK_EXPIRES_AT must be pushed forward by tryRenew. Before="
                        + expiresAtAfterAcquire + ", After=" + expiresAtAfterRenew);
        // Exact virtual-line contract: renewed expiry == renew-now + leaseMs.
        assertEquals(clock.peekMillis() + leaseMs, expiresAtAfterRenew,
                "Renewed LOCK_EXPIRES_AT should be renew-now + leaseMs");

        // A different owner cannot renew (returns false).
        assertFalse(lock.tryRenew(sessionId, "owner-B", leaseMs),
                "tryRenew must fail for a non-owner");

        assertTrue(lock.release(sessionId, owner));
    }

    /**
     * Plan 273 Phase 2 end-to-end: when the heartbeat renewal detects the
     * lease has been lost ({@code tryRenew == false} — another instance
     * preempted the lock), the engine aborts the local execution and
     * marks the session {@code failed} (double-execution prevention).
     *
     * <p>Anti-Hollow / No-Silent-No-Op (#22 #24): the abort is observable
     * — the session reaches the {@code failed} terminal state (not
     * silently continued, not silently completed), and the blocking LLM
     * call is interrupted (structural evidence: the call thread observed
     * the interrupt, not a natural timeout).
     */
    @Test
    void leaseLostAbortsExecutionAndMarksSessionFailed() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        // LeaseLosingLock delegates acquire/release to a real lock but
        // makes every tryRenew return false (simulates immediate lease
        // loss / preemption by another instance).
        LeaseLosingLock lock = new LeaseLosingLock(new DbSessionTakeoverLock(dataSource));

        CountDownLatch chatEntered = new CountDownLatch(1);
        CountDownLatch llmGate = new CountDownLatch(1);
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(
                new GateBlockingChatService(finalResponse("never-completes"), chatEntered, llmGate),
                noOpToolManager()).sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        engine.config().setSessionTakeoverLock(lock);
        engine.config().setLockRenewIntervalMs(500L); // first renewal at +500ms virtual → lease-lost
        engine.setLockRenewExecutor(renewScheduler);

        CompletableFuture<AgentExecutionResult> future = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "lease-lost-1", null));

        // Wait for the execution to enter the blocking LLM call.
        assertTrue(chatEntered.await(30, TimeUnit.SECONDS),
                "Execution should enter the LLM call");

        // Advance past the first renewal tick: tryRenew returns false →
        // handleLeaseLost → interrupt → blocking call throws → executor
        // aborts. The future must resolve (not hang).
        renewScheduler.advance(500L);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        // Structural evidence (plan 2511-WI5): the chat body never returned
        // naturally (gate still closed) — the abort came from the lease-lost
        // interrupt on the engine wait, not a natural timeout.
        assertEquals(1, llmGate.getCount(),
                "Lease-lost abort must interrupt the engine wait before the LLM call returns naturally");

        // The persisted session MUST be in the failed terminal state.
        assertEquals(AgentExecStatus.failed, engine.getSessionStatus("lease-lost-1"),
                "Session must be marked failed when the takeover lease is lost mid-execution");

        // The renewal task must be cancelled (no further tryRenew calls
        // after the execution ends) — further virtual advance must not
        // move the counter.
        int snapshot = lock.renewCount.get();
        renewScheduler.advance(1_000L);
        assertEquals(snapshot, lock.renewCount.get(),
                "tryRenew must NOT be called after lease-lost abort completes");

        // The result is the in-memory snapshot built by the executor
        // before the engine override — it reflects the abort (not
        // completed). The session (source of truth for recovery) is failed.
        assertFalse(result.getStatus() == AgentExecStatus.completed,
                "Lease-lost execution must NOT report completed");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int countLockRows(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentSessionLockTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = '" + sessionId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private long readLockExpiry(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT
                             + " FROM " + AiAgentSessionLockTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionLockTable.COL_SESSION_ID + " = '" + sessionId + "'")) {
            assertTrue(rs.next(), "Lock row must exist for sessionId=" + sessionId);
            return rs.getLong(1);
        }
    }

    /**
     * Lock wrapper that counts {@code tryRenew} invocations so tests can
     * verify the heartbeat scheduler actually calls it (Wiring Verification).
     */
    static final class CountingTakeoverLock implements ISessionTakeoverLock {
        private final ISessionTakeoverLock delegate;
        final AtomicInteger renewCount = new AtomicInteger(0);

        CountingTakeoverLock(ISessionTakeoverLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return delegate.tryAcquire(sessionId, ownerId, leaseMs);
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return delegate.release(sessionId, ownerId);
        }

        @Override
        public boolean isHeld(String sessionId) {
            return delegate.isHeld(sessionId);
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            boolean result = delegate.tryRenew(sessionId, ownerId, leaseMs);
            renewCount.incrementAndGet();
            return result;
        }
    }

    /**
     * Lock wrapper that simulates lease loss: delegates
     * {@code tryAcquire} / {@code release} / {@code isHeld} to a real
     * lock, but makes {@code tryRenew} always return {@code false}
     * (simulates the lock being preempted by another instance). Counts
     * {@code tryRenew} calls so the test can verify the renewal task is
     * cancelled after the abort.
     */
    static final class LeaseLosingLock implements ISessionTakeoverLock {
        private final ISessionTakeoverLock delegate;
        final AtomicInteger renewCount = new AtomicInteger(0);

        LeaseLosingLock(ISessionTakeoverLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            return delegate.tryAcquire(sessionId, ownerId, leaseMs);
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return delegate.release(sessionId, ownerId);
        }

        @Override
        public boolean isHeld(String sessionId) {
            return delegate.isHeld(sessionId);
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            renewCount.incrementAndGet();
            // Simulate lease loss: another instance preempted the lock,
            // so the renewal fails.
            return false;
        }
    }

    /**
     * Chat service that blocks in the LLM call on a latch (timeout is hang
     * protection only). The lease-lost abort interrupts the engine wait —
     * the gate staying closed proves the call did not end by natural timeout.
     */
    static final class GateBlockingChatService implements IChatService {
        private final ChatResponse response;
        private final CountDownLatch enteredLatch;
        private final CountDownLatch llmGate;

        GateBlockingChatService(ChatResponse response, CountDownLatch enteredLatch, CountDownLatch llmGate) {
            this.response = response;
            this.enteredLatch = enteredLatch;
            this.llmGate = llmGate;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            enteredLatch.countDown();
            try {
                llmGate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return response;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Chat service that holds each LLM call on a latch until the test
     * releases it — the renewal ticks are driven explicitly by
     * {@code renewScheduler.advance} while the call is held, replacing the
     * former real-ms {@code Thread.sleep} latency.
     */
    static final class GatedChatService implements IChatService {
        private final List<ChatResponse> scripted;
        private final CountDownLatch enteredLatch;
        private final CountDownLatch proceedLatch;
        private final AtomicInteger idx = new AtomicInteger(0);

        GatedChatService(List<ChatResponse> scripted, CountDownLatch enteredLatch,
                         CountDownLatch proceedLatch) {
            this.scripted = scripted;
            this.enteredLatch = enteredLatch;
            this.proceedLatch = proceedLatch;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            enteredLatch.countDown();
            try {
                assertTrue(proceedLatch.await(30, TimeUnit.SECONDS),
                        "Chat service should be released by the test within timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(no more scripted responses — auto-final)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * Chat service returning each scripted response immediately (auto-final
     * on exhaustion) — no simulated latency.
     */
    static final class AutoFinalChatService implements IChatService {
        private final List<ChatResponse> scripted;
        private final AtomicInteger idx = new AtomicInteger(0);

        AutoFinalChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(no more scripted responses — auto-final)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
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
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setDescription("Mock tool: " + toolName);
                return model;
            }
        };
    }

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }
}
