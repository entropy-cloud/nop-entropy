package io.nop.job.coordinator.metrics;

public interface IJobDispatcherMetrics {
    void onWaitingFires(int count);

    void onDispatchConflicts(int count);

    void onFiresDispatched(int count);

    /**
     * 单个 fire 派发失败（含被 no-fitting-worker 回退 backoff）时累加。
     * 用于 per-fire 错误隔离的可观测信号（AR-86）。
     */
    void onFireDispatchFailed(int count);
}
