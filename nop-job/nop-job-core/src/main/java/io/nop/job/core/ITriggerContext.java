/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core;

import io.nop.api.core.beans.ErrorBean;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.spec.ITriggerSpec;

/**
 * 记录触发器的状态信息
 *
 * @author canonical_entropy@163.com
 */
public interface ITriggerContext extends ITriggerState {

    long getMinScheduleTime();

    long getMaxScheduleTime();

    long getMaxExecutionCount();

    long getMaxFailedCount();

    void setMaxFailedCount(long maxFailedCount);

    void setMaxExecutionCount(long maxExecutionCount);

    void setMinScheduleTime(long minScheduleTime);

    void setMaxScheduleTime(long maxScheduleTime);


    default boolean isJobFinished() {
        return getTriggerStatus() >= NopJobCoreConstants.JOB_INSTANCE_STATUS_JOB_FINISHED;
    }

    default boolean isRunning() {
        return getTriggerStatus() == NopJobCoreConstants.JOB_INSTANCE_STATUS_RUNNING;
    }

    void onSchedule(long currentTime, long nextScheduleTime);

    void onBeginExecute(long currentTime);

    void onEndExecute(long currentTime);

    void onCompleted(long currentTime);

    void onException(long currentTime, ErrorBean exception);

    void onError(long currentTime, ErrorBean error);

    void onCancel(long currentTime);

    void onPaused(long currentTime);

    void onBeginFireNow(long currentTime);

    void onEndFireNow(long currentTime);

    void deactivate();

    void update(ITriggerSpec spec);
}