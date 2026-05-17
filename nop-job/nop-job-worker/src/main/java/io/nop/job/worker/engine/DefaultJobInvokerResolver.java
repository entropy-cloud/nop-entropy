package io.nop.job.worker.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;

import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_EXECUTOR_REF_EMPTY;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_NOT_FOUND;

public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorRef = resolveExecutorRef(schedule, fire);
        if (executorRef == null || executorRef.isBlank()) {
            throw new NopException(ERR_JOB_EXECUTOR_REF_EMPTY)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }

        String beanName = INVOKER_PREFIX + executorRef;
        Object bean = BeanContainer.tryGetBean(beanName);
        if (!(bean instanceof IJobInvoker)) {
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                    .param("executorRef", executorRef)
                    .param("beanName", beanName)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }
        return (IJobInvoker) bean;
    }

    private String resolveExecutorRef(NopJobSchedule schedule, NopJobFire fire) {
        Map<String, Object> executorSnapshot = fire.getExecutorSnapshotComponent().get_jsonMap();
        Object executorRef = executorSnapshot == null ? null : executorSnapshot.get("executorRef");
        return executorRef instanceof String ? (String) executorRef : schedule.getExecutorRef();
    }
}
