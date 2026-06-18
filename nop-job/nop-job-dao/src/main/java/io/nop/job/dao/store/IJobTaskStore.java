package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;

public interface IJobTaskStore {
    boolean updateTask(NopJobTask task);

    List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions);

    List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs);

    List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions);

    List<NopJobTask> findTasksByFireId(String jobFireId);

    NopJobTask loadTask(String jobTaskId);

    long countInFlightTasks(String workerInstanceId);

    /**
     * 单 worker 已归因 task cost 聚合求和（WAITING + CLAIMED + SUSPICIOUS + RUNNING）。
     * 用于 worker 侧 {@code JobWorkerScannerImpl.scanOnce} 评估 myRemaining = myCapacity - myReserved。
     * <p>
     * 与 {@link #countInFlightTasks(String)} 状态集故意不对称（design §3.3.4）。
     * 无匹配行时返回 {@link ResourceVector#ZERO}。
     *
     * @param workerInstanceId worker 实例 id（通常为 {@code AppConfig.hostId()}）
     */
    ResourceVector sumReservedCost(String workerInstanceId);
}
