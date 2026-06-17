package io.nop.ai.agent.runtime.lock;

/**
 * Shipped no-op default for {@link ISessionTakeoverLock}.
 *
 * <p>Used by {@code DefaultAgentEngine} out-of-the-box: single-process
 * deployments keep relying on the engine's in-process
 * {@code runningExecutions.putIfAbsent} guard (plan 197), so engine
 * behaviour is unchanged. Integrators opt into cross-process protection
 * by wiring a functional lock (e.g. {@link DbSessionTakeoverLock}) via
 * {@code DefaultAgentEngine.setSessionTakeoverLock}.
 *
 * <p>This is an explicit "no cross-process lock needed" semantic, not a
 * silent no-op (Minimum Rules #24):
 * <ul>
 *   <li>{@link #tryAcquire} unconditionally returns {@code true} — the
 *       caller proceeds on the existing in-process path.</li>
 *   <li>{@link #isHeld} unconditionally returns {@code false} — no other
 *       instance holds this session, so {@code restorePendingSessions}
 *       does not skip any candidate.</li>
 *   <li>{@link #release} / {@link #tryRenew} return {@code true} as
 *       no-op acknowledgements (consistent with the NoOp pattern in
 *       {@code NoOpActorRuntime} / {@code NoOpBudgetProvider}).</li>
 * </ul>
 *
 * <p>See plan 221 (L4-8-P4) Phase 1.
 */
public final class NoOpSessionTakeoverLock implements ISessionTakeoverLock {

    private static final NoOpSessionTakeoverLock INSTANCE = new NoOpSessionTakeoverLock();

    private NoOpSessionTakeoverLock() {
    }

    public static NoOpSessionTakeoverLock noOp() {
        return INSTANCE;
    }

    @Override
    public boolean tryAcquire(String sessionId, String ownerId, long leaseMs) {
        return true;
    }

    @Override
    public boolean release(String sessionId, String ownerId) {
        return true;
    }

    @Override
    public boolean isHeld(String sessionId) {
        return false;
    }

    @Override
    public boolean tryRenew(String sessionId, String ownerId, long leaseMs) {
        return true;
    }
}
