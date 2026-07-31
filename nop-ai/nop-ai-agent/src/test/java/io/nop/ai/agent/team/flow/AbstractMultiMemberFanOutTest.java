package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared fixtures for the Plan 244 (L4-multi-member-per-task-routing)
 * fan-out test classes (MA4.2-06 split): router helpers, recording
 * engine/spawner doubles, team/task builders and CoreInitialization
 * lifecycle. No {@code @Test} methods — concrete scenarios live in
 * {@link TestMultiMemberFanOutSuccess} / {@link TestMultiMemberFanOutFailure}
 * / {@link TestMultiMemberFanOutRouting}.
 */
public abstract class AbstractMultiMemberFanOutTest {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Router helpers — produce multi-target dispatch plans for tests.
    // ========================================================================

    /**
     * Router that returns a fixed dispatch plan for every task (ignoring the
     * team / task data). Used to drive specific fan-out shapes from tests.
     */
    static final class FixedPlanRouter implements ITaskMemberRouter {
        private final java.util.function.Function<TeamTask, MemberDispatchPlan> planFn;

        FixedPlanRouter(java.util.function.Function<TeamTask, MemberDispatchPlan> planFn) {
            this.planFn = planFn;
        }

        @Override
        public MemberDispatchPlan route(Team team, TeamTask task) {
            return planFn.apply(task);
        }
    }

