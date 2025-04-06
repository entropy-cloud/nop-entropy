/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.IJobInstanceState;
import io.nop.job.api.execution.JobFireResult;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ITriggerAction {
    CompletionStage<JobFireResult> execute(boolean forceFire, IJobInstanceState state, ICancelToken cancelToken);
}