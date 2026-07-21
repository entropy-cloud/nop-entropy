package io.nop.stream.core.operators;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.time.TimerService;

@Internal
public class StreamingRuntimeContext implements RuntimeContext {

    private int indexOfThisSubtask;
    private int numberOfParallelSubtasks;
    private String taskName;
    private KeyedStateStore keyedStateStore;
    private TimerService timerService;

    public StreamingRuntimeContext() {
    }

    public StreamingRuntimeContext(int indexOfThisSubtask, int numberOfParallelSubtasks, String taskName) {
        this.indexOfThisSubtask = indexOfThisSubtask;
        this.numberOfParallelSubtasks = numberOfParallelSubtasks;
        this.taskName = taskName;
    }

    public StreamingRuntimeContext(int indexOfThisSubtask, int numberOfParallelSubtasks, String taskName,
                                   KeyedStateStore keyedStateStore, TimerService timerService) {
        this.indexOfThisSubtask = indexOfThisSubtask;
        this.numberOfParallelSubtasks = numberOfParallelSubtasks;
        this.taskName = taskName;
        this.keyedStateStore = keyedStateStore;
        this.timerService = timerService;
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

    @Override
    public KeyedStateStore getKeyedStateStore() {
        if (keyedStateStore == null) {
            throw new UnsupportedOperationException("Keyed state is only available on a keyed stream.");
        }
        return keyedStateStore;
    }

    @Override
    public TimerService getTimerService() {
        if (timerService == null) {
            throw new UnsupportedOperationException("Timers are only available on a keyed stream.");
        }
        return timerService;
    }

    public void setKeyedStateStore(KeyedStateStore keyedStateStore) {
        this.keyedStateStore = keyedStateStore;
    }

    public void setTimerService(TimerService timerService) {
        this.timerService = timerService;
    }
}
