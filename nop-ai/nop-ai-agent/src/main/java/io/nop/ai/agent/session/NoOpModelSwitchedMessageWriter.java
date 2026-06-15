package io.nop.ai.agent.session;

/**
 * Pass-through {@link IModelSwitchedMessageWriter} used as the shipped default
 * when no functional writer is registered (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.5 / plan 205 L2-21).
 * {@link #writeModelSwitched} discards the event.
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour: model-switched audit messages are not a
 * correctness/safety component, so the system runs correctly with no writer —
 * it simply does not persist the audit trail. A functional writer (e.g.
 * {@link DbModelSwitchedMessageWriter} writing
 * {@code nop_ai_session_message}, L2-21) is registered explicitly via
 * {@code ReActAgentExecutor.Builder.modelSwitchedMessageWriter}. This mirrors
 * the {@code NoOpUsageRecorder} sibling pass-through pattern.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpModelSwitchedMessageWriter implements IModelSwitchedMessageWriter {

    private static final NoOpModelSwitchedMessageWriter INSTANCE = new NoOpModelSwitchedMessageWriter();

    private NoOpModelSwitchedMessageWriter() {
    }

    /**
     * @return the singleton pass-through {@link IModelSwitchedMessageWriter}
     *         instance
     */
    public static IModelSwitchedMessageWriter noOp() {
        return INSTANCE;
    }

    @Override
    public void writeModelSwitched(String sessionId, String fromModel, String toModel,
                                   String routingReason, String complexity, long seq) {
        // No-op: the pass-through default persists no model-switched audit
        // message. This is an explicit pass-through design (see class
        // javadoc), not a hidden gap — a functional writer persists real
        // audit records.
    }
}
