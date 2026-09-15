package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.DefaultWaitCoordinator;
import io.nop.ai.agent.reliability.WaitCondition;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-4 session-lifecycle regression tests (plan P2-ROUND4-SESSION):
 *
 * <p><b>Phase 1 — lock gate</b>: {@code resumeSession} / {@code wakeSession}
 * must not mutate the shared live session (status / denial evidence / wake
 * condition / audit events) BEFORE the takeover lock is acquired. A failed
 * {@code tryAcquire} must leave the cached session in its prior state so a
 * retry after the lock is released works — not bricked as running with the
 * pause evidence already destroyed.
 *
 * <p><b>Phase 3 — tenant context</b>: {@code wakeSession} must re-establish
 * the session's tenant context in both the synchronous phase and the worker
 * lambda (symmetric with {@code resumeSession}) so tenant-scoped DB
 * operations do not silently run with tenant=null.
 *
 * <p>Anti-Hollow: every failure-path assertion verifies concrete observable
 * state (status enum, ledger counts, coordinator satisfied flag, event
 * absence), not merely "throws".
 */
public class TestSessionLifecycleLockGate {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TempDir
    Path tempDir;

    // ========================================================================
    // Phase 1: resumeSession lock gate
    // ========================================================================

    /**
     * Core Phase-1 scenario (InMemory store): another instance holds the
     * takeover lock → resumeSession must fail-fast WITHOUT (a) flipping the
     * cached session to running, (b) resetting the denial ledger (pause
     * evidence destroyed), or (c) publishing a misleading SESSION_RESUMED
     * event. After the lock is released, the retry succeeds — the end-to-end
     * recovery chain stays intact.
     */
    @Test
    void resumeSession_lockHeldByAnother_keepsSessionPausedAndEvidenceIntact() {
        InMemorySessionStore store = new InMemorySessionStore();
        CountingLedger ledger = new CountingLedger(2);
        TrackingTakeoverLock lock = new TrackingTakeoverLock();

        AgentSession session = store.getOrCreate("paused-locked", "test-react-agent");
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        session.setStatus(AgentExecStatus.paused);
        recordDenials(ledger, "paused-locked", 2);
        assertTrue(ledger.isPaused("paused-locked"),
                "precondition: session must be paused before the locked resume attempt");

        DefaultAgentEngine engine = newEngine(ledger, store);
        engine.setSessionTakeoverLock(lock);
        assertTrue(lock.tryAcquire("paused-locked", "other-instance", 60_000L),
                "precondition: another instance holds the takeover lock");

        // Fail-fast semantics unchanged.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("paused-locked", "operator-1", "test"),
                "resumeSession must fail-fast when the lock is held by another instance");
        assertTrue(ex.getMessage().contains("locked by another instance"),
                "Exception must identify the lock conflict. Got: " + ex.getMessage());

        // (b) the cached session stays paused — NOT bricked as running.
        assertEquals(AgentExecStatus.paused, session.getStatus(),
                "failed resume must NOT set the cached session to running "
                        + "(would brick every retry with 'not paused')");
        // (c) the denial evidence is intact — reset never ran.
        assertEquals(0, ledger.resetCount.get(),
                "failed resume must NOT reset the denial ledger");
        assertTrue(ledger.isPaused("paused-locked"),
                "pause evidence must survive a failed resume");
        assertEquals(2, ledger.getDenialCount("paused-locked"),
                "denial count must survive a failed resume");
        // The other instance's lease survives the failed attempt.
        assertTrue(lock.isHeld("paused-locked"),
                "the other instance's lease must survive the failed resume");

