package io.nop.ai.agent.security;

import java.util.Map;

/**
 * Pass-through {@link IPostDenialGuard} used as the default when no functional
 * guard is registered. Consultation always returns {@code null} (no
 * blind-retry detection), recording and reset are no-ops (design §6.3
 * default). Consistent with the {@link AutoApproveGate} /
 * {@link NoOpDenialLedger} / {@link NoOpSecurityLevelResolver} /
 * {@link PassThroughPermissionMatrix} sibling pattern.
 *
 * <p>The pass-through semantics are semantically correct (design §6.3
 * default): the shipped default does not track denied actions, so unattended
 * Layer 1 automation is unaffected — every tool call proceeds to the Layer 1
 * checks regardless of whether an identical action was denied earlier. A
 * functional guard (tracking fingerprints and blocking blind retries) is
 * registered explicitly via
 * {@code DefaultAgentEngine.setPostDenialGuard}.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class PassThroughPostDenialGuard implements IPostDenialGuard {

    private static final PassThroughPostDenialGuard INSTANCE = new PassThroughPostDenialGuard();

    private PassThroughPostDenialGuard() {
    }

    public static IPostDenialGuard passThrough() {
        return INSTANCE;
    }

    /**
     * Always returns {@code null} — the pass-through default does not block
     * any retry. This is the semantically-correct "no tracking" behavior, not
     * a silent skip: a functional guard ({@link FingerprintPostDenialGuard})
     * performs real blind-retry detection.
     */
    @Override
    public DenialResult checkBeforeDispatch(String sessionId, String toolName,
                                            Map<String, Object> arguments, String workDir) {
        return null;
    }

    /**
     * No-op — the pass-through default maintains no per-session state. The
     * semantic is "no recording is needed because nothing is tracked" — this
     * is an explicit empty method, not a silent skip of required behavior. A
     * functional guard records real fingerprints.
     */
    @Override
    public void recordDeniedAction(String sessionId, String toolName,
                                   Map<String, Object> arguments, String workDir) {
        // No-op: the pass-through default maintains no per-session state.
    }

    /**
     * No-op — the pass-through default maintains no per-session state.
     */
    @Override
    public void reset(String sessionId) {
        // No-op: the pass-through default maintains no per-session state.
    }
}
