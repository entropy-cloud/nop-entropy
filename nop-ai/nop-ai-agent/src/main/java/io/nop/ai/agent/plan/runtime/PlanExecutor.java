package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The plan execution host (design §14.5 host ruling). This is the minimal
 * self-contained plan executor that consumes the previously-orphaned
 * {@link PlanScheduler#getReadyTasks} and {@link PlanRunner#checkGate}
 * helpers to drive the plan/phase/task state machine forward, and is the
 * mounting point for {@link StagnationDetector} and {@link PlanReplanner}.
 *
 * <p><b>Why this host exists</b>: until W1-4, {@code PlanRunner.checkGate}
 * and {@code PlanScheduler.getReadyTasks} were stateless queries whose
 * output was consumed by nothing — there was no plan executor and the agent
 * engine has no phase-transition hook. Options (ii) nop-task migration and
 * (iii) mounting the ReAct loop were ruled out (design §14.5), leaving (i)
 * this minimal executor as the replanner's host.
 *
 * <p><b>Freeze protocol</b>: the loaded {@link AgentPlan} is frozen
 * (cascade) by {@code ResourceComponentManager}, so the executor never
 * mutates it. All runtime state lives in a {@link PlanExecutionState}
 * overlay (design §14.4.3): task/phase status, {@code AgentPlanError}
 * records, gate-exhaustion markers, and runtime-split task nodes.
 *
 * <p><b>Loop + recoverable replanning</b>: for each phase (from the declared
 * {@code currentPhase}), run ready tasks via the injected {@link TaskRunner},
 * apply outcomes (status transitions + error recording on failure), then
 * check the phase gate. After every task batch the {@link StagnationDetector}
 * is consulted; any stagnation event is handed to the {@link PlanReplanner}.
 * Terminal decisions ({@link ReplanDecision#ESCALATE} / gate BLOCKED /
 * EXPLICIT_VERDICT_REQUIRED) stop and return the result.
 * <b>Recoverable</b> decisions keep execution alive:
 * <ul>
 *   <li>{@link ReplanDecision#ROLLBACK_PHASE} — the replanner has moved
 *       {@code currentPhase} back to a preceding phase; the loop re-enters
 *       at that phase index instead of terminating.</li>
 *   <li>{@link ReplanDecision#SPLIT_TASK} — the replanner has inserted
 *       runtime sub-task nodes; the loop re-drives the current phase so the
 *       scheduler picks them up.</li>
 * </ul>
 * A cycle-safety bound counts recoveries and throws
 * {@link IllegalStateException} if a ROLLBACK↔advance loop fails to converge
 * (reusing the {@link #computeSafetyBound} pattern), so a misconfigured
 * rollback policy fails fast rather than spinning.
 *
 * <p>This executor produces <b>real</b> state transitions and <b>real</b>
 * error records — stagnation events are derived from the live state machine,
 * never synthesized out of band (Anti-Hollow).
 */
public class PlanExecutor {

    private final TaskRunner taskRunner;
    private final StagnationDetector detector;
    private final PlanReplanner replanner;
    private final PlanScheduler scheduler;
    private final PlanRunner gateRunner;
    private final FailureEscalationPolicy failureEscalationPolicy;

    /**
     * Construct a host with the given collaborators and the default
     * (disabled) failure-escalation policy — zero regression.
     */
    public PlanExecutor(TaskRunner taskRunner, StagnationDetector detector, PlanReplanner replanner) {
        this(taskRunner, detector, replanner, new PlanScheduler(), new PlanRunner(),
                FailureEscalationPolicy.disabled());
    }

    /**
     * Construct a host with an explicit failure-escalation policy (design
     * §13.3 W2-3). Pass {@link FailureEscalationPolicy#disabled()} for the
     * pre-W2-3 undifferentiated behaviour.
     */
    public PlanExecutor(TaskRunner taskRunner, StagnationDetector detector, PlanReplanner replanner,
                        FailureEscalationPolicy failureEscalationPolicy) {
        this(taskRunner, detector, replanner, new PlanScheduler(), new PlanRunner(), failureEscalationPolicy);
    }

    PlanExecutor(TaskRunner taskRunner, StagnationDetector detector, PlanReplanner replanner,
                 PlanScheduler scheduler, PlanRunner gateRunner,
                 FailureEscalationPolicy failureEscalationPolicy) {
        if (taskRunner == null) throw new IllegalArgumentException("taskRunner must not be null");
        if (detector == null) throw new IllegalArgumentException("detector must not be null");
        if (replanner == null) throw new IllegalArgumentException("replanner must not be null");
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (gateRunner == null) throw new IllegalArgumentException("gateRunner must not be null");
        if (failureEscalationPolicy == null)
            throw new IllegalArgumentException("failureEscalationPolicy must not be null");
        this.taskRunner = taskRunner;
        this.detector = detector;
        this.replanner = replanner;
        this.scheduler = scheduler;
        this.gateRunner = gateRunner;
        this.failureEscalationPolicy = failureEscalationPolicy;
    }

    /**
     * Drive the plan through the state machine and return the observable
     * result. The input plan is treated as a read-only template.
     */
    public PlanExecutionResult execute(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        PlanExecutionState state = new PlanExecutionState(plan);
        state.setPlanStatus(AgentExecStatus.running);

        List<StagnationEvent> eventsObserved = new ArrayList<>();
        List<ReplanDecisionResult> decisionsEnacted = new ArrayList<>();

        List<AgentPlanPhase> phases = plan.getPhases();
        if (phases == null || phases.isEmpty()) {
            state.setPlanStatus(AgentExecStatus.completed);
            return new PlanExecutionResult(state.getPlanStatus(), eventsObserved, decisionsEnacted,
                    0, 0, null);
        }

        int safetyMaxCycles = computeSafetyBound(phases);
        int maxRecoveries = safetyMaxCycles;
        int recoveries = 0;

        int phaseIdx = indexOfCurrentPhase(phases, state.getCurrentPhase());

        while (phaseIdx < phases.size()) {
            AgentPlanPhase phase = phases.get(phaseIdx);
            String phaseName = phase.getName();
            state.setCurrentPhase(phaseName);
            state.setPhaseStatus(phaseName, AgentExecStatus.running);

            Set<String> phaseTaskNos = collectTaskNos(phase, state);

            StopOutcome tasksOutcome = drivePhaseTasks(state, phase, phaseTaskNos,
                    eventsObserved, decisionsEnacted, safetyMaxCycles);
            if (tasksOutcome.terminal != null) {
                return tasksOutcome.terminal;
            }
            if (tasksOutcome.recoverable != null) {
                if (++recoveries > maxRecoveries) {
                    throw new IllegalStateException("PlanExecutor exceeded recovery cycle bound ("
                            + maxRecoveries + ") — a ROLLBACK/SPLIT policy is not converging");
                }
                if (tasksOutcome.recoverable.getType() == ReplanDecision.ROLLBACK_PHASE) {
                    phaseIdx = indexOfCurrentPhase(phases, state.getCurrentPhase());
                }
                // SPLIT_TASK: re-drive the same phase (sub-tasks inserted) — do not advance.
                continue;
            }

            StopOutcome gateOutcome = checkPhaseGate(state, phase, eventsObserved, decisionsEnacted,
                    safetyMaxCycles);
            if (gateOutcome.terminal != null) {
                return gateOutcome.terminal;
            }
            if (gateOutcome.recoverable != null) {
                if (++recoveries > maxRecoveries) {
                    throw new IllegalStateException("PlanExecutor exceeded recovery cycle bound ("
                            + maxRecoveries + ") — a ROLLBACK/SPLIT policy is not converging");
                }
                if (gateOutcome.recoverable.getType() == ReplanDecision.ROLLBACK_PHASE) {
                    phaseIdx = indexOfCurrentPhase(phases, state.getCurrentPhase());
                    continue;
                }
                // SPLIT at gate level: re-drive the same phase to execute inserted sub-tasks.
                continue;
            }

            state.setPhaseStatus(phaseName, AgentExecStatus.completed);
            phaseIdx++;
        }

        state.setPlanStatus(AgentExecStatus.completed);
        return new PlanExecutionResult(state.getPlanStatus(), eventsObserved, decisionsEnacted,
                countCompleted(state), state.getErrors().size(), state.getCurrentPhase());
    }

    private StopOutcome drivePhaseTasks(PlanExecutionState state, AgentPlanPhase phase,
                                        Set<String> phaseTaskNos,
                                        List<StagnationEvent> eventsObserved,
                                        List<ReplanDecisionResult> decisionsEnacted,
                                        int safetyMaxCycles) {
        int cycles = 0;
        while (true) {
            if (++cycles > safetyMaxCycles) {
                throw new IllegalStateException(
                        "PlanExecutor exceeded safety cycle bound (" + safetyMaxCycles
                                + ") without converging or detecting stagnation in phase "
                                + phase.getName());
            }

            List<AgentPlanTaskModel> ready = readyTasksForPhase(state, phase, phaseTaskNos);
            if (ready.isEmpty()) {
                return StopOutcome.proceed();
            }

            for (AgentPlanTaskModel task : ready) {
                String taskNo = task.getTaskNo();
                state.setTaskStatus(taskNo, AgentExecStatus.running);
                state.incrementTaskAttempts(taskNo);

                TaskOutcome outcome = taskRunner.run(task);
                if (outcome.isSuccess()) {
                    state.setTaskStatus(taskNo, AgentExecStatus.completed);
                    state.resetConsecutiveFailures(taskNo);
                    state.resetTypedFailures(taskNo);
                } else {
                    state.incrementConsecutiveFailures(taskNo);
                    state.recordError(taskNo, state.getTaskAttempts(taskNo), outcome.getErrorText());

                    // W2-3 three-level failure escalation (design §13.3).
                    // Typed failures are counted per-type and escalated when
                    // their threshold is hit. Untyped failures (null type)
                    // always retry (pending) — zero regression.
                    FailureType failureType = outcome.getFailureType();
                    if (failureType != null) {
                        state.recordTypedFailure(taskNo, failureType);
                        int typedCount = state.getTypedFailureCount(taskNo, failureType);
                        if (failureEscalationPolicy.shouldEscalate(failureType, typedCount)) {
                            // Escalation action: mark the task failed (terminal).
                            // Errors remain unresolved → feed REPEATED_ERRORS
                            // (Contribute model, design §13.3 裁定 E).
                            state.setTaskStatus(taskNo, AgentExecStatus.failed);
                        } else {
                            state.setTaskStatus(taskNo, AgentExecStatus.pending);
                        }
                    } else {
                        state.setTaskStatus(taskNo, AgentExecStatus.pending);
                    }
                }
            }

            StopOutcome outcome = respondToStagnation(state, eventsObserved, decisionsEnacted);
            if (outcome.terminal != null || outcome.recoverable != null) {
                return outcome;
            }
        }
    }

    private StopOutcome checkPhaseGate(PlanExecutionState state, AgentPlanPhase phase,
                                       List<StagnationEvent> eventsObserved,
                                       List<ReplanDecisionResult> decisionsEnacted,
                                       int safetyMaxCycles) {
        String phaseName = phase.getName();
        int attempt = 1;
        int guard = 0;
        while (true) {
            if (++guard > safetyMaxCycles) {
                throw new IllegalStateException(
                        "PlanExecutor exceeded gate-check safety bound for phase " + phaseName);
            }

            GateCheckResult gateResult = gateRunner.checkGate(phase, attempt);
            switch (gateResult.getOutcome()) {
                case PASSED:
                    return StopOutcome.proceed();
                case RETRY:
                    attempt++;
                    break;
                case RETRY_EXHAUSTED:
                case ESCALATED:
                    state.markGateExhausted(phaseName, attempt);
                    return respondToStagnation(state, eventsObserved, decisionsEnacted);
                case BLOCKED:
                case EXPLICIT_VERDICT_REQUIRED:
                    return StopOutcome.terminal(new PlanExecutionResult(
                            state.getPlanStatus(), eventsObserved, decisionsEnacted,
                            countCompleted(state), state.getErrors().size(), phaseName));
                default:
                    throw new IllegalArgumentException(
                            "Unknown gate outcome: " + gateResult.getOutcome());
            }
        }
    }

    private StopOutcome respondToStagnation(PlanExecutionState state,
                                            List<StagnationEvent> eventsObserved,
                                            List<ReplanDecisionResult> decisionsEnacted) {
        List<StagnationEvent> events = detector.detect(state);
        if (events.isEmpty()) {
            return StopOutcome.proceed();
        }
        eventsObserved.addAll(events);
        ReplanDecisionResult decision = replanner.decide(events);
        decisionsEnacted.add(decision);
        replanner.apply(decision, state);
        if (decision.isRecoverable()) {
            return StopOutcome.recoverable(decision);
        }
        // Terminal decision (ESCALATE enacted, or ABORT would have thrown inside apply()).
        return StopOutcome.terminal(new PlanExecutionResult(
                state.getPlanStatus(), eventsObserved, decisionsEnacted,
                countCompleted(state), state.getErrors().size(), state.getCurrentPhase()));
    }

    private List<AgentPlanTaskModel> readyTasksForPhase(PlanExecutionState state, AgentPlanPhase phase,
                                                        Set<String> phaseTaskNos) {
        List<AgentPlanTaskModel> ready = scheduler.getReadyTasks(
                state.getPlan(), state.statusProvider(), state.runtimeTaskOverlay());
        List<AgentPlanTaskModel> inPhase = new ArrayList<>();
        for (AgentPlanTaskModel task : ready) {
            if (phaseTaskNos.contains(task.getTaskNo())) {
                inPhase.add(task);
            }
        }
        return inPhase;
    }

    private Set<String> collectTaskNos(AgentPlanPhase phase, PlanExecutionState state) {
        Set<String> nos = new HashSet<>(state.phaseTaskNos(phase.getName()));
        collectRecursive(phase.getTasks(), nos);
        return nos;
    }

    private void collectRecursive(List<AgentPlanTaskModel> tasks, Set<String> out) {
        if (tasks == null) return;
        for (AgentPlanTaskModel task : tasks) {
            if (task.getTaskNo() != null) {
                out.add(task.getTaskNo());
            }
            collectRecursive(task.getSubTasks(), out);
        }
    }

    private int countCompleted(PlanExecutionState state) {
        int n = 0;
        if (state.getPlan().getPhases() != null) {
            for (AgentPlanPhase phase : state.getPlan().getPhases()) {
                n += countCompletedRecursive(phase.getTasks(), state);
            }
        }
        n += state.countCompletedRuntimeTasks();
        return n;
    }

    private int countCompletedRecursive(List<AgentPlanTaskModel> tasks, PlanExecutionState state) {
        if (tasks == null) return 0;
        int n = 0;
        for (AgentPlanTaskModel task : tasks) {
            if (state.getTaskStatus(task.getTaskNo()) == AgentExecStatus.completed) {
                n++;
            }
            n += countCompletedRecursive(task.getSubTasks(), state);
        }
        return n;
    }

    private int indexOfCurrentPhase(List<AgentPlanPhase> phases, String currentPhase) {
        if (currentPhase == null) {
            return 0;
        }
        for (int i = 0; i < phases.size(); i++) {
            if (currentPhase.equals(phases.get(i).getName())) {
                return i;
            }
        }
        return 0;
    }

    private int computeSafetyBound(List<AgentPlanPhase> phases) {
        int tasks = 0;
        for (AgentPlanPhase phase : phases) {
            tasks += countTasksRecursive(phase.getTasks());
        }
        int threshold = Math.max(detector.getStaleTaskCycles(), detector.getMaxErrorsPerTask()) + 5;
        return Math.max(100, (tasks + 1) * threshold + 50);
    }

    private int countTasksRecursive(List<AgentPlanTaskModel> tasks) {
        if (tasks == null) return 0;
        int n = 0;
        for (AgentPlanTaskModel task : tasks) {
            n++;
            n += countTasksRecursive(task.getSubTasks());
        }
        return n;
    }

    /** Either proceed, or hand back a recoverable decision, or a terminal result. */
    private static final class StopOutcome {
        final ReplanDecisionResult recoverable;
        final PlanExecutionResult terminal;

        private StopOutcome(ReplanDecisionResult recoverable, PlanExecutionResult terminal) {
            this.recoverable = recoverable;
            this.terminal = terminal;
        }

        static StopOutcome proceed() {
            return new StopOutcome(null, null);
        }

        static StopOutcome recoverable(ReplanDecisionResult decision) {
            return new StopOutcome(decision, null);
        }

        static StopOutcome terminal(PlanExecutionResult result) {
            return new StopOutcome(null, result);
        }
    }
}
