package io.nop.task.impl;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.task.ITaskFlowManager;

public interface ITaskFlowManagerImplementor extends ITaskFlowManager {
    IScheduledExecutor getScheduledExecutor();
}
