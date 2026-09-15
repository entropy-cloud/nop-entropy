package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.IDenialLedger;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-4 session-lifecycle regression tests (plan P2-ROUND4-SESSION,
 * Phase 2 — save-before-release):
 *
 * <p>All four execution paths ({@code doExecute} / {@code resumeSession} /
 * {@code wakeSession} / {@code restoreSession}) must persist the terminal
 * session state BEFORE releasing the takeover lease — no "unlocked but stale
 * persisted state" window in which a crash or save failure leaves another
 * instance free to resume a stale session and re-execute the same user turn.
 *
 * <p>A save failure on the terminal path must (a) propagate the error to the
 * caller (no silent success), and (b) still release the lease (no permanent
 * lock).
 *
 * <p>Anti-Hollow: ordering is asserted via a shared sequence counter recorded
 * by a store wrapper (save) and a lock wrapper (release); failure propagation
 * is asserted on the caller-visible future.
 */
public class TestSessionLifecycleSaveBeforeRelease {

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
    // Save-before-release ordering on the four execution paths
    // ========================================================================

    /**
     * doExecute: the final {@code sessionStore.save} must be sequenced before
     * the takeover-lease {@code release}.
     */
    @Test
    void doExecute_savesBeforeReleasingLease() {
        AtomicInteger seq = new AtomicInteger();
        SequenceRecordingStore store = new SequenceRecordingStore(new InMemorySessionStore(), seq);
        SequenceTrackingLock lock = new SequenceTrackingLock(seq);

        DefaultAgentEngine engine = newEngine(store);
        engine.setSessionTakeoverLock(lock);

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi", "order-doexecute", null))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertOrdering(store.lastSaveSeq, lock.releaseSeq);
    }

    /**
     * resumeSession: same ordering on the recovery path.
     */
    @Test
    void resumeSession_savesBeforeReleasingLease() {
        AtomicInteger seq = new AtomicInteger();
        SequenceRecordingStore store = new SequenceRecordingStore(new InMemorySessionStore(), seq);
        SequenceTrackingLock lock = new SequenceTrackingLock(seq);

        AgentSession session = store.getOrCreate("order-resume", "test-react-agent");
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        session.setStatus(AgentExecStatus.paused);

        DefaultAgentEngine engine = newEngine(store);
        engine.setSessionTakeoverLock(lock);

        AgentExecutionResult result = engine.resumeSession("order-resume", "operator-1", "test")
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertOrdering(store.lastSaveSeq, lock.releaseSeq);
    }

    /**
     * wakeSession: same ordering on the WAIT_FOR wake path.
     */
    @Test
    void wakeSession_savesBeforeReleasingLease() {
        AtomicInteger seq = new AtomicInteger();
        SequenceRecordingStore store = new SequenceRecordingStore(new InMemorySessionStore(), seq);
        SequenceTrackingLock lock = new SequenceTrackingLock(seq);

        AgentSession session = store.getOrCreate("order-wake", "test-react-agent");
        session.setStatus(AgentExecStatus.waiting);

        DefaultAgentEngine engine = newEngine(store);
        engine.setSessionTakeoverLock(lock);

        AgentExecutionResult result = engine.wakeSession("order-wake").toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertOrdering(store.lastSaveSeq, lock.releaseSeq);
    }

    /**
     * restoreSession: same ordering on the crash-restore path (file-backed
     * store so the restore protocol has persistent state).
     */
    @Test
    void restoreSession_savesBeforeReleasingLease() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        SequenceRecordingStore store = new SequenceRecordingStore(
                new FileBackedSessionStore(tempDir.resolve("order-restore-store")), seq);
        SequenceTrackingLock lock = new SequenceTrackingLock(seq);
        FileBackedCheckpointManager ckpt = new FileBackedCheckpointManager(tempDir.resolve("order-restore-ckpt"));

        // Seed a crashed (running) session via a first engine.
        DefaultAgentEngine seedEngine = newEngine(store);
        seedEngine.setCheckpointManager(ckpt);
        seedEngine.execute(new AgentMessageRequest("test-react-agent", "hi", "order-restore", null))
                .toCompletableFuture().join();
        AgentSession crashed = store.get("order-restore");
        crashed.setStatus(AgentExecStatus.running);
        store.save(crashed);

        // Restore with a fresh engine — the recording store/lock are shared.
        DefaultAgentEngine engine2 = newEngine(store);
        engine2.setCheckpointManager(ckpt);
        engine2.setSessionTakeoverLock(lock);

