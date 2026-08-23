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
    String ARG_TASK_COST = "taskCost";
    String ARG_SERVICE_NAME = "serviceName";
    String ARG_HEALTHY_COUNT = "healthyCount";
    String ARG_CONFIG_NAME = "configName";
    String ARG_CONFIG_VALUE = "configValue";
    String ARG_INDEX = "index";

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

    ErrorCode ERR_JOB_INVALID_CONFIG_VALUE = define("nop.err.job.invalid-config-value",
            "Invalid job config [{configName}] value: {configValue}", ARG_CONFIG_NAME, ARG_CONFIG_VALUE);

    ErrorCode ERR_JOB_TASK_ATTRIBUTE_MISSING = define("nop.err.job.task-attribute-missing",
            "Job execution context attribute is missing: {configName}", ARG_CONFIG_NAME);

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
            "dispatchMode '{dispatchMode}' is not yet implemented (jobFireId={jobFireId}); use 'single', 'partition', 'broadcast', or 'bestFit' instead",
            ARG_DISPATCH_MODE, ARG_JOB_FIRE_ID);

    ErrorCode ERR_JOB_SERVICE_NAME_REQUIRED = define("nop.err.job.service-name-required",
            "serviceName is required for dispatchMode '{dispatchMode}' (jobFireId={jobFireId}); add a string 'serviceName' param to the job params",
            ARG_DISPATCH_MODE, ARG_JOB_FIRE_ID);

    ErrorCode ERR_JOB_DISCOVERY_CLIENT_REQUIRED = define("nop.err.job.discovery-client-required",
            "IDiscoveryClient is not injected for service '{serviceName}'; configure discovery to use broadcast/partition dispatch",
            ARG_SERVICE_NAME);

    ErrorCode ERR_JOB_NO_AVAILABLE_INSTANCE = define("nop.err.job.no-available-instance",
            "No healthy instance found for service '{serviceName}' (healthyCount={healthyCount}); dispatch-failed, fire stays DISPATCHING until timeout recovery",
            ARG_SERVICE_NAME, ARG_HEALTHY_COUNT);

    ErrorCode ERR_JOB_NO_FITTING_WORKER = define("nop.err.job.no-fitting-worker",
            "No worker can fit task cost {taskCost} for service '{serviceName}'; either reduce cost, add workers, or switch dispatchMode to single",
            ARG_TASK_COST);

    String ARG_ENFORCE_ATTRIBUTION = "enforceAttribution";

    // plan 340 §2.11 (P3-e): dedicated worker-pool mode requires hostId to be configured.
    ErrorCode ERR_JOB_WORKER_INSTANCE_ID_REQUIRED = define("nop.err.job.worker-instance-id-required",
            "fetchWaitingTasks called with enforceAttribution={enforceAttribution} but workerInstanceId is null/empty; "
                    + "configure the worker hostId before enabling dedicated-pool attribution",
            ARG_ENFORCE_ATTRIBUTION);

    // plan 2254: remote 模式（dispatchMode=remote）错误码
    String ARG_TASK_ID = "taskId";

    ErrorCode ERR_JOB_REMOTE_INVOKE_FAILED = define("nop.err.job.remote-invoke-failed",
            "Remote startJob invoke failed for task {taskId}; task marked FAILED, retry via nop-retry bridge",
            ARG_TASK_ID);

    ErrorCode ERR_JOB_REMOTE_TASK_LOST = define("nop.err.job.remote-task-lost",
            "Remote task {taskId} not found on worker (worker restarted or never received it); marked FAILED",
            ARG_TASK_ID);
}
