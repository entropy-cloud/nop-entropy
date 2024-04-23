package io.nop.task.reflect;

import io.nop.api.core.annotations.task.GraphTaskStep;
import io.nop.api.core.annotations.task.TaskStep;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.TagsHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.XplTaskStepModel;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.exec.NullExecutable;

/**
 * 识别{@link io.nop.api.core.annotations.task.TaskStep}注解，动态生成TaskStep节点配置
 */
public class ReflectionTaskStepBuilder {
    public GraphTaskStepModel buildTaskStepGraph(IClassModel classModel, GraphTaskStep taskGraph, String beanName) {
        KeyedList<TaskStepModel> steps = new KeyedList<>(TaskStepModel::getName);
        for (IFunctionModel method : classModel.getMethods()) {
            TaskStep taskStep = method.getAnnotation(TaskStep.class);
            if (taskStep != null) {
                steps.add(buildTaskStep(method, taskStep, beanName));
            }
        }

        GraphTaskStepModel step = new GraphTaskStepModel();
        step.setEnterSteps(TagsHelper.toSet(taskGraph.enterSteps()));
        step.setExitSteps(TagsHelper.toSet(taskGraph.exitSteps()));
        step.setSteps(steps);
        return step;
    }

    public TaskStepModel buildTaskStep(IFunctionModel method, TaskStep taskStep, String beanName) {
        XplTaskStepModel step = new XplTaskStepModel();
        buildTaskStepBase(step, method, taskStep);
        StringBuilder code = new StringBuilder();
        code.append(" const bean = inject('").append(beanName).append("');\n");
        step.setSource(new EvalCode(NullExecutable.NULL, code.toString()));
        return step;
    }

    protected void buildTaskStepBase(TaskStepModel step, IFunctionModel method, TaskStep taskStep) {
        if (!taskStep.executor().isEmpty())
            step.setExecutor(taskStep.executor());

        if (taskStep.timeout() > 0)
            step.setTimeout(taskStep.timeout());

        if (!taskStep.next().isEmpty())
            step.setNext(taskStep.next());

        if (!taskStep.nextOnError().isEmpty())
            step.setNextOnError(taskStep.nextOnError());

        if (taskStep.waitSteps().length > 0)
            step.setWaitSteps(TagsHelper.toSet(taskStep.waitSteps()));

        if (taskStep.waitErrorSteps().length > 0)
            step.setWaitErrorSteps(TagsHelper.toSet(taskStep.waitErrorSteps()));

        if (!taskStep.when().isEmpty()) {
            step.setWhen(new EvalCode(NullExecutable.NULL, taskStep.when()));
        }

        if (taskStep.concurrent())
            step.setConcurrent(true);

        if (taskStep.tagSet().length > 0)
            step.setTagSet(TagsHelper.toSet(taskStep.tagSet()));

        String name = taskStep.name();
        if (name.isEmpty())
            name = method.getName();
        step.setName(name);

        if (taskStep.sync())
            step.setSync(true);

        if (taskStep.internal())
            step.setInternal(true);

        if (!taskStep.errorName().isEmpty())
            step.setErrorName(taskStep.errorName());

        if (taskStep.runOnContext())
            step.setRunOnContext(true);

        if (taskStep.recordMetrics())
            step.setRecordMetrics(true);

        if (taskStep.saveState())
            step.setSaveState(true);

        if (taskStep.useParentScope())
            step.setUseParentScope(true);
    }
}
