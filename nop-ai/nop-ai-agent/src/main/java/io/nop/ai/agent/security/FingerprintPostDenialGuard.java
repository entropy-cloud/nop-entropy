package io.nop.ai.agent.security;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Functional {@link IPostDenialGuard} implementation (design §6.3): maintains
 * a per-session set of denied action fingerprints and blocks blind retries
 * via exact-fingerprint matching. This is the shipped functional default
 * (pure in-memory, no external dependencies); register it via
 * {@code DefaultAgentEngine.setPostDenialGuard} to enable blind-retry
 * blocking.
 *
 * <p><b>Detection strategy</b>: exact-fingerprint matching. Two actions with
 * the same {@code actionKind + argv + cwd + criticalEnv} produce the same
 * {@link ActionFingerprint} and are treated as the same already-denied
 * intent. A legitimate follow-up (changed parameters, lower privileges,
 * narrower approval scope) naturally produces a different fingerprint and is
 * therefore not blocked — this covers the main legitimate-follow-up
 * scenarios without requiring explicit follow-up-tag detection (design §6.3
 * non-goal).
 *
 * <p><b>Thread safety</b>: backed by a {@link ConcurrentHashMap} keyed by
 * session id, with each session's fingerprint set backed by
 * {@link ConcurrentHashMap#newKeySet()}. Per-session sets are independent —
 * a denial recorded in session A does not affect the denied set of session B.
 *
 * <p><b>Anonymous sessions</b>: when {@code sessionId} is null, consultation
 * returns {@code null} (no tracking) and recording is a no-op. The semantic
 * is "anonymous sessions are not tracked" — explicit, not a silent skip.
 *
 * <p><b>Persistence</b>: in-memory only. A DB-backed successor persists the
 * per-session denied set across session recovery (deferred, symmetric to the
 * {@code DBDenialLedger} successor).
 */
public final class FingerprintPostDenialGuard implements IPostDenialGuard {

    private final ConcurrentHashMap<String, Set<String>> deniedFingerprints = new ConcurrentHashMap<>();

    /**
     * Compute the fingerprint that this guard would use to key the given
     * action. Exposed primarily for testing and for callers that want to
     * inspect which fingerprint an action maps to.
     *
     * @param toolName  the tool name / operation category; may be null
     * @param arguments the tool-call arguments map; may be null or empty
     * @param workDir   the working directory; may be null
     * @return the computed fingerprint value
     */
    public String fingerprintOf(String toolName, Map<String, Object> arguments, String workDir) {
        return ActionFingerprint.compute(toolName, arguments, workDir, null).getValue();
    }

    @Override
    public DenialResult checkBeforeDispatch(String sessionId, String toolName,
                                            Map<String, Object> arguments, String workDir) {
        if (sessionId == null) {
            // Anonymous sessions are not tracked — explicit semantic, not a
            // silent skip.
            return null;
        }
        String fingerprint = fingerprintOf(toolName, arguments, workDir);
        Set<String> sessionSet = deniedFingerprints.get(sessionId);
        if (sessionSet == null || !sessionSet.contains(fingerprint)) {
            return null;
        }
        String message = "Repeated same denied action: " + (toolName != null ? toolName : "<unknown>");
        return DenialResult.repeatedSameIntent(fingerprint, message);
    }

    @Override
    public void recordDeniedAction(String sessionId, String toolName,
                                   Map<String, Object> arguments, String workDir) {
        if (sessionId == null) {
            // Anonymous sessions are not tracked — explicit semantic, not a
            // silent skip.
            return;
        }
        String fingerprint = fingerprintOf(toolName, arguments, workDir);
        deniedFingerprints.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(fingerprint);
    }

    @Override
    public void reset(String sessionId) {
        if (sessionId == null) {
            return;
        }
        deniedFingerprints.remove(sessionId);
    }

    /**
     * Test/diagnostic accessor: query whether a fingerprint is currently in
     * the session's denied set. Returns {@code false} for unknown / anonymous
     * sessions.
     */
    public boolean isDenied(String sessionId, String fingerprint) {
        if (sessionId == null) {
            return false;
        }
        Set<String> sessionSet = deniedFingerprints.get(sessionId);
        return sessionSet != null && sessionSet.contains(fingerprint);
    }
}
