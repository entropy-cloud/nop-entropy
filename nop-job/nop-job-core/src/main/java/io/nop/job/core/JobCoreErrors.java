package io.nop.job.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface JobCoreErrors {
    String ARG_LOOP_COUNT = "loopCount";
    String ARG_CRON_EXPR = "cronExpr";

    ErrorCode ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.job.trigger.loop-count-exceed-limit",
            "计算下一次触发时间时似乎陷入死循环，循环次数超过最大限制", ARG_LOOP_COUNT);

    ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL = define("nop.err.job.trigger.parse-cron-expr-fail",
            "解析定时器表达式失败:{cronExpr}", ARG_CRON_EXPR);
}