        // End-to-end: after the lock is released, the retry succeeds and the
        // pause is cleared exactly once.
        assertTrue(lock.release("paused-locked", "other-instance"));
        AgentExecutionResult result = engine.resumeSession("paused-locked", "operator-1", "test")
                .toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "retry after lock release must complete normally");
        assertEquals(AgentExecStatus.completed, session.getStatus(),
                "session must reach completed after the successful retry");
        assertEquals(1, ledger.resetCount.get(),
                "successful resume must reset the denial ledger exactly once");
        assertFalse(ledger.isPaused("paused-locked"),
                "successful resume must clear the pause");
    }

    /**
     * Same Phase-1 scenario against a {@link FileBackedSessionStore}: the
     * store returns the cached live session object, so a failed resume must
     * leave that cached object paused with intact denial evidence.
     */
    @Test
    void resumeSession_lockHeldByAnother_fileBackedStore_keepsPausedAndEvidenceIntact() {
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("fb-lock-gate"));
        CountingLedger ledger = new CountingLedger(2);
        TrackingTakeoverLock lock = new TrackingTakeoverLock();

        AgentSession session = store.getOrCreate("fb-paused-locked", "test-react-agent");
        session.setStatus(AgentExecStatus.paused);
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        store.save(session);
        recordDenials(ledger, "fb-paused-locked", 2);

        DefaultAgentEngine engine = newEngine(ledger, store);
        engine.setSessionTakeoverLock(lock);
        assertTrue(lock.tryAcquire("fb-paused-locked", "other-instance", 60_000L));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("fb-paused-locked", "operator-1", "test"));
        assertTrue(ex.getMessage().contains("locked by another instance"));

        // The cached live object (FileBackedSessionStore returns the cached
        // session, not a copy) is untouched by the failed attempt.
        AgentSession cached = store.get("fb-paused-locked");
        assertEquals(AgentExecStatus.paused, cached.getStatus(),
                "FileBacked cached session must stay paused after the failed resume");
        assertEquals(0, ledger.resetCount.get(),
                "FileBacked path must not reset the ledger on a failed resume");
        assertEquals(2, ledger.getDenialCount("fb-paused-locked"));

        // Retry succeeds after release.
        assertTrue(lock.release("fb-paused-locked", "other-instance"));
        AgentExecutionResult result = engine.resumeSession("fb-paused-locked", "operator-1", "test")
                .toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(AgentExecStatus.completed, store.get("fb-paused-locked").getStatus());
        assertEquals(1, ledger.resetCount.get());
    }

    /**
     * No misleading audit event on the failure path: a locked resume must not
     * publish SESSION_RESUMED (the event is the success-path signal).
     */
    @Test
    void resumeSession_lockHeldByAnother_publishesNoResumedEvent() {
        InMemorySessionStore store = new InMemorySessionStore();
        CountingLedger ledger = new CountingLedger(2);
        TrackingTakeoverLock lock = new TrackingTakeoverLock();

        AgentSession session = store.getOrCreate("event-locked", "test-react-agent");
        session.setStatus(AgentExecStatus.paused);
        recordDenials(ledger, "event-locked", 2);

        DefaultAgentEngine engine = newEngine(ledger, store);
        engine.setSessionTakeoverLock(lock);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        assertTrue(lock.tryAcquire("event-locked", "other-instance", 60_000L));
        assertThrows(NopAiAgentException.class,
                () -> engine.resumeSession("event-locked", "operator-1", "test"));

        assertFalse(subscriber.hasType(AgentEventType.SESSION_RESUMED),
                "failed resume must NOT publish SESSION_RESUMED (event is the success signal)");

        // Success path publishes exactly one.
        assertTrue(lock.release("event-locked", "other-instance"));
        engine.resumeSession("event-locked", "operator-1", "test").toCompletableFuture().join();
        assertTrue(subscriber.hasType(AgentEventType.SESSION_RESUMED),
                "successful resume must publish SESSION_RESUMED");
    }

    // ========================================================================
    // Phase 1: wakeSession lock gate
    // ========================================================================

    /**
     * Core Phase-1 scenario for {@code wakeSession}: the takeover lock is held
     * by another instance → wakeSession must fail-fast WITHOUT (a) flipping
     * the cached session to running, (b) marking the wait condition satisfied
     * (deliverWake must not run — the wake did not happen), or (c) publishing
     * SESSION_WOKE. Retry after release succeeds.
     */
    @Test
    void wakeSession_lockHeldByAnother_keepsSessionWaitingAndConditionUnsatisfied() {
        InMemorySessionStore store = new InMemorySessionStore();
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator();

        AgentSession session = store.getOrCreate("wait-locked", "test-react-agent");
        session.setStatus(AgentExecStatus.waiting);
        coordinator.requestWait("wait-locked", WaitCondition.event("test-event"));
        assertTrue(coordinator.isWaiting("wait-locked"),
                "precondition: the wait condition must be registered and unsatisfied");

        DefaultAgentEngine engine = newEngine(new CountingLedger(10), store);
        engine.setSessionTakeoverLock(lock);
        engine.setWaitCoordinator(coordinator);
        assertTrue(lock.tryAcquire("wait-locked", "other-instance", 60_000L));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.wakeSession("wait-locked"),
                "wakeSession must fail-fast when the lock is held by another instance");
        assertTrue(ex.getMessage().contains("locked by another instance"),
                "Exception must identify the lock conflict. Got: " + ex.getMessage());

        // (b) the cached session stays waiting — NOT bricked as running.
        assertEquals(AgentExecStatus.waiting, session.getStatus(),
                "failed wake must NOT set the cached session to running "
                        + "(would brick every retry with 'not waiting')");
        // (c) the wake condition is NOT marked satisfied — deliverWake did not run.
        assertTrue(coordinator.isWaiting("wait-locked"),
                "failed wake must NOT mark the wait condition satisfied "
                        + "(deliverWake must not run on the failure path)");
        assertTrue(lock.isHeld("wait-locked"),
                "the other instance's lease must survive the failed wake");

        // End-to-end: after the lock is released, the retry wakes and completes.
        assertTrue(lock.release("wait-locked", "other-instance"));
        AgentExecutionResult result = engine.wakeSession("wait-locked").toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "retry after lock release must wake and complete normally");
        assertFalse(coordinator.isWaiting("wait-locked"),
                "successful wake must consume the wait condition");
    }

    /**
     * No misleading SESSION_WOKE event on the failure path.
     */
    @Test
    void wakeSession_lockHeldByAnother_publishesNoWokeEvent() {
        InMemorySessionStore store = new InMemorySessionStore();
        TrackingTakeoverLock lock = new TrackingTakeoverLock();

        AgentSession session = store.getOrCreate("wake-event-locked", "test-react-agent");
        session.setStatus(AgentExecStatus.waiting);

        DefaultAgentEngine engine = newEngine(new CountingLedger(10), store);
        engine.setSessionTakeoverLock(lock);
        CollectingSubscriber subscriber = new CollectingSubscriber();
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        assertTrue(lock.tryAcquire("wake-event-locked", "other-instance", 60_000L));
        assertThrows(NopAiAgentException.class, () -> engine.wakeSession("wake-event-locked"));

        assertFalse(subscriber.hasType(AgentEventType.SESSION_WOKE),
                "failed wake must NOT publish SESSION_WOKE");

        assertTrue(lock.release("wake-event-locked", "other-instance"));
        engine.wakeSession("wake-event-locked").toCompletableFuture().join();
        assertTrue(subscriber.hasType(AgentEventType.SESSION_WOKE),
                "successful wake must publish SESSION_WOKE");
    }

    // ========================================================================
    // Phase 3: wakeSession tenant context
    // ========================================================================

    /**
     * Phase-3 wiring: a waiting session carrying a tenantId must have its
     * tenant-scoped operations run under that tenant in BOTH the synchronous
     * phase (SESSION_WOKE publication observed with the session tenant active)
     * and the worker lambda (every {@code sessionStore.save} observed with the
     * session tenant active) — never silently with tenant=null.
     */
    @Test
    void wakeSession_runsTenantScopedOperationsWithSessionTenant() {
        InMemorySessionStore delegate = new InMemorySessionStore();
        TenantCapturingStore store = new TenantCapturingStore(delegate);
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator();

        AgentSession session = store.getOrCreate("wake-tenant", "test-react-agent");
        session.setTenantId("tenant-A");
        session.setStatus(AgentExecStatus.waiting);

        DefaultAgentEngine engine = newEngine(new CountingLedger(10), store);
        engine.setSessionTakeoverLock(lock);
        engine.setWaitCoordinator(coordinator);
        TenantCapturingSubscriber subscriber = new TenantCapturingSubscriber();
        ((DefaultAgentEventPublisher) engine.getEventPublisher()).addSubscriber(subscriber);

        engine.wakeSession("wake-tenant").toCompletableFuture().join();

        assertEquals("tenant-A", subscriber.tenantAtSessionWoke,
                "the synchronous-phase SESSION_WOKE publication must run under "
                        + "the session's tenant (not null)");
        assertFalse(store.saveTenants.isEmpty(),
                "the worker must persist the session at least once");
        for (String tenant : store.saveTenants) {
            assertEquals("tenant-A", tenant,
                    "worker-lambda sessionStore.save must run under the session tenant");
        }
    }

    /**
     * Phase-3 hygiene: the synchronous phase must restore the caller's tenant
     * context afterward (no leak across the wakeSession call), symmetric with
     * resumeSession.
     */
    @Test
    void wakeSession_restoresCallerTenantContextAfterSyncPhase() {
        InMemorySessionStore delegate = new InMemorySessionStore();
        TenantCapturingStore store = new TenantCapturingStore(delegate);
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        DefaultWaitCoordinator coordinator = new DefaultWaitCoordinator();

        AgentSession session = store.getOrCreate("wake-tenant-restore", "test-react-agent");
        session.setTenantId("session-tenant");
        session.setStatus(AgentExecStatus.waiting);

        DefaultAgentEngine engine = newEngine(new CountingLedger(10), store);
        engine.setSessionTakeoverLock(lock);
        engine.setWaitCoordinator(coordinator);

        ThreadLocalTenantResolver.set("caller-tenant");
        try {
            engine.wakeSession("wake-tenant-restore").toCompletableFuture().join();
            assertEquals("caller-tenant", ThreadLocalTenantResolver.current(),
                    "wakeSession must restore the caller's tenant context after "
                            + "the synchronous phase (no leak across the call)");
        } finally {
            ThreadLocalTenantResolver.clear();
        }
        for (String tenant : store.saveTenants) {
            assertEquals("session-tenant", tenant,
                    "worker-lambda saves must run under the session tenant");
        }
    }

    // ========================================================================
    // Test-internal functional components
    // ========================================================================

    /**
     * Test-internal functional ledger that mirrors the contract: per-session
     * counting, threshold pause, and a tracked {@code reset} invocation.
     */
    static final class CountingLedger implements IDenialLedger {
        final AtomicInteger resetCount = new AtomicInteger();
        final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        final java.util.Set<String> paused =
                java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        final int threshold;

        CountingLedger(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            String sid = record.getSessionId();
            if (sid == null) {
                return DenialRecordOutcome.of(0, false);
            }
            int c = counts.computeIfAbsent(sid, k -> new AtomicInteger()).incrementAndGet();
            boolean exceeded = c >= threshold;
            if (exceeded) {
                paused.add(sid);
            }
            return DenialRecordOutcome.of(c, exceeded);
        }

        @Override
        public boolean isPaused(String sessionId) {
            return sessionId != null && paused.contains(sessionId);
        }

        @Override
        public int getDenialCount(String sessionId) {
            if (sessionId == null) return 0;
            AtomicInteger c = counts.get(sessionId);
            return c == null ? 0 : c.get();
        }

        @Override
        public void reset(String sessionId) {
            resetCount.incrementAndGet();
            if (sessionId == null) return;
            counts.remove(sessionId);
            paused.remove(sessionId);
        }
    }

    /**
     * A store wrapper that records the {@link ThreadLocalTenantResolver}
     * tenant observed inside every {@code save} call (worker-thread
     * tenant-context wiring evidence).
     */
    static final class TenantCapturingStore implements ISessionStore {
        final ISessionStore delegate;
        final List<String> saveTenants = java.util.Collections.synchronizedList(new ArrayList<>());

        TenantCapturingStore(ISessionStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentSession getOrCreate(String sessionId, String agentName) {
            return delegate.getOrCreate(sessionId, agentName);
        }

        @Override
        public AgentSession get(String sessionId) {
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

        @Override
        public void save(AgentSession session) {
            saveTenants.add(ThreadLocalTenantResolver.current());
            delegate.save(session);
        }
    }

    /**
     * Event subscriber that captures the active tenant at SESSION_WOKE
     * publication time (the synchronous phase of wakeSession).
     */
    static final class TenantCapturingSubscriber implements IAgentEventSubscriber {
        volatile String tenantAtSessionWoke;

        @Override
        public void onEvent(AgentEvent event) {
            if (event.getEventType() == AgentEventType.SESSION_WOKE) {
                tenantAtSessionWoke = ThreadLocalTenantResolver.current();
            }
        }
    }

    /**
     * Event subscriber that captures published events for absence/presence
     * assertions.
     */
    static final class CollectingSubscriber implements IAgentEventSubscriber {
        final List<AgentEvent> events = new ArrayList<>();

        @Override
        public void onEvent(AgentEvent event) {
            events.add(event);
        }

        boolean hasType(AgentEventType t) {
            for (AgentEvent e : events) {
                if (e.getEventType() == t) return true;
            }
            return false;
        }
    }

    /**
     * An {@link ISessionTakeoverLock} with real in-memory acquire/release
     * semantics so tests can simulate another instance holding the lease.
     */
    static final class TrackingTakeoverLock implements ISessionTakeoverLock {
        final ConcurrentHashMap<String, String> heldBy = new ConcurrentHashMap<>();

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            String prev = heldBy.putIfAbsent(sessionId, ownerId);
            return prev == null || prev.equals(ownerId);
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            return heldBy.remove(sessionId, ownerId);
        }

        @Override
        public boolean isHeld(String sessionId) {
            return heldBy.containsKey(sessionId);
        }

        @Override
        public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
            return ownerId.equals(heldBy.get(sessionId));
        }
    }

    private static void recordDenials(CountingLedger ledger, String sessionId, int n) {
        for (int i = 0; i < n; i++) {
            ledger.recordDenial(DenialRecord.of(sessionId, "shell.exec",
                    DenialLayerSource.LAYER1_TOOL_ACCESS, "no", "rule", 1000L + i));
        }
    }

    private static DefaultAgentEngine newEngine(IDenialLedger ledger, ISessionStore store) {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("done"))),
                stubToolManager(),
                store,
                new AllowAllPermissionProvider());
        engine.setDenialLedger(ledger);
        return engine;
    }

    static final class RecordingChatService implements IChatService {
        final List<ChatResponse> scripted;

        RecordingChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            return scripted.get(0);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private static IToolManager stubToolManager() {
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
                return null;
            }
        };
    }
}