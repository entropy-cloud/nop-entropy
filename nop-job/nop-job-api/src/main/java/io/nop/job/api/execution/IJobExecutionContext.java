package io.nop.job.api.execution;

import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.IJobInstanceState;

public interface IJobExecutionContext extends IJobInstanceState {

    long getMinScheduleTime();

    long getMaxScheduleTime();

    long getMaxExecutionCount();

    long getMaxFailedCount();

    boolean isJobFinished();

    boolean isInstanceRunning();

    boolean isScheduleEnabled();

    default ICancelToken getCancelToken(){
        return null;
    }
}