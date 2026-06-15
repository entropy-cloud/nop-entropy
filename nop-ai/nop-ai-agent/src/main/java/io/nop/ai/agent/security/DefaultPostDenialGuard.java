package io.nop.ai.agent.security;

import java.util.Map;

/**
 * Shipped default {@link IPostDenialGuard} implementing fingerprint-based
 * blind-retry blocking (design §6.3 / design doc §4.9 decision 4). This is
 * the engine default, replacing the former
 * {@link PassThroughPostDenialGuard} default (plan 200).
 *
 * <p>Delegates to {@link FingerprintPostDenialGuard} for the actual
 * per-session fingerprint tracking and exact-fingerprint matching logic
 * (design §6.3). The delegation avoids code duplication while providing
 * naming consistency with the other shipped defaults
 * ({@link DefaultApprovalGate}, {@link DefaultToolAccessChecker},
 * {@link DefaultPathAccessChecker}).
 *
 * <p>{@link PassThroughPostDenialGuard} is retained as a public opt-in for
 * integrators who need the "no tracking, no blocking" behavior.
 */
public final class DefaultPostDenialGuard implements IPostDenialGuard {

    private final FingerprintPostDenialGuard delegate = new FingerprintPostDenialGuard();

    @Override
    public DenialResult checkBeforeDispatch(String sessionId, String toolName,
                                            Map<String, Object> arguments, String workDir) {
        return delegate.checkBeforeDispatch(sessionId, toolName, arguments, workDir);
    }

    @Override
    public void recordDeniedAction(String sessionId, String toolName,
                                   Map<String, Object> arguments, String workDir) {
        delegate.recordDeniedAction(sessionId, toolName, arguments, workDir);
    }

    @Override
    public void reset(String sessionId) {
        delegate.reset(sessionId);
    }
}
