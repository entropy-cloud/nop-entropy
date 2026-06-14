package io.nop.ai.agent.security;

/**
 * Pass-through {@link IDenialLedger} used as the default when no functional
 * ledger is registered. No denials are counted, no sessions are paused, and
 * {@link #isPaused} always returns {@code false} (design §6.2 default).
 * Consistent with the {@code AutoApproveGate} / {@code NoOpSecurityLevelResolver}
 * / {@code PassThroughPermissionMatrix} sibling pattern.
 *
 * <p>The NoOp pass-through semantics are semantically correct (design §6.2
 * default): the shipped default does not impose denial counting or threshold
 * pausing, so unattended Layer 1 automation is unaffected. A functional
 * ledger (counting denials and pausing on threshold) is registered
 * explicitly via {@code DefaultAgentEngine.setDenialLedger}.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpDenialLedger implements IDenialLedger {

    private static final NoOpDenialLedger INSTANCE = new NoOpDenialLedger();

    /**
     * The fixed outcome returned by {@link #recordDenial}: count {@code 0},
     * threshold-not-exceeded. Captures the "not counting" semantic — it is
     * an explicit pass-through result, not a silent skip.
     */
    private static final DenialRecordOutcome NOT_COUNTED = DenialRecordOutcome.of(0, false);

    private NoOpDenialLedger() {
    }

    public static IDenialLedger noOp() {
        return INSTANCE;
    }

    @Override
    public DenialRecordOutcome recordDenial(DenialRecord record) {
        return NOT_COUNTED;
    }

    @Override
    public boolean isPaused(String sessionId) {
        return false;
    }

    @Override
    public int getDenialCount(String sessionId) {
        return 0;
    }

    @Override
    public void reset(String sessionId) {
        // No-op: the pass-through default maintains no per-session state.
        // The semantic is "no reset is needed because nothing was counted" —
        // this is an explicit empty method, not a silent skip of required
        // behavior. A functional ledger maintains real per-session counts.
    }
}
