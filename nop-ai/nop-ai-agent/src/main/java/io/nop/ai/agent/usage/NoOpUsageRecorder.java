package io.nop.ai.agent.usage;

/**
 * Pass-through {@link IUsageRecorder} used as the shipped default when no
 * functional recorder is registered. {@link #record} discards the supplied
 * usage snapshot (design {@code nop-ai-agent-usage-and-billing.md} §3.1
 * default / plan 201 Phase 1).
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour: usage tracking is not a correctness/safety component,
 * so the system runs correctly with no recorder — it simply does not persist
 * per-call usage data. A functional recorder (e.g. {@code DbUsageRecorder}
 * writing {@code NopAiChatResponse}, L2-18) is registered explicitly via
 * {@code DefaultAgentEngine.setUsageRecorder}. This mirrors the
 * {@code NoOpCheckpoint} / {@code NoOpContextCompactor} sibling pass-through
 * pattern.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpUsageRecorder implements IUsageRecorder {

    private static final NoOpUsageRecorder INSTANCE = new NoOpUsageRecorder();

    private NoOpUsageRecorder() {
    }

    /**
     * @return the singleton pass-through {@link IUsageRecorder} instance
     */
    public static IUsageRecorder noOp() {
        return INSTANCE;
    }

    @Override
    public void record(UsageRecord record) {
        // No-op: the pass-through default persists no per-call usage data.
        // This is an explicit pass-through design (see class javadoc), not a
        // hidden gap — a functional recorder persists real usage records.
    }
}