    /**
     * Build a bound dispatch plan over the named bound members of the team.
     */
    static MemberDispatchPlan boundFanOutPlan(Team team, TeamTask task, List<String> memberNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : memberNames) {
            io.nop.ai.agent.team.TeamMember m = team.getMembers().get(name);
            Objects.requireNonNull(m, "bound member not found: " + name);
            assertTrue(m.isBound(), "member not bound: " + name);
            targets.add(DispatchTarget.bound(name, m.getSessionId(), agentModelOf(team, name)));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    /**
     * Build a spawn dispatch plan over the named memberSpecs of the team.
     */
    static MemberDispatchPlan spawnFanOutPlan(Team team, TeamTask task, List<String> memberNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : memberNames) {
            TeamMemberSpec spec = findMemberSpec(team, name);
            targets.add(DispatchTarget.spawn(spec));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    /**
     * Build a mixed dispatch plan (bound + spawn) — the named bound members
     * are bound targets, the named spawn members are spawn targets.
     */
    static MemberDispatchPlan mixedFanOutPlan(Team team, TeamTask task,
                                              List<String> boundNames, List<String> spawnNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : boundNames) {
            io.nop.ai.agent.team.TeamMember m = team.getMembers().get(name);
            Objects.requireNonNull(m, "bound member not found: " + name);
            targets.add(DispatchTarget.bound(name, m.getSessionId(), agentModelOf(team, name)));
        }
        for (String name : spawnNames) {
            targets.add(DispatchTarget.spawn(findMemberSpec(team, name)));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    static MemberDispatchPlan emptyPlan(Team team, TeamTask task) {
        return new MemberDispatchPlan(team, task, Collections.emptyList(),
                AllMustSucceedReduction.instance());
    }

    static TeamMemberSpec findMemberSpec(Team team, String memberName) {
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                return s;
            }
        }
        throw new AssertionError("memberSpec not found: " + memberName);
    }

    static String agentModelOf(Team team, String memberName) {
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                return s.getAgentModel();
            }
        }
        return null;
    }

    // ========================================================================
    // Engine / spawner mocks.
    // ========================================================================

    /**
     * Engine that records per-target wall-clock intervals and peak
     * concurrency so fan-out tests can assert REAL concurrency (not just
     * final COMPLETED status). Returns a completed result for each call.
     */
    static final class ConcurrencyRecordingEngine implements IAgentEngine {
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger executeCount = new AtomicInteger();
        final Map<String, String> memberForTask = new ConcurrentHashMap<>();
        final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            String taskId = (String) request.getMetadata().get("teamTaskId");
            String member = (String) request.getMetadata().get("fanoutMember");
            // Compound key so two members of the same task don't collide.
            String key = taskId + "#" + member;
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            if (member != null) {
                memberForTask.put(key, member);
            }
            threadNames.add(Thread.currentThread().getName());

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                active.decrementAndGet();
                exitNano.put(key, System.nanoTime());
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    /**
     * Engine whose failMember throwing or non-completed status can be
     * configured per (taskId, member) key, to drive honest-failure reduction
     * paths in bound fan-out.
     */
    static final class ConfigurableFanOutEngine implements IAgentEngine {
        final Map<String, FailureKind> failKeys = new ConcurrentHashMap<>();
        final AtomicInteger executeCount = new AtomicInteger();

        enum FailureKind {
            EXCEPTION, NON_COMPLETED
        }

        void failOn(String taskId, String member, FailureKind kind) {
            failKeys.put(taskId + "#" + member, kind);
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            String taskId = (String) request.getMetadata().get("teamTaskId");
            String member = (String) request.getMetadata().get("fanoutMember");
            String key = taskId + "#" + member;
            FailureKind kind = failKeys.get(key);
            if (kind == FailureKind.EXCEPTION) {
                CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("boom:" + key));
                return f;
            }
            if (kind == FailureKind.NON_COMPLETED) {
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "non-completed:" + key));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    /**
     * Spawner that records peak concurrency + intervals (mirrors
     * ConcurrencyRecordingEngine but for the spawn half).
     */
    static final class ConcurrencyRecordingSpawner implements IMemberSpawner {
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final List<String> spawnedMembers = Collections.synchronizedList(new ArrayList<>());
        final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        // Optional per-(taskId,member) failure config.
        final Map<String, SpawnFailure> failKeys = new ConcurrentHashMap<>();

        enum SpawnFailure {
            NO_SPAWN, SPAWN_FAILED, THROWS, NULL, NON_COMPLETED
        }

        void failOn(String taskId, String member, SpawnFailure kind) {
            failKeys.put(taskId + "#" + member, kind);
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            TeamMemberSpec target = request.getTarget();
            String member = target != null ? target.getMemberName() : "?";
            String key = taskId + "#" + member;
            threadNames.add(Thread.currentThread().getName());

            SpawnFailure fail = failKeys.get(key);
            if (fail != null) {
                switch (fail) {
                    case NO_SPAWN:
                        return SpawnMemberResult.noSpawn("declined:" + key);
                    case SPAWN_FAILED:
                        return SpawnMemberResult.spawnFailed("spawn-failed:" + key);
                    case THROWS:
                        throw new NopAiAgentException("spawner-throws:" + key);
                    case NULL:
                        return null;
                    case NON_COMPLETED:
                        return SpawnMemberResult.dispatched(
                                new AgentExecutionResult(AgentExecStatus.failed, null,
                                        Collections.emptyList(), 0, 0L, 0L, "non-completed:" + key),
                                target != null ? target.getAgentModel() : "x",
                                "spawned-" + key);
                    default:
                        // fall through to success
                }
            }

            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            spawnedMembers.add(member);
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException("interrupted", e);
            }
            active.decrementAndGet();
            exitNano.put(key, System.nanoTime());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    target != null ? target.getAgentModel() : "worker-agent-model",
                    "spawned-" + key);
        }
    }

    // ========================================================================
    // Team / task builders.
    // ========================================================================

    /**
     * Create a team with several MEMBER-role memberSpecs but NO members
     * bound (so the NoOp router's spawn fallback would apply, but tests
     * usually inject their own multi-member router).
     */
    static Team createTeamWithMembers(InMemoryTeamManager mgr, String teamName,
                                      String... memberNames) {
        List<TeamMemberSpec> specs = new ArrayList<>();
        specs.add(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD));
        for (String name : memberNames) {
            specs.add(new TeamMemberSpec(name, name + "-agent", MemberRole.MEMBER));
        }
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent", specs, 0);
        return mgr.createTeam(spec);
    }

    /**
     * Create a team and bind the named members to fresh sessions.
     */
    static Team createTeamAndBindMembers(InMemoryTeamManager mgr, String teamName,
                                         String[] memberNames, String[] sessionIds) {
        Team team = createTeamWithMembers(mgr, teamName, memberNames);
        for (int i = 0; i < memberNames.length; i++) {
            mgr.bindMemberSession(team.getTeamId(), memberNames[i], sessionIds[i],
                    "actor-" + memberNames[i]);
        }
        return team;
    }

    static String createTask(ITeamTaskStore store, String teamId,
                             String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }
}
