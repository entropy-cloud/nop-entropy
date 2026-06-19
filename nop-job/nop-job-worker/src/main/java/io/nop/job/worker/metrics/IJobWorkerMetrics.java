package io.nop.job.worker.metrics;

public interface IJobWorkerMetrics {
    void onTasksClaimed(int count);

    void onTaskSuccess(long durationMs);

    void onTaskFailure(long durationMs);

    void onTaskTimeout(long durationMs);

    void onRejected(int runningCount);

    /**
     * 单个已认领任务执行处理失败（loadFire/loadSchedule 抛异常等）时累加。
     * 用于 per-task 错误隔离的可观测信号（AR-86 worker 侧）。
     */
    void onTaskExecuteFailed(int count);
}
