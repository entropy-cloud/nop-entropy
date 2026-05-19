package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;

public interface IJobTaskStore {
    void updateTask(NopJobTask task);

    List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions);

    List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs);

    List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions);

    List<NopJobTask> findTasksByFireId(String jobFireId);

    NopJobTask loadTask(String jobTaskId);

    long countRunningTasks(String workerInstanceId);
}
