package io.nop.xlang.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个列在一次单元执行中的判定结果：PASS / FAIL（逐层失败原因）/ 执行明细。
 */
public final class ColumnOutcome {
    public enum Status {
        PASS, FAIL
    }

    private final String backendId;
    private final Status status;
    private final List<String> failures;
    private final BackendExecutionResult execution;
    private final SideEffectSnapshot snapshot;

    public ColumnOutcome(String backendId, Status status, List<String> failures,
                         BackendExecutionResult execution, SideEffectSnapshot snapshot) {
        this.backendId = backendId;
        this.status = status;
        this.failures = failures == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(failures));
        this.execution = execution;
        this.snapshot = snapshot;
    }

    public String getBackendId() {
        return backendId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPassed() {
        return status == Status.PASS;
    }

    public List<String> getFailures() {
        return failures;
    }

    public BackendExecutionResult getExecution() {
        return execution;
    }

    public SideEffectSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public String toString() {
        return backendId + ":" + status + (failures.isEmpty() ? "" : " " + failures);
    }
}
