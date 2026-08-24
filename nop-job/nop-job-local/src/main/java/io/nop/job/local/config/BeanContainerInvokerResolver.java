package io.nop.job.local.config;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobInvoker;

import java.util.function.Function;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_NOT_FOUND;

public class BeanContainerInvokerResolver implements Function<String, IJobInvoker> {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker apply(String invokerName) {
        String beanName = INVOKER_PREFIX + invokerName;
        Object bean = BeanContainer.tryGetBean(beanName);
        // check2 [P3-10]: bean 存在但类型不符时原实现直接强转抛无上下文的 ClassCastException。
        // 对齐 DefaultJobInvokerResolver：非 IJobInvoker 抛带 bean 名上下文的
        // ERR_JOB_INVOKER_NOT_FOUND；bean 不存在仍返回 null（由 LocalJobScheduler.addJob
        // 以 ERR_JOB_BEAN_NOT_FOUND 处理，保持既有契约）。
        if (bean != null && !(bean instanceof IJobInvoker)) {
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                    .param("beanName", beanName)
                    .param("invokerName", invokerName)
                    .param("actualType", bean.getClass().getName());
        }
        return (IJobInvoker) bean;
    }
}
