package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.util.Collections;
import java.util.List;

/**
 * Specification of how to split a stalled task into runtime sub-tasks
 * (design §14.4.3 SPLIT_TASK semantics — subtask spec landing point).
 *
 * <p><b>Adjudication (avoids Protected-Area model change)</b>: the split
 * template is declared at construction time on {@link ReplanPolicy} (a
 * {@code Map<String, SplitSpec>} keyed by parent task number), <b>not</b> as a
 * new {@code <splitTemplate>} element in {@code agent-plan.xdef}. This mirrors
 * the {@link ReplanPolicy} adjudication for ROLLBACK triggers and avoids the
 * codegen cascade an xdef change would trigger
 * ({@code _AgentPlanTaskModel}/{@code _AgentPlan} regeneration). A declarative
 * xdef {@code <splitTemplate>} element can be layered on later as a
 * non-breaking successor that populates the same {@link ReplanPolicy}.
 *
 * <p>A {@code SplitSpec} carries the parent task number and an ordered list of
 * child {@link AgentPlanTaskModel templates} (each with its own
 * {@code taskNo}, {@code dependsOn}, {@code triggerRule}). On enactment
 * ({@link PlanReplanner#apply}), each template is registered as a runtime
 * overlay task under the parent's owning phase (the frozen template is never
 * mutated). The parent is marked as a split placeholder (treated as
 * completed) so it does not re-stall; its accumulated errors are resolved and
 * its failure counter zeroed to break the detect→split loop.
 *
 * <p>Child {@code taskNo}s must be globally unique (distinct from every frozen
 * task and from each other) — the executor's {@link PlanDagBuilder} DAG
 * uniqueness contract still applies once the overlay is visible to the
 * scheduler.
 *
 * <p>Immutable.
 */
public final class SplitSpec {

    private final String parentTaskNo;
    private final List<AgentPlanTaskModel> childTemplates;

    /**
     * @param parentTaskNo    the task to split (non-null, non-empty)
     * @param childTemplates  the runtime sub-task templates to insert
     *                        (non-null, non-empty; each must have a unique
     *                        {@code taskNo})
     */
    public SplitSpec(String parentTaskNo, List<AgentPlanTaskModel> childTemplates) {
        if (parentTaskNo == null || parentTaskNo.isEmpty()) {
            throw new IllegalArgumentException("parentTaskNo must not be null/empty");
        }
        if (childTemplates == null || childTemplates.isEmpty()) {
            throw new IllegalArgumentException("childTemplates must not be null/empty");
        }
        for (AgentPlanTaskModel t : childTemplates) {
            if (t == null || t.getTaskNo() == null || t.getTaskNo().isEmpty()) {
                throw new IllegalArgumentException("each child template must have a non-empty taskNo");
            }
        }
        this.parentTaskNo = parentTaskNo;
        this.childTemplates = Collections.unmodifiableList(childTemplates);
    }

    public String getParentTaskNo() {
        return parentTaskNo;
    }

    public List<AgentPlanTaskModel> getChildTemplates() {
        return childTemplates;
    }
}
