package io.nop.ai.agent.security;

/**
 * The structured reason carried by a {@link DenialResult} (design §6.3).
 * Narrows audit-readiness finding L3-G1 ({@code DenialResult.reason} enum
 * was missing {@code TIMEOUT}) by distinguishing an approval timeout from a
 * human rejection, a blind retry, and a threshold-exceeded pause.
 *
 * <p>Scoped to the {@code IPostDenialGuard} / {@code DenialResult} contract
 * surface (design §6.3). Distinct from {@link ApprovalDenialKind} (the
 * {@link IApprovalGate} own-decision kind) — the two enums describe different
 * decision points in the defense-in-depth chain (design §8).
 */
public enum DenialReason {
    /**
     * A human reviewer explicitly rejected the request. Corresponds to an
     * {@link ApprovalDenialKind#HUMAN_REJECTED} decision produced upstream
     * by the {@link IApprovalGate}.
     */
    HUMAN_REJECTED,

    /**
     * The cumulative per-session denial count reached the configured
     * threshold and the {@link IDenialLedger} paused the session. Reported
     * when a {@code DenialResult} is synthesized to explain a session-pause
     * event.
     */
    THRESHOLD_EXCEEDED,

    /**
     * The agent re-submitted an action whose fingerprint (same actionKind +
     * argv + cwd + criticalEnv) is already in the session's denied set — a
     * blind retry with no legitimate follow-up (no changed parameters,
     * lower privilege, or narrower approval scope). This is the core
     * scenario the {@code IPostDenialGuard} blind-retry detection is
     * designed to block (design §6.3 / L3-7).
     */
    REPEATED_SAME_INTENT,

    /**
     * No human response arrived within the approval wait window. Corresponds
     * to an {@link ApprovalDenialKind#TIMEOUT} decision produced upstream
     * by the {@link IApprovalGate}. Narrows audit-readiness finding L3-G1
     * (the {@code reason} enum previously could not distinguish a timeout
     * from an explicit human rejection).
     */
    TIMEOUT
}
