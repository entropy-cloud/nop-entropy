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
    String ARG_METADATA_KEY = "metadataKey";
    String ARG_METADATA_VALUE = "metadataValue";
    String ARG_WORKER_INSTANCE_ID = "workerInstanceId";
    String ARG_DISPATCH_MODE = "dispatchMode";
    String ARG_JOB_FIRE_ID = "jobFireId";

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

    ErrorCode ERR_JOB_CALENDAR_MAX_ITERATION_EXCEEDED = define("nop.err.job.calendar.max-iteration-exceeded",
            "Calendar getNextIncludedTime exceeded max iteration limit");

    ErrorCode ERR_JOB_CALENDAR_INVALID_TIMESTAMP = define("nop.err.job.calendar.invalid-timestamp",
            "Timestamp must be greater than 0");

    ErrorCode ERR_JOB_CALENDAR_NULL_EXPRESSION = define("nop.err.job.calendar.null-expression",
            "Cron expression cannot be null");

    ErrorCode ERR_JOB_CALENDAR_INVALID_TIME_STRING = define("nop.err.job.calendar.invalid-time-string",
            "Invalid time string");

    ErrorCode ERR_JOB_CALENDAR_INVALID_TIME_RANGE = define("nop.err.job.calendar.invalid-time-range",
            "Invalid time range");

    ErrorCode ERR_JOB_CALENDAR_INVALID_HOUR = define("nop.err.job.calendar.invalid-hour",
            "Invalid hour of day");

    ErrorCode ERR_JOB_CALENDAR_INVALID_MINUTE = define("nop.err.job.calendar.invalid-minute",
            "Invalid minute");

    ErrorCode ERR_JOB_CALENDAR_INVALID_SECOND = define("nop.err.job.calendar.invalid-second",
            "Invalid second");

    ErrorCode ERR_JOB_CALENDAR_INVALID_MILLIS = define("nop.err.job.calendar.invalid-millis",
            "Invalid milliseconds");

    ErrorCode ERR_JOB_CALENDAR_INVALID_DAY = define("nop.err.job.calendar.invalid-day",
            "Invalid day parameter");

    ErrorCode ERR_JOB_CALENDAR_NULL_DAYS = define("nop.err.job.calendar.null-days",
            "Days parameter cannot be null");

    ErrorCode ERR_JOB_WORKER_CAPACITY_MALFORMED = define("nop.err.job.worker-capacity-malformed",
            "Worker capacity metadata value is not a valid integer: {metadataKey}={metadataValue}", ARG_METADATA_KEY, ARG_METADATA_VALUE);

    ErrorCode ERR_JOB_WORKER_CAPACITY_PROVIDER_REQUIRED = define("nop.err.job.worker-capacity-provider-required",
            "IWorkerCapacityProvider is not injected; cannot evaluate worker-side resource limit");

    ErrorCode ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED = define("nop.err.job.dispatch-mode-not-implemented",
            "dispatchMode '{dispatchMode}' is not yet implemented (jobFireId={jobFireId}); use 'single', 'partition', or 'broadcast' instead",
            ARG_DISPATCH_MODE, ARG_JOB_FIRE_ID);
}
