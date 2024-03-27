/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
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

    String ARG_REQUEST_PER_SECOND = "requestPerSecond";

    String ARG_GRAPH_STEP_NAME = "graphStepName";

    String ARG_LOOP_EDGES = "loopEdges";

    String ARG_TASK_INSTANCE_ID = "taskInstanceId";

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

    ErrorCode ERR_TASK_REQUEST_RATE_EXCEED_LIMIT =
            define("nop.err.task.request-rate-exceed-limit",
                    "访问速率超过限制：TPS={}", ARG_REQUEST_PER_SECOND);

    ErrorCode ERR_TASK_UNSUPPORTED_STEP_TYPE =
            define("nop.err.task.unsupported-step-type",
                    "不支持节点[{stepName}]的类型:{stepType}", ARG_STEP_NAME, ARG_STEP_TYPE);

    ErrorCode ERR_TASK_GRAPH_STEP_NO_ENTER_STEPS =
            define("nop.err.task.graph-step-no-enter-steps",
                    "流程图[{stepName}]必须指定起始步骤", ARG_STEP_NAME);

    ErrorCode ERR_TASK_GRAPH_STEP_NO_EXIT_STEPS =
            define("nop.err.task.graph-step-no-exit-steps",
                    "流程图[{stepName}]必须指定终止步骤", ARG_STEP_NAME);

    ErrorCode ERR_TASK_UNKNOWN_STEP_IN_GRAPH =
            define("nop.err.task.unknown-step-in-graph",
                    "流程图[{graphStepName}]中没有定义步骤[{stepName}]", ARG_GRAPH_STEP_NAME, ARG_STEP_NAME);

    ErrorCode ERR_TASK_GRAPH_STEP_CONTAINS_LOOP =
            define("nop.err.task.graph-step-contains-loop",
                    "流程图[{graphStepName}]包含循环结构，不满足要求，需要删除以下连接:{loopEdges}", ARG_GRAPH_STEP_NAME, ARG_LOOP_EDGES);


    ErrorCode ERR_TASK_NO_PERSIST_STATE_STORE =
            define("nop.err.task.nop-persist-state-store",
                    "没有定义持久化任务状态存储");

    ErrorCode ERR_TASK_UNKNOWN_TASK_INSTANCE =
            define("nop.err.task.unknown-task-instance", "未知的任务实例:{taskInstanceId}",
                    ARG_TASK_INSTANCE_ID);
}
