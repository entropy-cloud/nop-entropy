package io.nop.task.impl;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;

public interface ITaskFlowManagerImplementor extends ITaskFlowManager {
    IScheduledExecutor getScheduledExecutor();

    IRateLimiter getRateLimiter(ITaskRuntime taskRt, String key, double requestPerSecond, boolean global);
}
