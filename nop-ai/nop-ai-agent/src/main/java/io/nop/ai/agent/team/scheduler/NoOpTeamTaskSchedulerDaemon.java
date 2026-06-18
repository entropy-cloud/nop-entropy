package io.nop.ai.agent.team.scheduler;

/**
 * Shipped no-op default for {@link ITeamTaskSchedulerDaemon}.
 *
 * <p>Used by {@code DefaultAgentEngine} out-of-the-box: deployments that do
 * not opt into unattended auto-scheduling keep relying on explicit / program-
 * matic / LLM-driven invocation of
 * {@link io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator}, so engine
 * behaviour is unchanged. Integrators opt into unattended auto-scheduling by
 * wiring a functional daemon (e.g. {@link TeamTaskSchedulerDaemon}) via
 * {@code DefaultAgentEngine.setTeamTaskSchedulerDaemon} and calling
 * {@code start()} from the deployment layer.
 *
 * <p>This is an explicit "no auto-scheduling" semantic, not a silent no-op
 * (Minimum Rules #24):
 * <ul>
 *   <li>{@link #start} / {@link #stop} are no-ops (no scheduler is
 *       configured for the no-op default).</li>
 *   <li>{@link #scanOnce} returns an all-zero {@link SchedulerScanResult}
 *       (via {@link SchedulerScanResult#empty()}) — an explicit "no scheduling
 *       scanning" return value, never null.</li>
 * </ul>
 *
 * <p>Consistent with the NoOp pattern in
 * {@link io.nop.ai.agent.runtime.recovery.NoOpRecoveryManager} /
 * {@link io.nop.ai.agent.team.NoOpTeamManager}.
 *
 * <p>See plan 236 (L4-blockedBy-resolution-engine) Phase 1.
 */
public final class NoOpTeamTaskSchedulerDaemon implements ITeamTaskSchedulerDaemon {

    private static final NoOpTeamTaskSchedulerDaemon INSTANCE = new NoOpTeamTaskSchedulerDaemon();

    private NoOpTeamTaskSchedulerDaemon() {
    }

    public static NoOpTeamTaskSchedulerDaemon noOp() {
        return INSTANCE;
    }

    @Override
    public void start() {
        // No-op: the shipped default does not run a background sweep.
        // Integrators opt into unattended auto-scheduling via
        // setTeamTaskSchedulerDaemon.
    }

    @Override
    public void stop() {
        // No-op: nothing was started.
    }

    @Override
    public SchedulerScanResult scanOnce() {
        // Explicit "no scheduling scanning" semantic — all-zero result,
        // never null (Minimum Rules #24).
        return SchedulerScanResult.empty();
    }
}
