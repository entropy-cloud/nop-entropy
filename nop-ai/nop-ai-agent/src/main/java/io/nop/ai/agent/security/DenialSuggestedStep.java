package io.nop.ai.agent.security;

/**
 * The suggested next step carried by a {@link DenialResult} (design §6.3).
 * Maps to the three legitimate follow-up labels plus the generic replan /
 * ask-user recovery strategies that a denied agent may take to legitimately
 * proceed (rather than blindly retrying the same denied intent).
 *
 * <p>Scoped to the {@code IPostDenialGuard} / {@code DenialResult} contract
 * surface (design §6.3).
 */
public enum DenialSuggestedStep {
    /**
     * The agent should re-plan its approach — the denied action is not
     * achievable as-submitted and a different plan is required. This is the
     * default suggestion for a {@link DenialReason#REPEATED_SAME_INTENT}
     * denial (blind retry with no legitimate follow-up).
     */
    REPLAN,

    /**
     * The agent should ask the user for guidance or additional information
     * (e.g. obtain a different path, a lower-privilege credential, or
     * explicit permission).
     */
    ASK_USER,

    /**
     * The agent may retry with reduced privileges (corresponds to the
     * {@code LOWER_PRIVILEGE} legitimate follow-up label, design §6.3).
     * A retry that genuinely lowers privileges naturally produces a
     * different fingerprint (changed argv) and is therefore not blocked by
     * the {@code FingerprintPostDenialGuard} exact-fingerprint matching.
     */
    LOWER_PRIVILEGE,

    /**
     * The agent may request a narrower-scope approval (corresponds to the
     * {@code NARROWER_APPROVAL} legitimate follow-up label, design §6.3).
     * A retry that genuinely narrows the scope (changed argv / paths)
     * naturally produces a different fingerprint and is therefore not
     * blocked by the {@code FingerprintPostDenialGuard} exact-fingerprint
     * matching.
     */
    NARROWER_APPROVAL
}
