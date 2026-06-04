/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.service;

import io.nop.api.core.exceptions.ErrorCode;

public interface NopJobErrors{
    ErrorCode ERR_JOB_SCHEDULE_ALREADY_ARCHIVED = ErrorCode.define(
            "nop.err.job.schedule.already-archived",
            "Archived schedule cannot be enabled or resumed"
    );

    ErrorCode ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION = ErrorCode.define(
            "nop.err.job.schedule.invalid-status-transition",
            "Schedule action is not allowed for the current status"
    );

    ErrorCode ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.schedule.manual-trigger-not-allowed",
            "Manual trigger is not allowed for the current schedule status"
    );

    ErrorCode ERR_JOB_FIRE_CANCEL_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.fire.cancel-not-allowed",
            "Fire cancel is not allowed for the current fire status"
    );

    ErrorCode ERR_JOB_FIRE_RERUN_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.fire.rerun-not-allowed",
            "Fire rerun is not allowed for the current fire status"
    );

    ErrorCode ERR_JOB_SCHEDULE_MANUAL_TRIGGER_DISCARDED = ErrorCode.define(
            "nop.err.job.schedule.manual-trigger-discarded",
            "Manual trigger discarded because active fires exist and block strategy is DISCARD"
    );

    ErrorCode ERR_JOB_FIRE_RERUN_DISCARDED = ErrorCode.define(
            "nop.err.job.fire.rerun-discarded",
            "Fire rerun discarded because active fires exist and block strategy is DISCARD"
    );

    ErrorCode ERR_JOB_TASK_DELETE_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.task.delete-not-allowed",
            "Job tasks must be managed via the Store layer, not through direct CRUD operations"
    );

    ErrorCode ERR_JOB_SCHEDULE_DELETE_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.schedule.delete-not-allowed",
            "Job schedules must be archived, not directly deleted"
    );

    ErrorCode ERR_JOB_FIRE_DELETE_NOT_ALLOWED = ErrorCode.define(
            "nop.err.job.fire.delete-not-allowed",
            "Job fires must be managed via the Store layer, not through direct CRUD operations"
    );

    String ARG_PARAM_NAME = "paramName";

    ErrorCode ERR_RPC_INVOKER_MISSING_PARAM = ErrorCode.define(
            "nop.err.job.rpc-invoker-missing-param",
            "RPC invoker missing required parameter: {paramName}"
    );
}
