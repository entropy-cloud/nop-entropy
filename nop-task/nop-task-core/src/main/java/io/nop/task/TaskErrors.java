/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface TaskErrors {
    String ARG_STEP_ID = "stepId";
    String ARG_STEP_NAME = "stepName";
    String ARG_STEP_TYPE = "stepType";
    String ARG_TASK_NAME = "taskName";
    String ARG_RUN_ID = "runId";

    String ARG_OUTPUT = "output";

    String ARG_NEXT_STEP = "nextStep";

    String ARG_BEGIN = "begin";

    String ARG_END = "end";

    String ARG_STEP = "step";

    String ARG_LIB_NAME = "libName";

    ErrorCode ERR_TASK_STEP_NOT_RESTARTABLE = define("nop.err.task.step.not-restartable",
            "步骤[{stepName}]不允许多次执行", ARG_TASK_NAME, ARG_STEP_NAME);

    ErrorCode ERR_TASK_NULL_ASYNC_PROMISE = define("nop.err.task.step.null-async-promise",
            "异步步骤的线程局部变量[asyncPromise]不应该为null");

    ErrorCode ERR_TASK_ASYNC_RETURN_NEXT_STEP_SHOULD_NOT_BE_ASYNC =
            define("nop.err.task.step.async-return-next-step-should-no-be-async",
                    "异步步骤的返回结果不应为ASYNC标记");

    ErrorCode ERR_TASK_RETRY_TIMES_EXCEED_LIMIT =
            define("nop.err.task.step.retry-times-exceed-limit",
                    "步骤重试次数超过限制");

    ErrorCode ERR_TASK_CANCELLED =
            define("nop.err.task.cancelled", "任务已经被取消");

    ErrorCode ERR_TASK_STEP_TIMEOUT =
            define("nop.err.task.step-timeout", "步骤已超时");

    ErrorCode ERR_TASK_STEP_MANDATORY_OUTPUT_IS_EMPTY =
            define("nop.err.task.step-mandatory-output-is-empty", "步骤[{stepId}]的输出[{output}]不允许为空",
                    ARG_STEP_ID, ARG_OUTPUT);

    ErrorCode ERR_TASK_UNKNOWN_NEXT_STEP =
            define("nop.err.task.unknown-next-step", "步骤[{stepId}]不支持跳转到子步骤[{nextStep}]",
                    ARG_STEP_ID, ARG_NEXT_STEP);

    ErrorCode ERR_TASK_LOOP_STEP_INVALID_LOOP_VAR =
            define("nop.err.task.loop-step-invalid-loop-var", "循环步骤的循环变量设置不正确：begin={begin},end={end},step={step}",
                    ARG_BEGIN, ARG_END, ARG_STEP);

    ErrorCode ERR_TASK_UNKNOWN_STEP_IN_LIB =
            define("nop.err.task.unknown-step-in-lib", "任务库[{libName}]中没有定义步骤:[{stepName}]",
                    ARG_LIB_NAME, ARG_STEP_NAME);
}
