package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link ICheckpointManager} used as the default when no
 * functional manager is registered. No checkpoints are saved, and
 * {@link #getLatestCheckpoint} / {@link #getCheckpoint} always return
 * {@code null} (design §5.4 default). Consistent with the
 * {@code NoOpDenialLedger} / {@code AutoApproveGate} /
 * {@code PassThroughPostDenialGuard} sibling pass-through pattern.
 *
 * <p>The NoOp pass-through semantics are semantically correct (design §5.4
 * default): the shipped default does not impose checkpoint recording, so
 * unattended Layer 1 automation is unaffected. A functional manager
 * (recording checkpoints in-memory or to a persistent store) is registered
 * explicitly via {@code DefaultAgentEngine.setCheckpointManager}.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpCheckpoint implements ICheckpointManager {

    private static final NoOpCheckpoint INSTANCE = new NoOpCheckpoint();

    private NoOpCheckpoint() {
    }

    /**
     * @return the singleton pass-through {@link ICheckpointManager} instance
     */
    public static ICheckpointManager noOp() {
        return INSTANCE;
    }

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        // No-op: the pass-through default maintains no per-session state.
        // The semantic is "no checkpoint is recorded because no functional
        // manager is registered" — this is an explicit pass-through default,
        // not a silent skip of required behavior. A functional manager
        // records real checkpoints.
    }

    @Override
    public Checkpoint getLatestCheckpoint(String sessionId) {
        return null;
    }

    @Override
    public Checkpoint getCheckpoint(String watermark) {
        return null;
    }
}
