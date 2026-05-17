package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IJobFireStore {
    List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions);

    List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions);

    List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                             long lockTimeoutMs);

    void insertTaskAndMarkFireDispatching(NopJobFire fire, NopJobTask task);

    void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks);

    void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule);

    boolean cancelFire(String jobFireId);

    NopJobFire loadFire(String jobFireId);

    Map<String, NopJobFire> batchLoadFires(Set<String> fireIds);
}
