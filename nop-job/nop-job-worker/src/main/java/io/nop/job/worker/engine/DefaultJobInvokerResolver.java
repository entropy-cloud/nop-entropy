package io.nop.job.worker.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_EXECUTOR_KIND_EMPTY;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_NOT_FOUND;

public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorKind = resolveExecutorKind(schedule, fire);
        if (executorKind == null || executorKind.isBlank()) {
            throw new NopException(ERR_JOB_EXECUTOR_KIND_EMPTY)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }

        String beanName = INVOKER_PREFIX + executorKind;
        Object bean = BeanContainer.tryGetBean(beanName);
        if (!(bean instanceof IJobInvoker)) {
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                    .param("executorKind", executorKind)
                    .param("beanName", beanName)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }
        return (IJobInvoker) bean;
    }

    private String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
        String kind = fire.getExecutorKind();
        return kind != null ? kind : schedule.getExecutorKind();
    }
}
