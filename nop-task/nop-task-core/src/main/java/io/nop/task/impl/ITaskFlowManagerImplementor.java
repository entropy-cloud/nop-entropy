package io.nop.task.impl;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;

public interface ITaskFlowManagerImplementor extends ITaskFlowManager {
    IScheduledExecutor getScheduledExecutor();

    IThreadPoolExecutor getThreadPoolExecutor(IBeanProvider beanProvider, String executorBean);

    IRateLimiter getRateLimiter(ITaskRuntime taskRt, String key, double requestPerSecond, boolean global);
}
