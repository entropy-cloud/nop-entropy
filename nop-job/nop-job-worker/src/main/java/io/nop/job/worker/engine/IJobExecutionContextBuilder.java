package io.nop.job.worker.engine;

import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;

public interface IJobExecutionContextBuilder {
    IJobExecutionContext buildContext(NopJobSchedule schedule, NopJobFire fire, NopJobTask task);

    JobTaskExecutionUpdate buildResultUpdate(NopJobTask task, JobFireResult result, Throwable err);
}
