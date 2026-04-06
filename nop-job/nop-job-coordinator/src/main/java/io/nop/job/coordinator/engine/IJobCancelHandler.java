package io.nop.job.coordinator.engine;

import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

public interface IJobCancelHandler {
    void cancelRunningTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);
}
