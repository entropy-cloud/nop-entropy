/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface TaskErrors {
    String ARG_STEP_ID = "stepId";
    String ARG_STEP_TYPE = "stepType";
    String ARG_TASK_NAME = "taskName";
    String ARG_RUN_ID = "runId";

    ErrorCode ERR_TASK_NOT_ALLOW_INTERNAL_STEP_ID = define("nop.err.task.step.not-allow-internal-step-id",
            "步骤id与系统内置id冲突", ARG_TASK_NAME, ARG_STEP_ID);

    ErrorCode ERR_TASK_STEP_NOT_RESTARTABLE = define("nop.err.task.step.not-restartable",
            "步骤[{stepId}]不允许多次执行", ARG_TASK_NAME, ARG_STEP_ID);

    ErrorCode ERR_TASK_NULL_ASYNC_PROMISE = define("nop.err.task.step.null-async-promise",
            "异步步骤的线程局部变量[asyncPromise]不应该为null");

    ErrorCode ERR_TASK_ASYNC_RETURN_NEXT_STEP_SHOULD_NOT_BE_ASYNC =
            define("nop.err.task.step.async-return-next-step-shoud-no-be-async",
                    "异步步骤的返回结果不应为ASYNC标记");

    ErrorCode ERR_TASK_RETRY_TIMES_EXCEED_LIMIT =
            define("nop.err.task.step.retry-times-exceed-limit",
                    "步骤重试次数超过限制");
}
