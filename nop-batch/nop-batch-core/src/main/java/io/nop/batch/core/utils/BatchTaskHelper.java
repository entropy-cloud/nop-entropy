package io.nop.batch.core.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchTaskContext;

import static io.nop.batch.core.BatchErrors.ARG_TASK_ID;
import static io.nop.batch.core.BatchErrors.ARG_TASK_KEY;
import static io.nop.batch.core.BatchErrors.ARG_TASK_NAME;

public class BatchTaskHelper {
    public static NopException newTaskError(IBatchTaskContext context, ErrorCode errorCode) {
        throw new NopException(errorCode).param(ARG_TASK_NAME, context.getTaskName())
                .param(ARG_TASK_ID, context.getTaskId())
                .param(ARG_TASK_KEY, context.getTaskKey());
    }
}
