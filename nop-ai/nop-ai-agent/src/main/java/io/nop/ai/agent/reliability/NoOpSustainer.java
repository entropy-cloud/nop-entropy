package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link ISustainer} used as the shipped default when no
 * functional sustainer is registered (design
 * {@code nop-ai-agent-reliability.md} §5.1a / plan 212 Phase 1 / L3-8).
 *
 * <p>{@link #onStop} unconditionally returns {@link SustainDecision#STOP}, so
 * the ReAct loop's exit decision point never forces a continuation and the
 * terminal-state change ({@code running} → {@code completed}) + event
 * publication proceed as normal the first time the loop exits. This preserves
 * the engine's pre-plan-212 zero-sustain behaviour verbatim, so wiring the
 * sustainer is a zero-regression change.
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour. Some deployments deliberately want zero sustaining
 * (interactive / cost-sensitive paths, or environments where the iteration
 * budget is a hard contract), and the shipped default makes that the safe
 * baseline. A functional sustainer ({@code SisypheanSustainer}) is registered
 * explicitly via {@code DefaultAgentEngine.setSustainer}. This mirrors the
 * {@link AlwaysClosed} / {@link NoOpGoalTracker} / {@link NoRetryPolicy} /
 * {@link NoOpCheckpoint} sibling pass-through pattern.
 *
 * <p>{@link #onStop} is an <b>explicit no-op decision</b>: it is not an empty
 * method body used as a placeholder for required behaviour (Minimum Rules
 * #24). The correct shipped behaviour of a pass-through sustainer is to allow
 * every stop, because no "never give up" strategy is configured out of the
 * box. A functional sustainer ({@code SisypheanSustainer}) returns CONTINUE
 * on sustainable exits under its {@code maxSustainCount} ceiling.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpSustainer implements ISustainer {

    private static final NoOpSustainer INSTANCE = new NoOpSustainer();

    private NoOpSustainer() {
    }

    /**
     * @return the singleton pass-through {@link ISustainer} instance
     */
    public static ISustainer noOp() {
        return INSTANCE;
    }

    @Override
    public SustainDecision onStop(SustainContext context) {
        // Explicit no-op decision: the pass-through default allows every stop,
        // so the terminal-state change + event publication proceed as normal.
        // This is not an empty placeholder for required behaviour — the correct
        // behaviour of a pass-through sustainer is to never force a continuation.
        // A functional sustainer (SisypheanSustainer) returns CONTINUE on
        // sustainable exits (MAX_ITERATIONS) under its maxSustainCount ceiling.
        return SustainDecision.STOP;
    }
}
