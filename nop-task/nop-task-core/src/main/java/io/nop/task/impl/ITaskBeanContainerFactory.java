package io.nop.task.impl;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.task.ITaskRuntime;

public interface ITaskBeanContainerFactory {
    IBeanContainer createBeanContainer(ITaskRuntime taskRt);
}
