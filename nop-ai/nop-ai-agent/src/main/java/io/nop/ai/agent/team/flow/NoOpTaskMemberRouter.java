package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shipped default {@link ITaskMemberRouter} that reproduces the pre-244
 * single-member dispatch behaviour line-for-line (plan 244 /
 * L4-multi-member-per-task-routing, design 裁定 2).
 *
 * <p>For each team task it produces a <b>singleton</b> {@link MemberDispatchPlan}
 * (exactly one {@link DispatchTarget}) — the same member that the pre-244
 * {@code TeamTaskFlowOrchestrator.resolveMember} +
 * {@code DefaultMemberSpawner.resolveSpawnTarget} pair would have selected.
 * This guarantees zero behaviour regression for plans 233/238/241/243: a
 * single-member plan is consumed by the existing single-target node steps
 * (no fan-out), and the same member is selected via the same priority
 * rules.
 *
 * <h2>Selection algorithm (mirrors pre-244 exactly)</h2>
 * <ol>
 *   <li><b>Bound priority (mirrors {@code resolveMember}, plan 233/238)</b>:
 *       if the task is already claimed, use the recorded claimedBy session.
 *       Otherwise prefer the first bound MEMBER-role member; fall back to
 *       the first bound member of any role. A bound hit yields a single
 *       BOUND dispatch target — exactly one element in the plan.</li>
 *   <li><b>Spawn fallback (mirrors {@code DefaultMemberSpawner.resolveSpawnTarget},
 *       plan 237)</b>: when no member is bound, select a single spawn
 *       target from the team's declarative {@code memberSpecs} — prefer
 *       the first MEMBER-role spec, fallback any spec. A spawn hit yields
 *       a single SPAWN dispatch target.</li>
 *   <li><b>Empty plan (honest failure)</b>: when no member is bound AND
 *       the team has no declarative memberSpec, return an empty plan. The
 *       orchestrator converts this into the same honest failure that
 *       pre-244 {@code SpawnMemberAgentTaskStep} produced at run time
 *       (NoOpMemberSpawner NO_SPAWN → throw). The failure shape is
 *       preserved; only the layer that detects it moves from run time
 *       (spawner) to build time (router), which is honest and consistent
 *       with the bound-member path's pre-238 build-time fast-fail.</li>
 * </ol>
 *
 * <p>The spawn-target selection is re-implemented here on the public
 * {@link io.nop.ai.agent.team.TeamSpec#getMemberSpecs()} data (mirroring
 * the same "prefer MEMBER role / fallback any spec" priority), NOT by
 * calling {@code DefaultMemberSpawner.resolveSpawnTarget}. That private
 * method remains as the spawner's own fallback for the daemon path
 * (whose {@code SpawnMemberRequest} carries {@code target == null}), so
 * the daemon route is line-for-line unchanged (plan 244 design 裁定 6).
 *
 * <h2>Reduction</h2>
 * <p>The shipped reduction is {@link AllMustSucceedReduction}. For a
 * singleton plan this is trivially equivalent to the single-member step's
 * existing complete-or-throw semantics (one target → one future → allOf
 * degenerates to that future).
 *
 * <h2>Wire-at-consumer</h2>
 * <p>Singleton; exposed via {@link #noOp()} / {@link #single()} (aliases).
 * The orchestrator null-safe-falls back to this instance when no router
 * is wired.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 2.
 */
public final class NoOpTaskMemberRouter implements ITaskMemberRouter {

    private static final NoOpTaskMemberRouter INSTANCE = new NoOpTaskMemberRouter();

    /**
     * @return the singleton shipped-default single-member router.
     */
    public static NoOpTaskMemberRouter noOp() {
        return INSTANCE;
    }

    /**
     * Alias for {@link #noOp()} — same singleton, exposed under the
     * "single-member" name used in the plan's exit criteria for clarity.
     *
     * @return the singleton shipped-default single-member router.
     */
    public static NoOpTaskMemberRouter single() {
        return INSTANCE;
    }

    private NoOpTaskMemberRouter() {
    }

    @Override
    public MemberDispatchPlan route(Team team, TeamTask task) {
        // 1. Bound priority (mirrors pre-244 resolveMember).
        DispatchTarget bound = resolveBoundTarget(team, task);
        if (bound != null) {
            return new MemberDispatchPlan(team, task,
                    Collections.singletonList(bound),
                    AllMustSucceedReduction.instance());
        }
        // 2. Spawn fallback (mirrors DefaultMemberSpawner.resolveSpawnTarget
        //    priority on the public memberSpecs data).
        DispatchTarget spawn = resolveSpawnTarget(team);
        if (spawn != null) {
            return new MemberDispatchPlan(team, task,
                    Collections.singletonList(spawn),
                    AllMustSucceedReduction.instance());
        }
        // 3. Honest failure: no bound member AND no memberSpec to spawn from.
        //    The orchestrator converts this empty plan into an honest throw
        //    (the task stays CREATED — the same outcome as pre-244's run-time
        //    NoOpMemberSpawner NO_SPAWN throw, only detected earlier).
        return new MemberDispatchPlan(team, task, Collections.emptyList(),
                AllMustSucceedReduction.instance());
    }

    /**
     * Resolve a single bound member for the task (mirrors pre-244
     * {@code TeamTaskFlowOrchestrator.resolveMember} line-for-line).
     * Returns {@code null} when no member is bound.
     */
    private DispatchTarget resolveBoundTarget(Team team, TeamTask task) {
        Map<String, TeamMember> members = team.getMembers();

        if (task.getClaimedBy() != null) {
            for (TeamMember m : members.values()) {
                if (task.getClaimedBy().equals(m.getSessionId())) {
                    return DispatchTarget.bound(m.getMemberName(), m.getSessionId(),
                            agentModelOf(team, m.getMemberName()));
                }
            }
            return DispatchTarget.bound("claimed", task.getClaimedBy(), null);
        }

        TeamMember fallback = null;
        for (TeamMember m : members.values()) {
            if (!m.isBound()) {
                continue;
            }
            if (m.getRole() == MemberRole.MEMBER) {
                return DispatchTarget.bound(m.getMemberName(), m.getSessionId(),
                        agentModelOf(team, m.getMemberName()));
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        if (fallback != null) {
            return DispatchTarget.bound(fallback.getMemberName(), fallback.getSessionId(),
                    agentModelOf(team, fallback.getMemberName()));
        }
        return null;
    }

    /**
     * Resolve a single spawn target from the team's declarative memberSpecs
     * (mirrors pre-244 {@code DefaultMemberSpawner.resolveSpawnTarget}
     * priority on the public {@code memberSpecs} data). Returns {@code null}
     * when the team has no declarative memberSpec.
     */
    private DispatchTarget resolveSpawnTarget(Team team) {
        if (team.getSpec() == null) {
            return null;
        }
        List<TeamMemberSpec> specs = team.getSpec().getMemberSpecs();
        if (specs == null || specs.isEmpty()) {
            return null;
        }
        TeamMemberSpec fallback = null;
        for (TeamMemberSpec ms : specs) {
            if (ms.getRole() == MemberRole.MEMBER) {
                return DispatchTarget.spawn(ms);
            }
            if (fallback == null) {
                fallback = ms;
            }
        }
        if (fallback != null) {
            return DispatchTarget.spawn(fallback);
        }
        return null;
    }

    private String agentModelOf(Team team, String memberName) {
        if (team.getSpec() == null) {
            return null;
        }
        List<TeamMemberSpec> specs = team.getSpec().getMemberSpecs();
        if (specs == null) {
            return null;
        }
        for (TeamMemberSpec spec : specs) {
            if (spec.getMemberName().equals(memberName)) {
                return spec.getAgentModel();
            }
        }
        return null;
    }
}
