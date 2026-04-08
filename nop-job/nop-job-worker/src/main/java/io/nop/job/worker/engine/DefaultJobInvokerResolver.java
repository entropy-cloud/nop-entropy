package io.nop.job.worker.engine;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;

import java.util.Map;

public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorRef = resolveExecutorRef(schedule, fire);
        if (executorRef == null || executorRef.isBlank() || !BeanContainer.isInitialized()) {
            return null;
        }

        Object bean = BeanContainer.tryGetBean(executorRef);
        if (!(bean instanceof IJobInvoker)) {
            bean = BeanContainer.tryGetBean("jobInvoker_" + executorRef);
        }
        return bean instanceof IJobInvoker ? (IJobInvoker) bean : null;
    }

    private String resolveExecutorRef(NopJobSchedule schedule, NopJobFire fire) {
        Map<String, Object> executorSnapshot = fire.getExecutorSnapshotComponent().get_jsonMap();
        Object executorRef = executorSnapshot == null ? null : executorSnapshot.get("executorRef");
        return executorRef instanceof String ? (String) executorRef : schedule.getExecutorRef();
    }
}
