/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface JobApiErrors {
    String ARG_JOB_NAME = "jobName";

    ErrorCode ERR_JOB_UNKNOWN_JOB = define("nop.err.job.unknown-job", "未知的任务:{jobName}", ARG_JOB_NAME);
}
