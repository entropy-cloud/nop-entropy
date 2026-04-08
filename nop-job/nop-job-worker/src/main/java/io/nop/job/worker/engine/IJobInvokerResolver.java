package io.nop.job.worker.engine;

import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;

public interface IJobInvokerResolver {
    IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire);
}
