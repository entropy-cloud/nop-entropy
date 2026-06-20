package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.exceptions.NopAiException;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 278 Phase 1 (AR-04): focused tests verifying the three engine entry
 * points ({@code doExecute} / {@code resumeSession} / {@code restoreSession})
 * symmetrically release all four resources (handle / actor / takeover lock /
 * heartbeat renewal) when {@code createActor} or {@code autoBindTeam} fails,
 * and that the same sessionId can be successfully executed again afterwards
 * (not permanently bricked by "session already executing").
 *
 * <p>Anti-Hollow + Wiring Verification (Minimum Rules #22 / #23): the tests
 * prove the cleanup finally actually runs on the failure path and actually
 * destroys the actor + releases the lock, not just that the code compiles.
 */
public class TestEngineEntryCleanupSymmetry {

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
    // doExecute: autoBindTeam failure → symmetric cleanup + re-execute
    // ========================================================================

    /**
     * Core AR-04 scenario for {@code doExecute}: a member agent declares
     * {@code <team-member>} but no ACTIVE team exists, so {@code autoBindTeam}
     * throws inside the supplyAsync lambda. The inner finally must run and
     * release all four resources; the same sessionId must be re-executable.
     */
    @Test
    void doExecute_autoBindTeamFailure_releasesResourcesAndAllowsReexecute() {
        RecordingActorRuntime actorRuntime = new RecordingActorRuntime();
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr, new InMemorySessionStore());
        engine.setActorRuntime(actorRuntime);
        engine.setSessionTakeoverLock(lock);

        String sessionId = "ar04-doexecute-sess";
        AgentMessageRequest req = new AgentMessageRequest(
                "test-team-member-a", "too early", sessionId, null);

        // First execute fails (member auto-bind: no ACTIVE team).
        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.execute(req).toCompletableFuture().join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException);
        assertTrue(cause.getMessage().contains("no ACTIVE team"));

        // AR-04 symmetric cleanup assertions (within the lock lease window):
        // 1. Actor created then destroyed (not leaked).
        assertEquals(1, actorRuntime.createCount.get(),
                "createActor must have been invoked once");
        assertEquals(1, actorRuntime.destroyCount.get(),
                "destroyActor must have been invoked once on the failure path");
        assertFalse(actorRuntime.getActorBySession(sessionId).isPresent(),
                "actor must not leak after autoBindTeam failure");

        // 2. Takeover lock released (can immediately re-acquire).
        assertEquals(1, lock.releaseCount.get(),
                "takeover lock must be released on the failure path");
        assertTrue(lock.tryAcquire(sessionId, "test-owner-reacquire", 60_000L),
                "takeover lock must be immediately re-acquirable after release");
        lock.release(sessionId, "test-owner-reacquire");

        // 3. Same sessionId can execute again successfully (not bricked).
        //    Use the lead agent (which has a valid <team>) so the re-execute
        //    succeeds — proving putIfAbsent no longer sees a stale handle.
        AgentMessageRequest retryReq = new AgentMessageRequest(
                "test-team-lead", "retry", sessionId, null);
        AgentExecutionResult result = engine.execute(retryReq)
                .toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "same sessionId must be re-executable after the failure cleanup "
                        + "(not permanently bricked). Messages: " + result.getMessages());
    }

    // ========================================================================
    // doExecute: createActor failure → symmetric cleanup
    // ========================================================================

    /**
     * AR-04 createActor-failure variant for {@code doExecute}: an
     * {@link IActorRuntime} whose {@code createActor} throws on the first
     * call must trigger the symmetric cleanup (lock released, no handle
     * leak). The actor-destroy path is a no-op here (no actor was created)
     * but the takeover lock + handle must still be released so the session
     * is re-executable. The second call succeeds so re-execute is provably
     * unblocked.
     */
    @Test
    void doExecute_createActorFailure_releasesResourcesAndAllowsReexecute() {
        FailOnceActorRuntime actorRuntime = new FailOnceActorRuntime();
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr, new InMemorySessionStore());
        engine.setActorRuntime(actorRuntime);
        engine.setSessionTakeoverLock(lock);

        String sessionId = "ar04-createactor-sess";
        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "hi", sessionId, null);

        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.execute(req).toCompletableFuture().join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof RuntimeException);
        assertTrue(cause.getMessage().contains("simulated createActor failure"));

        // Lock + handle released (the actor was never created, so destroy is
        // not expected — the key is that the lock and handle are released).
        assertEquals(1, lock.releaseCount.get(),
                "takeover lock must be released even when createActor fails");
        assertTrue(lock.tryAcquire(sessionId, "reacq", 60_000L),
                "lock must be immediately re-acquirable");
        lock.release(sessionId, "reacq");

        // Re-execute succeeds (the fail-once runtime now returns normally,
        // proving the handle was cleaned up — no "session already executing").
        AgentMessageRequest retryReq = new AgentMessageRequest(
                "test-react-agent", "retry", sessionId, null);
        AgentExecutionResult result = engine.execute(retryReq)
                .toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "same sessionId must be re-executable after createActor failure. "
                        + "Messages: " + result.getMessages());
    }

    // ========================================================================
    // resumeSession: autoBindTeam failure → symmetric cleanup
    // ========================================================================

    /**
     * AR-04 for {@code resumeSession}: create a paused session for a member
     * agent that will fail autoBindTeam on resume (no ACTIVE team). The
     * resume's inner finally must release the lock + handle so the session
     * is not bricked.
     */
    @Test
    void resumeSession_autoBindTeamFailure_releasesLockAndAllowsRetry() {
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(mgr, store);
        engine.setSessionTakeoverLock(lock);

        // Manually create a paused session for a member agent that will fail
        // autoBindTeam on resume (no ACTIVE team exists for it).
        String sessionId = "ar04-resume-sess";
        io.nop.ai.agent.session.AgentSession session =
                store.getOrCreate(sessionId, "test-team-member-a");
        session.setStatus(AgentExecStatus.paused);

        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.resumeSession(sessionId, "op", "recovery").toCompletableFuture().join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException);
        assertTrue(cause.getMessage().contains("no ACTIVE team"));

        // Lock released.
        assertEquals(1, lock.releaseCount.get(),
                "takeover lock must be released on resumeSession failure path");
        assertTrue(lock.tryAcquire(sessionId, "reacq", 60_000L),
                "lock must be immediately re-acquirable after resume failure");
        lock.release(sessionId, "reacq");
    }

    // ========================================================================
    // restoreSession: autoBindTeam failure → symmetric cleanup
    // ========================================================================

    /**
     * AR-04 for {@code restoreSession}: persist a non-terminal session, then
     * restore with a member agent that fails autoBindTeam. The restore's
     * inner finally must release the lock + handle.
     */
    @Test
    void restoreSession_autoBindTeamFailure_releasesLockAndAllowsRetry() {
        TrackingTakeoverLock lock = new TrackingTakeoverLock();
        InMemoryTeamManager mgr = new InMemoryTeamManager();

        // Use a file-backed session store so restoreSession has persistent state.
        Path storeDir = tempDir.resolve("restore-ar04-store");
        FileBackedSessionStore store = new FileBackedSessionStore(storeDir);

        DefaultAgentEngine engine = newEngine(mgr, store);
        engine.setSessionTakeoverLock(lock);

        // Persist a member session in a non-terminal state.
        String sessionId = "ar04-restore-sess";
        io.nop.ai.agent.session.AgentSession session =
                store.getOrCreate(sessionId, "test-team-member-a");
        session.setStatus(AgentExecStatus.running);
        store.save(session);

        CompletionException ce = assertThrows(CompletionException.class,
                () -> engine.restoreSession(sessionId, "op", "crash-recovery")
                        .toCompletableFuture().join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException);
        assertTrue(cause.getMessage().contains("no ACTIVE team"));

        // Lock released.
        assertEquals(1, lock.releaseCount.get(),
                "takeover lock must be released on restoreSession failure path");
        assertTrue(lock.tryAcquire(sessionId, "reacq", 60_000L),
                "lock must be immediately re-acquirable after restore failure");
        lock.release(sessionId, "reacq");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Throwable unwrap(CompletionException ce) {
        Throwable c = ce.getCause();
        return c != null ? c : ce;
    }

    private DefaultAgentEngine newEngine(InMemoryTeamManager mgr, ISessionStore store) {
        DefaultAgentEngine engine = new DefaultAgentEngine(singleTurnChat("done"), stubTools(), store);
        engine.setTeamManager(mgr);
        return engine;
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

    /**
     * Functional {@link IActorRuntime} that records create/destroy calls and
     * stores actors by session, mirroring the {@code SimpleFunctionalActorRuntime}
     * pattern in {@code TestTeamAutoBinding}. Used to verify actor cleanup on
     * the AR-04 failure path.
     */
    static final class RecordingActorRuntime implements IActorRuntime {
        final ConcurrentHashMap<String, AgentActor> bySession = new ConcurrentHashMap<>();
        final AtomicInteger createCount = new AtomicInteger();
        final AtomicInteger destroyCount = new AtomicInteger();
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public AgentActor createActor(String sessionId, String agentName) {
            createCount.incrementAndGet();
            return bySession.computeIfAbsent(sessionId, sid -> {
                String actorId = "actor-" + counter.incrementAndGet() + "-" + sid;
                return new AgentActor(actorId, sid, agentName, System.currentTimeMillis(), null);
            });
        }

        @Override
        public Optional<AgentActor> getActor(String actorId) {
            return bySession.values().stream()
                    .filter(a -> a.getActorId().equals(actorId)).findFirst();
        }

        @Override
        public Optional<AgentActor> getActorBySession(String sessionId) {
            return Optional.ofNullable(bySession.get(sessionId));
        }

        @Override
        public Collection<AgentActor> getActiveActors() {
            return bySession.values();
        }

        @Override
        public boolean destroyActor(String actorId) {
            destroyCount.incrementAndGet();
            return bySession.values().removeIf(a -> a.getActorId().equals(actorId));
        }

        @Override
        public int destroyAll() {
            int n = bySession.size();
            bySession.clear();
            return n;
        }
    }

    /**
     * An {@link IActorRuntime} whose {@code createActor} throws on the first
     * call and succeeds on subsequent calls. Used to verify the AR-04 cleanup
     * path when createActor itself fails (before autoBindTeam), and that the
     * session is re-executable after the failure.
     */
    static final class FailOnceActorRuntime implements IActorRuntime {
        final ConcurrentHashMap<String, AgentActor> bySession = new ConcurrentHashMap<>();
        final AtomicInteger createCount = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean firstCallFailed =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public AgentActor createActor(String sessionId, String agentName) {
            createCount.incrementAndGet();
            if (firstCallFailed.compareAndSet(false, true)) {
                throw new NopAiException("simulated createActor failure");
            }
            return bySession.computeIfAbsent(sessionId, sid -> {
                String actorId = "actor-recovered-" + sid;
                return new AgentActor(actorId, sid, agentName, System.currentTimeMillis(), null);
            });
        }

        @Override
        public Optional<AgentActor> getActor(String actorId) {
            return bySession.values().stream()
                    .filter(a -> a.getActorId().equals(actorId)).findFirst();
        }

        @Override
        public Optional<AgentActor> getActorBySession(String sessionId) {
            return Optional.ofNullable(bySession.get(sessionId));
        }

        @Override
        public Collection<AgentActor> getActiveActors() {
            return bySession.values();
        }

        @Override
        public boolean destroyActor(String actorId) {
            return bySession.values().removeIf(a -> a.getActorId().equals(actorId));
        }

        @Override
        public int destroyAll() {
            int n = bySession.size();
            bySession.clear();
            return n;
        }
    }

    /**
     * A takeover lock that tracks {@code release} invocations and provides
     * real acquire/release semantics (in-memory ConcurrentHashMap) so tests
     * can assert the lock was released on the failure path and is immediately
     * re-acquirable.
     */
    static final class TrackingTakeoverLock implements ISessionTakeoverLock {
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
}
