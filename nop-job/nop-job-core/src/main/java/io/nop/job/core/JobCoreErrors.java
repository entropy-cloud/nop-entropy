/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface JobCoreErrors {
    String ARG_LOOP_COUNT = "loopCount";
    String ARG_CRON_EXPR = "cronExpr";
    String ARG_JOB_NAME = "jobName";

    ErrorCode ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.job.trigger.loop-count-exceed-limit",
            "计算下一次触发时间时似乎陷入死循环，循环次数超过最大限制", ARG_LOOP_COUNT);

    ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL = define("nop.err.job.trigger.parse-cron-expr-fail",
            "解析定时器表达式失败:{cronExpr}", ARG_CRON_EXPR);

    ErrorCode ERR_JOB_TRIGGER_FIRE_FAIL = define("nop.err.job.trigger.fire-fail", "执行任务失败");

    ErrorCode ERR_JOB_INVALID_JOB_NAME = define("nop.err.job.invalid-job-name",
            "非法的任务名[{jobName}]，任务名应该与java类名类似，采用.分隔", ARG_JOB_NAME);

    ErrorCode ERR_JOB_EMPTY_INVOKER_NAME = define("nop.err.job.invalid-invoker-name", "任务执行器的名称不允许为空", ARG_JOB_NAME);

    ErrorCode ERR_JOB_ALREADY_EXISTS = define("nop.err.job.already-exists", "任务[{jobName}]已经存在，不能重复注册", ARG_JOB_NAME);

    ErrorCode ERR_JOB_ALREADY_FINISHED = define("nop.err.job.already-finished", "任务[{jobName}]已经结束，不应该再被调度",
            ARG_JOB_NAME);

    ErrorCode ERR_JOB_DEACTIVATED_SCHEDULER_NOT_ALLOW_OPERATION = define(
            "nop.err.job.deactivated-scheduler-not-allow-operation", "处于失活状态的调度器不允许执行操作");
}
