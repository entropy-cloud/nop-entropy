package io.nop.job.core;

/**
 * Trigger 纯计算阶段使用的只读上下文。
 */
public interface ITriggerEvalContext {
    long getFireCount();

    long getLastScheduledTime();

    long getLastEndTime();

    long getMinScheduleTime();

    long getMaxScheduleTime();

    long getMaxExecutionCount();

    boolean isScheduleCompleted();
}
