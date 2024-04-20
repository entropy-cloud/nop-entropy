package io.nop.batch.dsl.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchTask;

public interface IBatchTaskFactory {
    IBatchTask newTask(IBeanProvider beanContainer);
}