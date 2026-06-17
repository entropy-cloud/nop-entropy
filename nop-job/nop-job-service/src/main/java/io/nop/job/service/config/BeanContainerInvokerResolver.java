package io.nop.job.service.config;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobInvoker;
import jakarta.inject.Named;

import java.util.function.Function;

public class BeanContainerInvokerResolver implements Function<String, IJobInvoker> {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker apply(String invokerName) {
        String beanName = INVOKER_PREFIX + invokerName;
        return (IJobInvoker) BeanContainer.tryGetBean(beanName);
    }
}
