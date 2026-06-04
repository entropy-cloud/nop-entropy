/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface JobApiErrors {
    String ARG_JOB_NAME = "jobName";
    String ARG_PARAM_NAME = "paramName";

    ErrorCode ERR_JOB_UNKNOWN_JOB = define("nop.err.job.unknown-job", "Unknown job:{jobName}", ARG_JOB_NAME);

    ErrorCode ERR_JOB_ALREADY_EXISTS = define("nop.err.job.already-exists", "Job already exists:{jobName}", ARG_JOB_NAME);

    ErrorCode ERR_JOB_SCHEDULER_NOT_ACTIVE = define("nop.err.job.scheduler-not-active", "Scheduler is not active");

    ErrorCode ERR_JOB_SCHEDULE_ALREADY_ARCHIVED = define(
            "nop.err.job.schedule.already-archived",
            "Archived schedule cannot be enabled or resumed"
    );

    ErrorCode ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION = define(
            "nop.err.job.schedule.invalid-status-transition",
            "Schedule action is not allowed for the current status"
    );

    ErrorCode ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED = define(
            "nop.err.job.schedule.manual-trigger-not-allowed",
            "Manual trigger is not allowed for the current schedule status"
    );

    ErrorCode ERR_JOB_FIRE_CANCEL_NOT_ALLOWED = define(
            "nop.err.job.fire.cancel-not-allowed",
            "Fire cancel is not allowed for the current fire status"
    );

    ErrorCode ERR_JOB_FIRE_RERUN_NOT_ALLOWED = define(
            "nop.err.job.fire.rerun-not-allowed",
            "Fire rerun is not allowed for the current fire status"
    );

    ErrorCode ERR_JOB_SCHEDULE_MANUAL_TRIGGER_DISCARDED = define(
            "nop.err.job.schedule.manual-trigger-discarded",
            "Manual trigger discarded because active fires exist and block strategy is DISCARD"
    );

    ErrorCode ERR_JOB_FIRE_RERUN_DISCARDED = define(
            "nop.err.job.fire.rerun-discarded",
            "Fire rerun discarded because active fires exist and block strategy is DISCARD"
    );

    ErrorCode ERR_JOB_TASK_DELETE_NOT_ALLOWED = define(
            "nop.err.job.task.delete-not-allowed",
            "Job tasks must be managed via the Store layer, not through direct CRUD operations"
    );

    ErrorCode ERR_JOB_FIRE_DELETE_NOT_ALLOWED = define(
            "nop.err.job.fire.delete-not-allowed",
            "Job fires must be managed via the Store layer, not through direct CRUD operations"
    );

    ErrorCode ERR_JOB_SCHEDULE_DELETE_NOT_ALLOWED = define(
            "nop.err.job.schedule.delete-not-allowed",
            "Job schedules must be archived, not directly deleted"
    );

    ErrorCode ERR_RPC_INVOKER_MISSING_PARAM = define(
            "nop.err.job.rpc-invoker-missing-param",
            "RPC invoker missing required parameter: {paramName}"
    );
}
