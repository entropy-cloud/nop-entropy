package io.nop.job.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface JobCoreErrors {
    String ARG_LOOP_COUNT = "loopCount";
    String ARG_CRON_EXPR = "cronExpr";
    String ARG_EXECUTOR_REF = "executorRef";
    String ARG_EXECUTOR_KIND = "executorKind";

    ErrorCode ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.job.trigger.loop-count-exceed-limit",
            "计算下一次触发时间时似乎陷入死循环，循环次数超过最大限制", ARG_LOOP_COUNT);

    ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL = define("nop.err.job.trigger.parse-cron-expr-fail",
            "解析定时器表达式失败:{cronExpr}", ARG_CRON_EXPR);

    // Error codes stored in task/fire errorCode field - status markers, not thrown exceptions.
    // Using the same string values for backward compatibility with stored data and test assertions.

    ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT",
            "Job task timed out");

    ErrorCode ERR_JOB_INVOKER_NOT_FOUND = define("JOB_INVOKER_NOT_FOUND",
            "Job invoker not found for schedule");

    ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED",
            "Job fire/task canceled");

    ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID",
            "Job fire/task canceled by overlay");

    ErrorCode ERR_JOB_EXECUTION_FAILED = define("JOB_EXECUTION_FAILED",
            "Job execution failed");

    ErrorCode ERR_JOB_EXECUTOR_REF_EMPTY = define("nop.err.job.executor-ref-empty",
            "Job的执行器引用为空");

    ErrorCode ERR_JOB_EXECUTOR_KIND_EMPTY = define("nop.err.job.executor-kind-empty",
            "Job的执行器类型为空");
}
