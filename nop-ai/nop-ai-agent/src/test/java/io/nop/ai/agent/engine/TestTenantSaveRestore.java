package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.PrincipalRole;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.DBSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.TestSessionStoreConcurrency.H2DataSource;

import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.flow.AllMustSucceedReduction;
import io.nop.ai.agent.team.flow.MemberDispatchOutcome;
import io.nop.ai.agent.team.flow.DispatchTarget;
import io.nop.ai.agent.team.flow.MemberFanOutDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-15 (plan 2555 裁定 E) carrier — tenant save-restore: the engine's
 * tenant-context writes on shared/caller threads must capture-and-RESTORE the
 * previous thread-local value (never an unconditional clear), so an outer
 * thread's tenant context survives cancelSession / forkSession / fan-out
 * completion.
 */
public class TestTenantSaveRestore {

    @AfterEach
    void clearTenant() {
        ThreadLocalTenantResolver.clear();
    }

    private DefaultAgentEngine newEngine(DBSessionStore store) {
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(null, null)
                .sessionStore(store)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new DefaultToolAccessChecker())
                .pathAccessChecker(new DefaultPathAccessChecker())
                .contentGuardrail(NoOpContentGuardrail.noOp())
                .modelRouter(PassThroughModelRouter.passThrough())
                .contextCompactor(new MicroCompressionCompactor())
                .build();
        return engine;
    }

    // ========================================================================
    // cancelSession: outer tenant survives
    // ========================================================================

    @Test
    void cancelSessionRestoresOuterTenant() throws Exception {
        DBSessionStore store = new DBSessionStore(new H2DataSource(),
                ThreadLocalTenantResolver.INSTANCE);
        DefaultAgentEngine engine = newEngine(store);

        ThreadLocalTenantResolver.set("tenant-x");
        AgentSession session = store.getOrCreate("tenant-restore-1", "test-react-agent");
        store.save(session);

        // The cancel call re-sets the thread-local to null internally; with
        // save-restore the outer "tenant-x" must survive the call.
        engine.cancelSession("tenant-restore-1", "restore-check", false).get(10,
                java.util.concurrent.TimeUnit.SECONDS);

        assertEquals("tenant-x", ThreadLocalTenantResolver.current(),
                "cancelSession must RESTORE (not clear) the caller's outer tenant context");
    }

    // ========================================================================
    // forkSession: request tenant applied during, outer tenant restored after
    // ========================================================================

    @Test
    void forkSessionAppliesRequestTenantAndRestoresOuter() throws Exception {
        DataSource ds = new H2DataSource();
        DBSessionStore store = new DBSessionStore(ds, ThreadLocalTenantResolver.INSTANCE);
        DefaultAgentEngine engine = newEngine(store);

        // The parent is persisted under the REQUEST tenant so the fork's
        // tenant-scoped parent lookup can see it.
        ThreadLocalTenantResolver.set("req-t");
        AgentSession parent = store.getOrCreate("fork-parent", "test-react-agent");
        store.save(parent);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "fork me",
                "fork-parent", Collections.emptyMap(), null,
                new Principal(PrincipalRole.USER, "web", "req-t"));
        ThreadLocalTenantResolver.set("outer-t");

        CompletableFuture<String> forked = engine.forkSession(request, true);
        String childId = forked.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(childId);

        assertEquals("outer-t", ThreadLocalTenantResolver.current(),
                "forkSession must RESTORE the caller's outer tenant context");

        // The child must have been persisted under the REQUEST tenant (the
        // set-during-call behaviour still works).
        ThreadLocalTenantResolver.set("req-t");
        AgentSession child = store.get(childId);
        assertNotNull(child, "child session must be persisted under the request tenant");
        ThreadLocalTenantResolver.clear();
    }

    // ========================================================================
    // MemberFanOutDispatcher: completeTask runs under the captured tenant and
    // the completion thread's previous tenant is restored, not cleared
    // ========================================================================

    @Test
    void fanOutCompletionRestoresThreadTenant() throws Exception {
        AtomicReference<String> tenantDuringComplete = new AtomicReference<>();

        ITeamTaskStore taskStore = new ITeamTaskStore() {
            @Override
            public TeamTask createTask(String teamId, String subject, String description,
                                       List<String> blockedBy, String createdBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TeamTask> getTask(String taskId) {
                return Optional.empty();
            }

            @Override
            public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
                // Runs inside the dispatcher's captured-tenant window.
                tenantDuringComplete.set(ThreadLocalTenantResolver.current());
                return Optional.of(new TeamTask(taskId, "team-1", "s", "d",
                        Collections.emptyList(), TeamTaskStatus.COMPLETED,
                        "creator", completedBy, claimEpoch, 1L));
            }

            @Override
            public List<TeamTask> getTasksByTeam(String teamId) {
                return Collections.emptyList();
            }

            @Override
            public List<TeamTask> getTasksByCreator(String createdBy) {
                return Collections.emptyList();
            }

            @Override
            public Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
                throw new UnsupportedOperationException();
            }
        };

        RecordingFanOutEngine fanOutEngine = new RecordingFanOutEngine();

        TeamTask task = new TeamTask("task-1", "team-1", "subject", "desc",
                Collections.emptyList(), TeamTaskStatus.CLAIMED,
                "creator", "dispatcher", 0L, 1L);

        DispatchTarget target = DispatchTarget.bound("member-a", "sess-a", "test-react-agent");

        // Outer tenant value on the completion thread — with an
        // already-completed engine future the whole reduce/complete chain
        // runs synchronously on THIS thread, so the save-restore is directly
        // observable.
        ThreadLocalTenantResolver.set("outer-t");
        CompletableFuture<MemberDispatchOutcome> outcome = MemberFanOutDispatcher.dispatch(
                task, null,
                List.of(target),
                AllMustSucceedReduction.instance(),
                fanOutEngine,
                noopSpawner(),
                taskStore,
                "dispatch-session",
                null,
                "captured-t",
                60_000L);

        MemberDispatchOutcome result = outcome.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(result.isCompleted(), "fan-out must complete: " + result);
        assertEquals("captured-t", tenantDuringComplete.get(),
                "completeTask must run under the captured dispatch tenant");
        assertEquals("outer-t", ThreadLocalTenantResolver.current(),
                "the completion thread's previous tenant must be RESTORED, not cleared");
    }

    private static IMemberSpawner noopSpawner() {
        return io.nop.ai.agent.team.NoOpMemberSpawner.noOp();
    }

    /** Engine double returning an already-completed execution. */
    static final class RecordingFanOutEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            AgentExecutionResult result = new AgentExecutionResult(AgentExecStatus.completed,
                    "done", Collections.emptyList(), 1, 10L, 5L, null);
            return CompletableFuture.completedFuture(result);
        }
    }
}
