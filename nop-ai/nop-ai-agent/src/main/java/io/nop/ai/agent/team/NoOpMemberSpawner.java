package io.nop.ai.agent.team;

import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;

/**
 * Shipped no-op default for {@link IMemberSpawner}.
 *
 * <p>{@link #spawnMember} <b>always returns
 * {@link SpawnMemberResult.Status#NO_SPAWN}</b>} — an explicit "auto-spawn
 * not enabled" result. This is the daemon's default out-of-the-box (via
 * {@code TeamTaskSchedulerDaemon}'s null-safe spawner field), so integrators
 * see zero behaviour regression: an unbound-member team-task is abandoned
 * exactly as it was before plan 237 (honest {@code UNBOUND_MEMBER} outcome,
 * No Silent No-Op #24). The functional spawn behaviour is only engaged when
 * an integrator explicitly wires a {@link DefaultMemberSpawner} via
 * {@code TeamTaskSchedulerDaemon.setMemberSpawner(...)} or the spawner-aware
 * constructor.
 *
 * <p><b>Why NO_SPAWN and not DISPATCHED?</b> The NoOp encodes "auto-spawn
 * capability not enabled = do not materialise a member". The daemon folds
 * NO_SPAWN into its pre-spawn {@code UNBOUND_MEMBER} dispatch outcome, so the
 * observable behaviour (task abandoned, count incremented as abandoned not
 * dispatched) is identical to the pre-237 daemon — that is the zero-regression
 * contract. The explicit {@code NO_SPAWN} value (not a swallowed null) is what
 * distinguishes "auto-spawn disabled" from "spawner returned garbage" (Minimum
 * Rules #24).
 *
 * <p>Consistent with the NoOp pattern in
 * {@link io.nop.ai.agent.team.NoOpTeamAclChecker} /
 * {@link io.nop.ai.agent.team.NoOpTeamManager} /
 * {@link io.nop.ai.agent.team.NoOpTeamTaskStore}.
 *
 * <p>See plan 237 ({@code L4-auto-spawn-member-agent}) Phase 1.
 */
public final class NoOpMemberSpawner implements IMemberSpawner {

    private static final NoOpMemberSpawner INSTANCE = new NoOpMemberSpawner();

    private NoOpMemberSpawner() {
    }

    /**
     * @return the singleton NoOp spawner instance
     */
    public static NoOpMemberSpawner noOp() {
        return INSTANCE;
    }

    @Override
    public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
        // Explicit NO_SPAWN: "auto-spawn not enabled → decline to materialise
        // a member". Not a silent skip — the result object is returned, the
        // daemon's UNBOUND_MEMBER path runs honestly (abandon, not silent
        // null). Minimum Rules #24.
        return SpawnMemberResult.noSpawn("NoOpMemberSpawner: auto-spawn not enabled");
    }
}
