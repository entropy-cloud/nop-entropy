package io.nop.task.impl;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.task.ITaskManager;

public interface ITaskManagerImplementor extends ITaskManager {
    IScheduledExecutor getScheduledExecutor();
}