        AgentExecutionResult result = engine2.restoreSession("order-restore", "operator-1", "crash")
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertOrdering(store.lastSaveSeq, lock.releaseSeq);
    }

    // ========================================================================
    // Save failure: explicit error propagation + lease still released
    // ========================================================================

    /**
     * doExecute with a terminal-save failure (disk-full simulation): the
     * caller must receive the save error (no silent success) and the lease
     * must still be released (no permanently-held lock).
     */
    @Test
    void doExecute_saveFailure_propagatesErrorAndStillReleasesLock() {
        FailOnTerminalSaveStore store = new FailOnTerminalSaveStore(new InMemorySessionStore());
        TrackingReleaseLock lock = new TrackingReleaseLock();

        DefaultAgentEngine engine = newEngine(store);
        engine.setSessionTakeoverLock(lock);

        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.execute(new AgentMessageRequest("test-react-agent", "hi", "fail-save", null))
                        .toCompletableFuture().join(),
                "a terminal save failure must surface to the caller (no silent success)");
        Throwable cause = unwrap(ce);
        assertTrue(cause.getMessage().contains("simulated save failure"),
                "the propagated error must be the save failure. Got: " + cause.getMessage());

        assertEquals(1, lock.releaseCount.get(),
                "the lease must be released even when the terminal save fails "
                        + "(no permanently-held lock)");
        assertTrue(lock.tryAcquire("fail-save", "reacquire", 60_000L),
                "the lease must be immediately re-acquirable after the failed save");
    }

    /**
     * resumeSession with a terminal-save failure: same contract — error
     * propagation + lease release.
     */
    @Test
    void resumeSession_saveFailure_propagatesErrorAndStillReleasesLock() {
        FailOnTerminalSaveStore store = new FailOnTerminalSaveStore(new InMemorySessionStore());
        TrackingReleaseLock lock = new TrackingReleaseLock();

        AgentSession session = store.getOrCreate("fail-save-resume", "test-react-agent");
        session.appendMessages(List.of(new ChatUserMessage("hi")));
        session.setStatus(AgentExecStatus.paused);

        DefaultAgentEngine engine = newEngine(store);
        engine.setSessionTakeoverLock(lock);

        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.resumeSession("fail-save-resume", "operator-1", "test")
                        .toCompletableFuture().join(),
                "a terminal save failure on resume must surface to the caller");
        Throwable cause = unwrap(ce);
        assertTrue(cause.getMessage().contains("simulated save failure"),
                "the propagated error must be the save failure. Got: " + cause.getMessage());

        assertEquals(1, lock.releaseCount.get(),
                "the lease must be released even when the terminal save fails");
        assertTrue(lock.tryAcquire("fail-save-resume", "reacquire", 60_000L),
                "the lease must be immediately re-acquirable after the failed save");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static void assertOrdering(int lastSaveSeq, int releaseSeq) {
        assertTrue(lastSaveSeq > 0,
                "the success path must have persisted the session at least once");
        assertTrue(releaseSeq > 0,
                "the success path must have released the lease exactly once");
        assertTrue(lastSaveSeq < releaseSeq,
                "the terminal sessionStore.save must be sequenced BEFORE the lease "
                        + "release (saveSeq=" + lastSaveSeq + ", releaseSeq=" + releaseSeq + ")");
    }

    private static Throwable unwrap(CompletionException ce) {
        Throwable c = ce.getCause();
        return c != null ? c : ce;
    }

    private DefaultAgentEngine newEngine(ISessionStore store) {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                singleTurnChat("done"),
                stubTools(),
                store,
                new AllowAllPermissionProvider());
        engine.setDenialLedger(new NoPauseLedger());
        return engine;
    }

    /**
     * A ledger that never pauses (so resume/wake tests need no denial
     * seeding).
     */
    static final class NoPauseLedger implements IDenialLedger {
        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            return DenialRecordOutcome.of(0, false);
        }

        @Override
        public boolean isPaused(String sessionId) {
            return false;
        }

        @Override
        public int getDenialCount(String sessionId) {
            return 0;
        }

        @Override
        public void reset(String sessionId) {
        }
    }

    /**
     * Store wrapper that records the sequence number of the LAST save via a
     * shared counter.
     */
    static final class SequenceRecordingStore implements ISessionStore {
        final ISessionStore delegate;
        final AtomicInteger seq;
        volatile int lastSaveSeq;

        SequenceRecordingStore(ISessionStore delegate, AtomicInteger seq) {
            this.delegate = delegate;
            this.seq = seq;
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
            lastSaveSeq = seq.incrementAndGet();
            delegate.save(session);
        }
    }

    /**
     * Takeover lock that records the release sequence via the same shared
     * counter as the store wrapper.
     */
    static final class SequenceTrackingLock implements ISessionTakeoverLock {
        final ConcurrentHashMap<String, String> heldBy = new ConcurrentHashMap<>();
        final AtomicInteger seq;
        volatile int releaseSeq;

        SequenceTrackingLock(AtomicInteger seq) {
            this.seq = seq;
        }

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            String prev = heldBy.putIfAbsent(sessionId, ownerId);
            return prev == null || prev.equals(ownerId);
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            releaseSeq = seq.incrementAndGet();
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

    /**
     * Store wrapper that throws on the terminal save (status already
     * terminal), simulating a disk-full / save failure at the final
     * persistence point.
     */
    static final class FailOnTerminalSaveStore implements ISessionStore {
        final ISessionStore delegate;

        FailOnTerminalSaveStore(ISessionStore delegate) {
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
            if (AgentSessionLifecycle.isTerminalStatus(session.getStatus())) {
                throw new io.nop.ai.agent.engine.NopAiAgentException(
                        "simulated save failure (disk full): terminal state not persisted");
            }
            delegate.save(session);
        }
    }

    /**
     * Takeover lock tracking release invocations with real in-memory
     * semantics.
     */
    static final class TrackingReleaseLock implements ISessionTakeoverLock {
        final ConcurrentHashMap<String, String> heldBy = new ConcurrentHashMap<>();
        final AtomicInteger releaseCount = new AtomicInteger();

        @Override
        public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
            String prev = heldBy.putIfAbsent(sessionId, ownerId);
            return prev == null || prev.equals(ownerId);
        }

        @Override
        public boolean release(String sessionId, String ownerId) {
            releaseCount.incrementAndGet();
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

    private static IChatService singleTurnChat(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        ChatResponse response = ChatResponse.success(msg);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static IToolManager stubTools() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
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
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
    }
}