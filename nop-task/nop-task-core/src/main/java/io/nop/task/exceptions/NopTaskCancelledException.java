package io.nop.task.exceptions;

import io.nop.api.core.exceptions.NopSingletonException;

import static io.nop.task.TaskErrors.ERR_TASK_CANCELLED;

public class NopTaskCancelledException extends NopSingletonException {

    public static final NopTaskCancelledException INSTANCE = new NopTaskCancelledException();

    private NopTaskCancelledException() {
        super(ERR_TASK_CANCELLED);
    }
}
