package io.nop.task.metrics;

public interface ITaskFlowMetrics {
    Object beginTask();

    void endTask(Object meter, boolean success);

    Object beginStep(String stepId, String stepType);

    void endStep(Object meter, boolean success);
}
