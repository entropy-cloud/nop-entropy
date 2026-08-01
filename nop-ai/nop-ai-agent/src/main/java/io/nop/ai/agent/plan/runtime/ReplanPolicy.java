package io.nop.ai.agent.plan.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Construction-time configuration that maps stagnation signals to recoverable
 * replan decisions (design §14.4.2 trigger conditions).
 *
 * <p><b>Adjudication (avoids Protected-Area model change)</b>: ROLLBACK_PHASE
 * and SPLIT_TASK trigger conditions are declared at construction time on
 * {@link PlanReplanner} rather than as new elements in
 * {@code agent-plan.xdef}. Reasons:
 * <ul>
 *   <li>The {@code GateOnFail} enum ({@code retry|block|escalate}) is
 *       <b>not</b> expanded — gate {@code on-fail} stays the gate-channel;
 *       replan triggers are a separate, replanner-owned concern.</li>
 *   <li>The replanner is a runtime component whose policy is naturally a
 *       construction parameter — the same pattern already used by
 *       {@link StagnationDetector#StagnationDetector(int, int)} for its
 *       thresholds.</li>
 *   <li>Avoids the codegen cascade an xdef change would trigger
 *       ({@code _AgentPlan}/{@code _AgentPlanPhase}/{@code _AgentPlanTaskModel}
 *       regeneration, {@code GateCheckResult} changes).</li>
 *   <li>Trigger thresholds are production-tuning territory (Non-Blocking
 *       Follow-up); declarative xdef elements can be layered on later as a
 *       non-breaking successor that populates the same policy.</li>
 * </ul>
 *
 * <p><b>Mapping</b>:
 * <ul>
 *   <li>A phase registered via {@link #rollbackTarget(String)} is
 *       <em>rollback-eligible</em>: when stagnation is observed on that phase
 *       (or on a task owned by it), the replanner produces a ROLLBACK_PHASE
 *       result whose target is the registered preceding phase.</li>
 *   <li>A task registered via a {@link SplitSpec} in {@code splitSpecs} is
 *       <em>split-eligible</em>: when TASK_STALLED is observed on it, the
 *       replanner produces a SPLIT_TASK result and {@link PlanReplanner#apply}
 *       inserts the spec's child templates as runtime overlay tasks.</li>
 *   <li>Any signal not covered by the policy falls back to ESCALATE
 *       (zero-regression with {@link #escalateOnly()}).</li>
 * </ul>
 *
 * <p>Immutable and thread-safe.
 */
public final class ReplanPolicy {

    private final Map<String, String> rollbackTargets;
    private final Map<String, SplitSpec> splitSpecs;

    /**
     * @param rollbackTargets maps a phase name to the preceding phase to roll
     *                        back to when stagnation is observed on that phase
     *                        (or on a task it owns); never null
     * @param splitSpecs      maps a parent task number to its {@link SplitSpec};
     *                        a task with a spec is split-eligible; never null
     */
    public ReplanPolicy(Map<String, String> rollbackTargets, Map<String, SplitSpec> splitSpecs) {
        if (rollbackTargets == null) {
            throw new IllegalArgumentException("rollbackTargets must not be null");
        }
        if (splitSpecs == null) {
            throw new IllegalArgumentException("splitSpecs must not be null");
        }
        this.rollbackTargets = Collections.unmodifiableMap(new LinkedHashMap<>(rollbackTargets));
        this.splitSpecs = Collections.unmodifiableMap(new LinkedHashMap<>(splitSpecs));
    }

    /** Default policy: no recoverable decisions — every signal escalates (legacy behavior). */
    public static ReplanPolicy escalateOnly() {
        return new ReplanPolicy(Collections.emptyMap(), Collections.emptyMap());
    }

    /** Whether the given phase has a registered rollback target. */
    public boolean hasRollbackTarget(String phaseName) {
        return phaseName != null && rollbackTargets.containsKey(phaseName);
    }

    /** The preceding phase to roll back to for the given phase, or {@code null} if none. */
    public String rollbackTarget(String phaseName) {
        return phaseName == null ? null : rollbackTargets.get(phaseName);
    }

    /** Whether the given task has a registered {@link SplitSpec} (split-eligible). */
    public boolean isSplittable(String taskNo) {
        return taskNo != null && splitSpecs.containsKey(taskNo);
    }

    /** The {@link SplitSpec} for the given parent task, or {@code null} if not split-eligible. */
    public SplitSpec splitSpecFor(String taskNo) {
        return taskNo == null ? null : splitSpecs.get(taskNo);
    }

    /** A convenience view of the registered split-eligible task numbers. */
    public Set<String> splittableTasks() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(splitSpecs.keySet()));
    }
}
