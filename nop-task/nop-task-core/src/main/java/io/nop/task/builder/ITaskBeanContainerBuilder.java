package io.nop.task.builder;

import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.task.model.TaskFlowModel;

public interface ITaskBeanContainerBuilder {
    IBeanContainerImplementor buildBeanContainer(TaskFlowModel taskFlowModel);
}
