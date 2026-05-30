package io.nop.stream.core.operators;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.common.functions.RuntimeContext;

@Internal
public class StreamingRuntimeContext implements RuntimeContext {

    private int indexOfThisSubtask;
    private int numberOfParallelSubtasks;
    private String taskName;

    public StreamingRuntimeContext() {
    }

    public StreamingRuntimeContext(int indexOfThisSubtask, int numberOfParallelSubtasks, String taskName) {
        this.indexOfThisSubtask = indexOfThisSubtask;
        this.numberOfParallelSubtasks = numberOfParallelSubtasks;
        this.taskName = taskName;
    }

    @Override
    public int getIndexOfThisSubtask() {
        return indexOfThisSubtask;
    }

    @Override
    public int getNumberOfParallelSubtasks() {
        return numberOfParallelSubtasks;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }
}
