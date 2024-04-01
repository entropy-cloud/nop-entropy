package io.nop.task.metrics;

public class EmptyTaskFlowMetrics implements ITaskFlowMetrics {
    public static final EmptyTaskFlowMetrics INSTANCE = new EmptyTaskFlowMetrics();

    @Override
    public Object beginTask() {
        return null;
    }

    @Override
    public void endTask(Object meter, boolean success) {

    }

    @Override
    public Object beginStep(String stepId, String stepType) {
        return null;
    }

    @Override
    public void endStep(Object meter, boolean endStep) {

    }
}
