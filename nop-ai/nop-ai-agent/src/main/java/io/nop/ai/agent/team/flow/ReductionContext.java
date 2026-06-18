package io.nop.ai.agent.team.flow;

/**
 * Context handed to {@link IReductionStrategy#reduce} for a single fan-out
 * node (plan 244 / L4-multi-member-per-task-routing).
 *
 * <p>Exposes the shared {@link ExecutionRecorder} (so the strategy can
 * record markFailed/markComplete exactly once — multiple fan-out nodes in
 * the same DAG share the recorder) and the task id. The orchestrator
 * constructs one context per node reduction; the strategy does not retain
 * it beyond the reduce call.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing).
 */
public final class ReductionContext {

    private final String taskId;
    private final ExecutionRecorder recorder;

    /**
     * Build an immutable reduction context.
     *
     * @param taskId   the team task id being reduced (non-null)
     * @param recorder the shared execution recorder (non-null)
     */
    public ReductionContext(String taskId, ExecutionRecorder recorder) {
        this.taskId = java.util.Objects.requireNonNull(taskId, "taskId");
        this.recorder = java.util.Objects.requireNonNull(recorder, "recorder");
    }

    /**
     * @return the team task id being reduced.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * @return the shared execution recorder for the current DAG run.
     */
    public ExecutionRecorder getRecorder() {
        return recorder;
    }
}
