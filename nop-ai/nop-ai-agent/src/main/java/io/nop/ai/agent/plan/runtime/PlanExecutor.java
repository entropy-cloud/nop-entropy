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
 * records, gate-exhaustion markers.
 *
 * <p><b>Loop</b>: for each phase (from the declared {@code currentPhase}),
 * run ready tasks via the injected {@link TaskRunner}, apply outcomes
 * (status transitions + error recording on failure), then check the phase
 * gate. After every task batch the {@link StagnationDetector} is consulted;
 * any stagnation event is handed to the {@link PlanReplanner}, whose
 * decision is enacted immediately. The first observed {@link ReplanDecision#ESCALATE}
 * sets the plan status to {@link AgentExecStatus#escalated} and stops.
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

    /**
     * Construct a host with the given collaborators.
     */
    public PlanExecutor(TaskRunner taskRunner, StagnationDetector detector, PlanReplanner replanner) {
        this(taskRunner, detector, replanner, new PlanScheduler(), new PlanRunner());
    }

    PlanExecutor(TaskRunner taskRunner, StagnationDetector detector, PlanReplanner replanner,
                 PlanScheduler scheduler, PlanRunner gateRunner) {
        if (taskRunner == null) throw new IllegalArgumentException("taskRunner must not be null");
        if (detector == null) throw new IllegalArgumentException("detector must not be null");
        if (replanner == null) throw new IllegalArgumentException("replanner must not be null");
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (gateRunner == null) throw new IllegalArgumentException("gateRunner must not be null");
        this.taskRunner = taskRunner;
        this.detector = detector;
        this.replanner = replanner;
        this.scheduler = scheduler;
        this.gateRunner = gateRunner;
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
        List<ReplanDecision> decisionsEnacted = new ArrayList<>();

        List<AgentPlanPhase> phases = plan.getPhases();
        if (phases == null || phases.isEmpty()) {
            state.setPlanStatus(AgentExecStatus.completed);
            return new PlanExecutionResult(state.getPlanStatus(), eventsObserved, decisionsEnacted,
                    0, 0, null);
        }

        int startIdx = indexOfCurrentPhase(phases, state.getCurrentPhase());
        int safetyMaxCycles = computeSafetyBound(phases);

        for (int phaseIdx = startIdx; phaseIdx < phases.size(); phaseIdx++) {
            AgentPlanPhase phase = phases.get(phaseIdx);
            String phaseName = phase.getName();
            state.setCurrentPhase(phaseName);
            state.setPhaseStatus(phaseName, AgentExecStatus.running);

            Set<String> phaseTaskNos = collectTaskNos(phase);

            StagnationResponse stop = drivePhaseTasks(state, phase, phaseTaskNos,
                    eventsObserved, decisionsEnacted, safetyMaxCycles);
            if (stop != null) {
                return stop.result;
            }

            stop = checkPhaseGate(state, phase, eventsObserved, decisionsEnacted, safetyMaxCycles);
            if (stop != null) {
                return stop.result;
            }

            state.setPhaseStatus(phaseName, AgentExecStatus.completed);
        }

        state.setPlanStatus(AgentExecStatus.completed);
        return new PlanExecutionResult(state.getPlanStatus(), eventsObserved, decisionsEnacted,
                countCompleted(state), state.getErrors().size(), state.getCurrentPhase());
    }

    private StagnationResponse drivePhaseTasks(PlanExecutionState state, AgentPlanPhase phase,
                                               Set<String> phaseTaskNos,
                                               List<StagnationEvent> eventsObserved,
                                               List<ReplanDecision> decisionsEnacted,
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
                return null;
            }

            for (AgentPlanTaskModel task : ready) {
                String taskNo = task.getTaskNo();
                state.setTaskStatus(taskNo, AgentExecStatus.running);
                state.incrementTaskAttempts(taskNo);

                TaskOutcome outcome = taskRunner.run(task);
                if (outcome.isSuccess()) {
                    state.setTaskStatus(taskNo, AgentExecStatus.completed);
                    state.resetConsecutiveFailures(taskNo);
                } else {
                    state.incrementConsecutiveFailures(taskNo);
                    state.recordError(taskNo, state.getTaskAttempts(taskNo), outcome.getErrorText());
                    state.setTaskStatus(taskNo, AgentExecStatus.pending);
                }
            }

            StagnationResponse stop = respondToStagnation(state, eventsObserved, decisionsEnacted);
            if (stop != null) {
                return stop;
            }
        }
    }

    private StagnationResponse checkPhaseGate(PlanExecutionState state, AgentPlanPhase phase,
                                              List<StagnationEvent> eventsObserved,
                                              List<ReplanDecision> decisionsEnacted,
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
                    return null;
                case RETRY:
                    attempt++;
                    break;
                case RETRY_EXHAUSTED:
                case ESCALATED:
                    state.markGateExhausted(phaseName, attempt);
                    return respondToStagnation(state, eventsObserved, decisionsEnacted);
                case BLOCKED:
                case EXPLICIT_VERDICT_REQUIRED:
                    return new StagnationResponse(new PlanExecutionResult(
                            state.getPlanStatus(), eventsObserved, decisionsEnacted,
                            countCompleted(state), state.getErrors().size(), phaseName));
                default:
                    throw new IllegalArgumentException(
                            "Unknown gate outcome: " + gateResult.getOutcome());
            }
        }
    }

    private StagnationResponse respondToStagnation(PlanExecutionState state,
                                                   List<StagnationEvent> eventsObserved,
                                                   List<ReplanDecision> decisionsEnacted) {
        List<StagnationEvent> events = detector.detect(state);
        if (events.isEmpty()) {
            return null;
        }
        eventsObserved.addAll(events);
        ReplanDecision decision = replanner.decide(events);
        decisionsEnacted.add(decision);
        replanner.apply(decision, state);
        return new StagnationResponse(new PlanExecutionResult(
                state.getPlanStatus(), eventsObserved, decisionsEnacted,
                countCompleted(state), state.getErrors().size(), state.getCurrentPhase()));
    }

    private List<AgentPlanTaskModel> readyTasksForPhase(PlanExecutionState state, AgentPlanPhase phase,
                                                        Set<String> phaseTaskNos) {
        List<AgentPlanTaskModel> ready = scheduler.getReadyTasks(state.getPlan(), state.statusProvider());
        List<AgentPlanTaskModel> inPhase = new ArrayList<>();
        for (AgentPlanTaskModel task : ready) {
            if (phaseTaskNos.contains(task.getTaskNo())) {
                inPhase.add(task);
            }
        }
        return inPhase;
    }

    private Set<String> collectTaskNos(AgentPlanPhase phase) {
        Set<String> nos = new HashSet<>();
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

    private static final class StagnationResponse {
        final PlanExecutionResult result;

        StagnationResponse(PlanExecutionResult result) {
            this.result = result;
        }
    }
}
