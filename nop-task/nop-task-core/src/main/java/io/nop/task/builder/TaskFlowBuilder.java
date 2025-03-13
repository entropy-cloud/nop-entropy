package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.TaskConstants;
import io.nop.task.impl.ITaskBeanContainerFactory;
import io.nop.task.impl.TaskImpl;
import io.nop.task.model.InvokeStaticTaskStepModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskImportModel;
import io.nop.xlang.expr.MethodRef;

import static io.nop.task.TaskErrors.ARG_ARG_COUNT;
import static io.nop.task.TaskErrors.ARG_CLASS_NAME;
import static io.nop.task.TaskErrors.ARG_METHOD_NAME;
import static io.nop.task.TaskErrors.ARG_METHOD_REF;
import static io.nop.task.TaskErrors.ERR_TASK_STATIC_METHOD_NOT_FOUND;
import static io.nop.task.TaskErrors.ERR_TASK_UNRESOLVED_METHOD_OWNER;
import static io.nop.task.builder.TaskFlowAnalyzer.forEachStep;

public class TaskFlowBuilder implements ITaskFlowBuilder {

    @Override
    public ITask buildTask(TaskFlowModel taskFlowModel) {
        resolve(taskFlowModel);

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

    private void resolve(TaskFlowModel taskFlowModel) {
        forEachStep(taskFlowModel, step -> {
            if (TaskConstants.STEP_TYPE_INVOKE_STATIC.equals(step.getType())) {
                InvokeStaticTaskStepModel stepModel = (InvokeStaticTaskStepModel) step;
                MethodRef methodRef = stepModel.getMethod();
                TaskImportModel importModel = taskFlowModel.getImport(methodRef.getOwnerName());
                if (importModel == null)
                    throw new NopException(ERR_TASK_UNRESOLVED_METHOD_OWNER)
                            .param(ARG_METHOD_REF, methodRef.toString());

                try {
                    IClassModel classModel = ReflectionManager.instance().loadClassModel(importModel.getClassName());
                    IFunctionModel func = classModel.getStaticMethod(methodRef.getMethodName(), step.getInputs().size());
                    if (func == null)
                        throw new NopException(ERR_TASK_STATIC_METHOD_NOT_FOUND)
                                .param(ARG_METHOD_REF, methodRef.toString())
                                .param(ARG_CLASS_NAME, importModel.getClassName())
                                .param(ARG_METHOD_NAME, methodRef.getMethodName())
                                .param(ARG_ARG_COUNT, step.getInputs().size());

                    stepModel.setResolvedMethod(func);
                } catch (NopException e) {
                    e.source(step);
                    throw e;
                }
            }
        });
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
