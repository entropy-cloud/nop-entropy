package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;

public interface IJobFireStore {
    List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions);

    List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions);

    List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                             long lockTimeoutMs);

    void insertTaskAndMarkFireDispatching(NopJobFire fire, NopJobTask task);

    void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule);

    boolean cancelFire(String jobFireId);

    NopJobFire loadFire(String jobFireId);
}
