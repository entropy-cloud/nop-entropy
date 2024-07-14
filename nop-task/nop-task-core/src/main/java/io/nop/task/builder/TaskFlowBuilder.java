package io.nop.task.builder;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.impl.ITaskBeanContainerFactory;
import io.nop.task.impl.TaskImpl;
import io.nop.task.model.TaskFlowModel;

public class TaskFlowBuilder implements ITaskFlowBuilder {

    @Override
    public ITask buildTask(TaskFlowModel taskFlowModel) {
        TaskStepBuilder stepBuilder = new TaskStepBuilder();
        ITaskStep mainStep = stepBuilder.buildMainStep(taskFlowModel);

        IBeanContainerImplementor beanContainerTemplate = taskFlowModel.getBeanContainerTemplate(this::buildBeanContainerTemplate);

        ITaskBeanContainerFactory factory = beanContainerTemplate == null ? null :
                new BeanContainerFactory(taskFlowModel.isUseParentBeanContainer(), beanContainerTemplate);

        TaskImpl task = new TaskImpl(taskFlowModel.getName(), taskFlowModel.getVersion(), mainStep,
                taskFlowModel.isRecordMetrics(), taskFlowModel.getFlags(), factory,
                taskFlowModel.getInputs(), taskFlowModel.getOutputs());
        return task;
    }

    public IBeanContainerImplementor buildBeanContainerTemplate(TaskFlowModel taskFlowModel) {
        // 这里使用全局beanContainer作为编译时使用的parentContainer，用于校验对parentContainer中的bean名称的引用是否正确
        IBeanContainer parentContainer = taskFlowModel.isUseParentBeanContainer() ? BeanContainer.instance() : null;
        String taskName = taskFlowModel.getName();
        return new BeanContainerBuilder(parentContainer).addBeansModel(taskFlowModel.getBeans()).build(taskName + "-beans");
    }

    static class BeanContainerFactory implements ITaskBeanContainerFactory {
        private final boolean useParentBeanContainer;
        private final IBeanContainerImplementor beanContainerTemplate;

        public BeanContainerFactory(boolean useParentBeanContainer, IBeanContainerImplementor beanContainerTemplate) {
            this.useParentBeanContainer = useParentBeanContainer;
            this.beanContainerTemplate = beanContainerTemplate;
        }

        @Override
        public IBeanContainer createBeanContainer(ITaskRuntime taskRt) {
            IBeanContainer parentContainer = useParentBeanContainer ? getParentContainer(taskRt) : null;
            return beanContainerTemplate.buildNewInstance(parentContainer);
        }

        IBeanContainer getParentContainer(ITaskRuntime taskRt) {
            IBeanProvider provider = taskRt.getEvalScope().getBeanProvider();
            if (provider instanceof IBeanContainer)
                return (IBeanContainer) provider;
            return BeanContainer.instance();
        }
    }
}
