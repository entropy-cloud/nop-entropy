package io.nop.batch.dao;

import io.nop.api.core.exceptions.ErrorCode;

public interface NopBatchDaoErrors {
    String ARG_TASK_NAME = "taskName";
    String ARG_TASK_ID = "taskId";
    String ARG_TASK_KEY = "taskKey";
    String ARG_TASK_STATUS = "taskStatus";

    ErrorCode ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE =
            ErrorCode.define("nop.err.batch.task-not-allow-start-when-exist-running-instance",
                    "批处理任务[{taskName}-{taskKey}]的状态为[{taskStatus}], 尚未结束，不允许再次启动",
                    ARG_TASK_NAME, ARG_TASK_KEY, ARG_TASK_ID, ARG_TASK_STATUS);

    ErrorCode ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED = ErrorCode.define("nop.err.batch.task-not-allow-start-when-killed",
            "批处理任务[{taskName}-{taskKey}]的状态为[{taskStatus}], 已经被终止，不允许再次启动",
            ARG_TASK_NAME, ARG_TASK_KEY, ARG_TASK_ID, ARG_TASK_STATUS);

    ErrorCode ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED = ErrorCode.define("nop.err.batch.task-not-allow-start-when-completed",
            "批处理任务[{taskName}-{taskKey}]的状态为[{taskStatus}], 已经完成，不允许再次启动",
            ARG_TASK_NAME, ARG_TASK_KEY, ARG_TASK_ID, ARG_TASK_STATUS);

    ErrorCode ERR_BATCH_TASK_EXCEED_START_LIMIT = ErrorCode.define("nop.err.batch.task-exceed-start-limit",
            "批处理任务[{taskName}-{taskKey}]启动次数超过限制，不允许再次启动",
            ARG_TASK_NAME, ARG_TASK_KEY, ARG_TASK_ID, ARG_TASK_STATUS);
}
