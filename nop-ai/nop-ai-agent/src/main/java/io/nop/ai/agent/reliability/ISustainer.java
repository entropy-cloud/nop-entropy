package io.nop.ai.agent.reliability;

/**
 * Layer 3 extension point for the "never give up" (Sisyphean) elasticity
 * philosophy — the structural opposite of {@link ICircuitBreaker}'s fail-fast
 * philosophy (design {@code nop-ai-agent-reliability.md} §5.1a Sisyphean vs
 * Fast-fail / plan 212 / L3-8).
 *
 * <p>The ReAct loop's exit decision point consults the sustainer <b>after</b>
 * the reactLoop exits naturally (iteration budget exhausted —
 * {@code MAX_ITERATIONS}) and <b>before</b> the post-loop terminal-state
 * change ({@code running} → {@code completed}) and the
 * {@code EXECUTION_COMPLETED} / {@code POST_CALL} event publication (plan 212
 * Phase 1 adjudication):
 * <ul>
 *   <li>A {@link SustainDecision#CONTINUE} return means the sustainer forces
 *       the execution to continue: the engine extends the iteration budget by
 *       one sustain-round step (the original {@code maxIterations}) and
 *       re-enters the reactLoop from the top. The full top-of-loop check
 *       chain (cancel / denial-ledger pause / force-stop / assessGoal) is
 *       re-evaluated on every sustain round, so sustaining never bypasses
 *       governance. The {@code EXECUTION_COMPLETED} event and
 *       {@code POST_CALL} hook are <b>skipped</b> on a CONTINUE (the
 *       execution is not complete yet — publishing "completed" then
 *       reviving it would corrupt the event/status semantics).</li>
 *   <li>A {@link SustainDecision#STOP} return means the sustainer allows the
 *       execution to stop: the terminal-state change and event publication
 *       proceed as normal.</li>
 * </ul>
 *
 * <p><b>Plan 212 first version</b> only consults the sustainer on a single
 * sustainable exit point: {@link SustainStopReason#MAX_ITERATIONS} (the
 * reactLoop exited because the iteration budget was exhausted while the
 * status was still {@code running}). Other exit points
 * (completion-judge {@code isComplete}, {@code isEscalate},
 * {@code shouldForceStop} context-overflow, cancel, denial-ledger pause) are
 * <b>not</b> sustainable and the sustainer is <b>not</b> consulted on them —
 * they go straight to the terminal-state change + event publication. See
 * plan 212 Non-Goals for the successor plans that extend sustaining to those
 * exit points.
 *
 * <p><b>Semantic distinction from the sibling reliability extension points</b>
 * (plan 212 Phase 1 adjudication — avoids audit confusion about overlapping
 * "progress" concerns):
 * <ul>
 *   <li>{@link IGoalTracker} — {@code assessGoal} is called at the
 *       <i>iteration-start boundary</i>; a STUCK return <b>aborts</b> a
 *       stuck/looping agent (escalate). Operation direction: <b>stop</b> a
 *       stuck agent.</li>
 *   <li>{@link ISustainer} — {@code onStop} is called at the <i>exit
 *       decision point</i>; a CONTINUE return <b>forces continuation</b> of
 *       an agent that wanted to stop but whose task is not yet complete.
 *       Operation direction: <b>keep going</b> an unfinished agent.</li>
 *   <li>completion-judge {@code Continue} — the agent <b>voluntarily</b>
 *       continues (it judged "I'm not done, keep going"), with the existing
 *       {@code consecutiveContinues} dead-loop protection.
 *       {@link ISustainer} is <b>forced</b> continuation (the agent already
 *       stopped; the sustainer pushes it on). Sustained continuation does
 *       <b>not</b> bypass the {@code consecutiveContinues} protection —
 *       iterations produced by a sustain round still go through the
 *       completion judge and its dead-loop guard.</li>
 * </ul>
 *
 * <p><b>Pass-through semantics of the shipped default</b>: {@link NoOpSustainer}
 * unconditionally returns {@link SustainDecision#STOP} and treats
 * {@code onStop} as an explicit no-op. This preserves the engine's
 * pre-plan-212 zero-sustain behaviour verbatim, so wiring the sustainer is a
 * zero-regression change. A functional sustainer ({@code SisypheanSustainer})
 * is registered explicitly via
 * {@code DefaultAgentEngine.setSustainer}.
 *
 * <p><b>Mutual exclusivity with {@link ICircuitBreaker}</b> (design §5.1a /
 * §11a): the breaker and the sustainer represent opposite elasticity
 * philosophies ("fast-fail" vs "never give up"). Plan 212 adjudicates the
 * mutual exclusivity as a <b>deployment-layer documentation constraint</b>,
 * not a runtime guard: the engine does <b>not</b> throw when both a
 * functional breaker and a functional sustainer are registered
 * simultaneously. The two interfaces operate at different layers (breaker at
 * the model-call layer, sustainer at the task-exit layer) and ship
 * pass-through defaults, so they coexist mechanically. The integrator chooses
 * one philosophy per deployment scenario (interactive / cost-sensitive →
 * fail-fast + breaker; unattended long-running → Sisyphean + sustainer). A
 * runtime hard-mutex guard is an explicit Non-Goal successor.
 *
 * <p><b>Contract for implementations</b>: {@link #onStop} must never return
 * {@code null}. Implementations should be safe to call from the ReAct loop's
 * execution thread. A stateless implementation (like
 * {@code SisypheanSustainer}, which holds only a {@code final int
 * maxSustainCount} and keys its decision on {@link SustainContext}'s
 * per-execution {@code sustainCountSoFar}) is inherently thread-safe and
 * isolates concurrent {@code execute()} calls without synchronisation.
 */
public interface ISustainer {

    /**
     * Decide whether to force the execution to continue or allow it to stop,
     * at a sustainable ReAct-loop exit point. Called by the ReAct loop after
     * the reactLoop exits naturally (iteration budget exhausted) and before
     * the terminal-state change + event publication.
     *
     * <p>Plan 212's first version only calls this with
     * {@link SustainStopReason#MAX_ITERATIONS} (the only sustainable exit
     * point). A {@link SustainDecision#CONTINUE} return causes the engine to
     * extend the iteration budget and re-enter the reactLoop; a
     * {@link SustainDecision#STOP} return causes the normal terminal-state
     * change + event publication.
     *
     * @param context non-null per-exit-point data carrier
     * @return a non-null {@link SustainDecision}; never {@code null}
     */
    SustainDecision onStop(SustainContext context);
}
