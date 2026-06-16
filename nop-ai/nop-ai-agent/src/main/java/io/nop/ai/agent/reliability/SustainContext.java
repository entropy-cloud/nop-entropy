package io.nop.ai.agent.reliability;

/**
 * Reliability-local data carrier passed to
 * {@link ISustainer#onStop(SustainContext)} at a sustainable ReAct-loop exit
 * decision point (design {@code nop-ai-agent-reliability.md} §5.1a / plan 212
 * / L3-8).
 *
 * <p>This is a reliability-package-local type — it does not reference
 * {@code AgentExecutionContext} (an engine type), keeping the reliability
 * package self-contained. This mirrors the {@link IterationSnapshot} /
 * {@link RetryContext} / {@code Checkpoint} carrier pattern used by the
 * sibling reliability extension points (L3-1 circuit breaker / L3-2 retry /
 * L3-3 goal tracker / L3-4 checkpoint).
 *
 * <p>Carries:
 * <ul>
 *   <li>{@code sessionId} — the session identity (may be null for anonymous
 *       execution). A sustainer may use this to key per-session policy (a
 *       stateless sustainer like {@code SisypheanSustainer} ignores it; a
 *       future persistent sustainer could use it).</li>
 *   <li>{@code stopReason} — why the loop is stopping
 *       ({@link SustainStopReason}). Plan 212's first version only ever
 *       passes {@link SustainStopReason#MAX_ITERATIONS} (the only sustainable
 *       exit point); successor plans may pass additional values.</li>
 *   <li>{@code currentIteration} — the iteration index at the exit point
 *       (same notion as {@code ctx.getCurrentIteration()} at the call site).
 *       Lets the sustainer observe how much budget was consumed.</li>
 *   <li>{@code sustainCountSoFar} — how many sustain rounds have already been
 *       granted in this execution. The engine maintains this per-execution
 *       counter and passes it so a stateless sustainer can enforce a hard
 *       {@code maxSustainCount} ceiling without holding per-session mutable
 *       state (the {@code SisypheanSustainer} keys its CONTINUE/STOP decision
 *       on {@code sustainCountSoFar < maxSustainCount}).</li>
 * </ul>
 *
 * <p>This is an immutable data carrier. The engine constructs a fresh
 * instance at each sustainable exit point.
 */
public final class SustainContext {

    private final String sessionId;
    private final SustainStopReason stopReason;
    private final int currentIteration;
    private final int sustainCountSoFar;

    public SustainContext(String sessionId,
                          SustainStopReason stopReason,
                          int currentIteration,
                          int sustainCountSoFar) {
        if (stopReason == null) {
            throw new IllegalArgumentException("SustainContext stopReason must not be null");
        }
        if (currentIteration < 0) {
            throw new IllegalArgumentException("SustainContext currentIteration must not be negative: " + currentIteration);
        }
        if (sustainCountSoFar < 0) {
            throw new IllegalArgumentException("SustainContext sustainCountSoFar must not be negative: " + sustainCountSoFar);
        }
        this.sessionId = sessionId;
        this.stopReason = stopReason;
        this.currentIteration = currentIteration;
        this.sustainCountSoFar = sustainCountSoFar;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SustainStopReason getStopReason() {
        return stopReason;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public int getSustainCountSoFar() {
        return sustainCountSoFar;
    }
}
