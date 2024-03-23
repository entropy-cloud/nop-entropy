package io.nop.task.step;

import io.nop.api.core.util.Guard;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;

public class BeanTaskStepResultAggregator implements ITaskStepResultAggregator {
    private final String bean;

    public BeanTaskStepResultAggregator(String bean) {
        this.bean = Guard.notEmpty(bean, "bean");
    }

    @Override
    public TaskStepResult aggregate(MultiStepResultBean results, ITaskStepRuntime stepRt) {
        ITaskStepResultAggregator aggregator = (ITaskStepResultAggregator) stepRt.getBean(bean);
        return aggregator.aggregate(results, stepRt);
    }
}
