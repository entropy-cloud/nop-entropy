/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.job.api.execution.JobFireResult;

public interface ITriggerHook {
    void onSchedule(long currentTime, long nextScheduleTime, ITriggerContext context);

    void onBeginExecute(long currentTime, ITriggerContext context);

    void onEndExecute(long currentTime, ITriggerContext context);

    void onJobSuspended(long currentTime, ITriggerContext context);

    void onJobFinished(long currentTime, ITriggerContext context);

    void onException(long currentTime, Throwable exception, ITriggerContext context);

    void onError(long currentTime, JobFireResult result, ITriggerContext context);

    void onJobKilled(long currentTime, ITriggerContext context);

    void onInstanceCancelled(long currentTime, ITriggerContext context);

    void onBeginFireNow(long currentTime, ITriggerContext context);

    void onEndFireNow(JobFireResult result, Throwable exception, ITriggerContext context);
}