package io.nop.batch.dsl;

import io.nop.api.core.exceptions.ErrorCode;

public interface BatchDslErrors {
    String ARG_BATCH_TASK_NAME = "batchTaskName";

    ErrorCode ERR_BATCH_TASK_NO_LOADER =
            ErrorCode.define("nop.err.batch.task-no-loader", "批处理任务没有定义loader", ARG_BATCH_TASK_NAME);
}
