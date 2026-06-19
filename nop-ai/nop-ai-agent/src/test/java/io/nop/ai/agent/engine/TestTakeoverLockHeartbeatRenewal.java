package io.nop.ai.agent.engine;

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
import io.nop.api.core.util.ICancelToken;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
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
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
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

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("exec-done")), 150L),
                noOpToolManager(),
                store);
        engine.setSessionTakeoverLock(lock);
        // Short interval so multiple renewals fire during the 150ms execution.
        engine.setLockRenewIntervalMs(20L);

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "renew-exec-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // Renewal was actually called during execution (Wiring Verification).
        int renewalsDuringExec = lock.renewCount.get();
        assertTrue(renewalsDuringExec >= 1,
                "tryRenew must be called at least once during execution, got: " + renewalsDuringExec);

        // The renewal task must be cancelled on completion — no further calls.
        int snapshot = lock.renewCount.get();
        Thread.sleep(120L);
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

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("initial")), 50L),
                noOpToolManager(),
                store);
        engine.setCheckpointManager(ckpt);
        engine.setSessionTakeoverLock(lock);
        engine.setLockRenewIntervalMs(20L);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "renew-resume-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertEquals(0, countLockRows("renew-resume-1"));

        // Force the session into paused so resumeSession picks it up.
        AgentSession paused = store.get("renew-resume-1");
        paused.setStatus(AgentExecStatus.paused);
        store.save(paused);

        // Fresh counting lock for the resume phase (isolate the count).
        CountingTakeoverLock resumeLock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));
        DefaultAgentEngine engine2 = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("resume-done")), 150L),
                noOpToolManager(),
                store);
        engine2.setCheckpointManager(ckpt);
        engine2.setSessionTakeoverLock(resumeLock);
        engine2.setLockRenewIntervalMs(20L);

        AgentExecutionResult result = engine2.resumeSession("renew-resume-1", "operator", "resume")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(resumeLock.renewCount.get() >= 1,
                "tryRenew must be called during resumeSession, got: " + resumeLock.renewCount.get());

        int snapshot = resumeLock.renewCount.get();
        Thread.sleep(120L);
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

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("initial")), 50L),
                noOpToolManager(),
                store);
        engine.setCheckpointManager(ckpt);
        engine.setSessionTakeoverLock(lock);
        engine.setLockRenewIntervalMs(20L);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "renew-restore-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertEquals(0, countLockRows("renew-restore-1"));

        // Simulate crash: revert status to running.
        AgentSession crashed = store.get("renew-restore-1");
        crashed.setStatus(AgentExecStatus.running);
        store.save(crashed);

        CountingTakeoverLock restoreLock = new CountingTakeoverLock(new DbSessionTakeoverLock(dataSource));
        DefaultAgentEngine engine2 = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("restored-done")), 150L),
                noOpToolManager(),
                store);
        engine2.setCheckpointManager(ckpt);
        engine2.setSessionTakeoverLock(restoreLock);
        engine2.setLockRenewIntervalMs(20L);

        AgentExecutionResult result = engine2.restoreSession("renew-restore-1", "operator", "crash")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(restoreLock.renewCount.get() >= 1,
                "tryRenew must be called during restoreSession, got: " + restoreLock.renewCount.get());

        int snapshot = restoreLock.renewCount.get();
        Thread.sleep(120L);
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

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new SlowChatService(List.of(finalResponse("exec-done")), 80L),
                noOpToolManager(),
                store);
        engine.setSessionTakeoverLock(lock);
        engine.setLockRenewIntervalMs(0L); // disabled

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "renew-disabled-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
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
     * initial acquire.
     */
    @Test
    void tryRenewExtendsLeaseExpiry() throws Exception {
        DbSessionTakeoverLock lock = new DbSessionTakeoverLock(dataSource);
        String sessionId = "lease-extend-1";
        String owner = "owner-A";
        long leaseMs = 60_000L;

        assertTrue(lock.tryAcquire(sessionId, owner, leaseMs));
        long expiresAtAfterAcquire = readLockExpiry(sessionId);
        long acquiredAt = System.currentTimeMillis();
        // Sanity: the acquired expiry is ~ now + leaseMs.
        assertTrue(expiresAtAfterAcquire >= acquiredAt + leaseMs - 5_000L
                && expiresAtAfterAcquire <= acquiredAt + leaseMs + 5_000L,
                "Acquired LOCK_EXPIRES_AT should be ~ now + leaseMs, got: " + expiresAtAfterAcquire);

        // Sleep so the renew timestamp is observably later.
        Thread.sleep(60L);

        assertTrue(lock.tryRenew(sessionId, owner, leaseMs),
                "tryRenew must succeed when the owner holds an active lock");
        long expiresAtAfterRenew = readLockExpiry(sessionId);
        long renewedAt = System.currentTimeMillis();

        assertTrue(expiresAtAfterRenew > expiresAtAfterAcquire,
                "LOCK_EXPIRES_AT must be pushed forward by tryRenew. Before="
                        + expiresAtAfterAcquire + ", After=" + expiresAtAfterRenew);
        assertTrue(expiresAtAfterRenew >= renewedAt + leaseMs - 5_000L
                && expiresAtAfterRenew <= renewedAt + leaseMs + 5_000L,
                "Renewed LOCK_EXPIRES_AT should be ~ renew-now + leaseMs, got: " + expiresAtAfterRenew);

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
     * silently continued, not silently completed), and the execution
     * terminates promptly (not left running).
     */
    @Test
    void leaseLostAbortsExecutionAndMarksSessionFailed() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        // LeaseLosingLock delegates acquire/release to a real lock but
        // makes every tryRenew return false (simulates immediate lease
        // loss / preemption by another instance).
        LeaseLosingLock lock = new LeaseLosingLock(new DbSessionTakeoverLock(dataSource));

        CountDownLatch chatEntered = new CountDownLatch(1);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new BlockingInterruptibleChatService(finalResponse("never-completes"), chatEntered),
                noOpToolManager(),
                store);
        engine.setSessionTakeoverLock(lock);
        engine.setLockRenewIntervalMs(20L); // first renewal at ~20ms → lease-lost

        long startNs = System.nanoTime();
        CompletableFuture<AgentExecutionResult> future = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "lease-lost-1", null));

        // Wait for the execution to enter the blocking LLM call.
        assertTrue(chatEntered.await(30, TimeUnit.SECONDS),
                "Execution should enter the LLM call");

        // The first heartbeat renewal (at ~20ms) returns false →
        // handleLeaseLost → interrupt → blocking call throws → executor
        // aborts. The future must resolve promptly (not hang for 30s).
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertTrue(elapsedMs < 30_000L,
                "Lease-lost abort should terminate far sooner than the 30s block, took " + elapsedMs + "ms");

        // The persisted session MUST be in the failed terminal state.
        assertEquals(AgentExecStatus.failed, engine.getSessionStatus("lease-lost-1"),
                "Session must be marked failed when the takeover lease is lost mid-execution");

        // The renewal task must be cancelled (no further tryRenew calls
        // after the execution ends).
        int snapshot = lock.renewCount.get();
        Thread.sleep(120L);
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
     * Chat service that blocks in the LLM call until interrupted (mirrors
     * the {@code createBlockingChatService} pattern in
     * {@code TestDefaultAgentEngineCancel}). On interrupt it throws so the
     * ReAct executor's catch path handles the abort — used to verify the
     * lease-lost interrupt actually breaks a long blocking call.
     */
    static final class BlockingInterruptibleChatService implements IChatService {
        private final ChatResponse response;
        private final CountDownLatch enteredLatch;

        BlockingInterruptibleChatService(ChatResponse response, CountDownLatch enteredLatch) {
            this.response = response;
            this.enteredLatch = enteredLatch;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            enteredLatch.countDown();
            try {
                Thread.sleep(30_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException("interrupted during blocking LLM call (lease-lost)", e);
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
     * Chat service that sleeps {@code delayMs} before returning each
     * scripted response, so the heartbeat scheduler has time to fire
     * multiple renewal ticks during a single execution.
     */
    static final class SlowChatService implements IChatService {
        private final List<ChatResponse> scripted;
        private final long delayMs;
        private final AtomicInteger idx = new AtomicInteger(0);

        SlowChatService(List<ChatResponse> scripted, long delayMs) {
            this.scripted = scripted;
            this.delayMs = delayMs;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            try {
                Thread.sleep(delayMs);
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
