package io.nop.task.step;

import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

public class BeanTaskStep extends AbstractTaskStep {
    private String beanName;

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        ITaskStep step = (ITaskStep) stepRt.getBean(beanName);
        return step.execute(stepRt);
    }
}
