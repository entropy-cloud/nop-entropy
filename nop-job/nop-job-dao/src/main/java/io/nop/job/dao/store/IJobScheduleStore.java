package io.nop.job.dao.store;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IJobScheduleStore {
    List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions);

    List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId,
                                                 long lockTimeoutMs);

    void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime);

    void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                      Integer lastFireStatus);

    void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                       Integer lastFireStatus);

    void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime);

    boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire);

    NopJobSchedule loadSchedule(String jobScheduleId);

    Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds);

    long getCurrentTime();
}
