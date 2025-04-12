/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.api.core.beans.ErrorBean;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.ITriggerSpec;

/**
 * 记录触发器的状态信息
 *
 * @author canonical_entropy@163.com
 */
public interface ITriggerContext extends IJobExecutionContext {

    void setScheduleEnabled(boolean scheduleEnabled);

    void setMaxFailedCount(long maxFailedCount);

    void setMaxExecutionCount(long maxExecutionCount);

    void setMinScheduleTime(long minScheduleTime);

    void setMaxScheduleTime(long maxScheduleTime);


    default boolean isJobFinished() {
        return getInstanceStatus() >= NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED;
    }

    default boolean isInstanceRunning() {
        return getInstanceStatus() == NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING;
    }

    void onSchedule(long currentTime, long nextScheduleTime);

    void onInstanceBeginExecute(long currentTime);

    void onInstanceSuccess(long currentTime);

    void onInstanceFailed(long currentTime, ErrorBean exception);

    void onInstanceCancelled(long currentTime);

    void onInstanceTimeout(long currentTime);

    void onBeginFireNow(long currentTime);

    void onEndFireNow(long currentTime, JobFireResult result, Throwable err);

    void onJobFinished(long currentTime);

    void onJobFailed(long currentTime, ErrorBean error);

    void onJobSuspended(long currentTime);

    void onJobKilled(long currentTime);

    void update(ITriggerSpec spec);
}