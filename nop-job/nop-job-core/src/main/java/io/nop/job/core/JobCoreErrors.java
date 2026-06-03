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
            "Trigger calculation loop count exceeded limit", ARG_LOOP_COUNT);

    ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL = define("nop.err.job.trigger.parse-cron-expr-fail",
            "Failed to parse cron expression:{cronExpr}", ARG_CRON_EXPR);

    // Error codes stored in task/fire errorCode field - status markers, not thrown exceptions.
    // Using the same string values for backward compatibility with stored data and test assertions.

    ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT",
            "Job task timed out");

    ErrorCode ERR_JOB_INVOKER_NOT_FOUND = define("nop.err.job.invoker-not-found",
            "Job invoker not found for schedule");

    ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED",
            "Job fire/task canceled");

    ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID",
            "Job fire/task canceled by overlay");

    ErrorCode ERR_JOB_EXECUTION_FAILED = define("nop.err.job.execution-failed",
            "Job execution failed");

    ErrorCode ERR_JOB_EXECUTOR_REF_EMPTY = define("nop.err.job.executor-ref-empty",
            "Job executor ref is empty");

    ErrorCode ERR_JOB_EXECUTOR_KIND_EMPTY = define("nop.err.job.executor-kind-empty",
            "Job executor kind is empty");

    ErrorCode ERR_JOB_FIRE_STATUS_CONFLICT = define("nop.err.job.fire-status-conflict",
            "Fire status version conflict during dispatch");

    ErrorCode ERR_JOB_SCHEDULE_DELETED = define("nop.err.job.schedule-deleted",
            "Schedule has been deleted");

    ErrorCode ERR_JOB_INVOKER_RETURNED_NULL = define("JOB_INVOKER_RETURNED_NULL",
            "Job invoker returned null promise");
}
