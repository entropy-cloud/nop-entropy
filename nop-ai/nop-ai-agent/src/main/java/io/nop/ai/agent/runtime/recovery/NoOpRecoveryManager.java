package io.nop.ai.agent.runtime.recovery;

/**
 * Shipped no-op default for {@link IRecoveryManager}.
 *
 * <p>Used by {@code DefaultAgentEngine} out-of-the-box: single-process
 * deployments keep relying on the engine's in-process guards and the
 * one-shot {@code restorePendingSessions}, so engine behaviour is
 * unchanged. Integrators opt into continuous sweeping by wiring a
 * functional manager (e.g. {@code ScheduledRecoveryManager}) via
 * {@code DefaultAgentEngine.setRecoveryManager} and calling
 * {@code start()} from the deployment layer.
 *
 * <p>This is an explicit "no recovery scanning needed" semantic, not a
 * silent no-op (Minimum Rules #24):
 * <ul>
 *   <li>{@link #start} / {@link #stop} are no-ops (no scheduler is
 *       configured for the no-op default).</li>
 *   <li>{@link #scanOnce} returns an all-zero {@link RecoveryScanResult}
 *       (via {@link RecoveryScanResult#empty()}) — an explicit "no
 *       recovery scanning" return value, never null.</li>
 * </ul>
 *
 * <p>Consistent with the NoOp pattern in
 * {@code NoOpSessionTakeoverLock} / {@code NoOpActorRuntime}.
 *
 * <p>See plan 222 (L4-8-P4-RecoveryDaemon) Phase 1.
 */
public final class NoOpRecoveryManager implements IRecoveryManager {

    private static final NoOpRecoveryManager INSTANCE = new NoOpRecoveryManager();

    private NoOpRecoveryManager() {
    }

    public static NoOpRecoveryManager noOp() {
        return INSTANCE;
    }

    @Override
    public void start() {
        // No-op: the shipped default does not run a background sweep.
        // Integrators opt into continuous sweeping via setRecoveryManager.
    }

    @Override
    public void stop() {
        // No-op: nothing was started.
    }

    @Override
    public RecoveryScanResult scanOnce() {
        // Explicit "no recovery scanning" semantic — all-zero result,
        // never null (Minimum Rules #24).
        return RecoveryScanResult.empty();
    }
}
