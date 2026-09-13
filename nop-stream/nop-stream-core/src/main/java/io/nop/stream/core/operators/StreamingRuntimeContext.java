package io.nop.stream.core.operators;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;
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
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "Keyed state is only available on a keyed stream.");
        }
        return keyedStateStore;
    }

    @Override
    public TimerService getTimerService() {
        if (timerService == null) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, "Timers are only available on a keyed stream.");
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
