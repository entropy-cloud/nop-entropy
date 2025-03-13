package io.nop.task.model;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.task.TaskConstants;
import io.nop.task.model._gen._InvokeStaticTaskStepModel;

public class InvokeStaticTaskStepModel extends _InvokeStaticTaskStepModel {
    private IEvalFunction resolvedMethod;

    public InvokeStaticTaskStepModel() {

    }

    public IEvalFunction getResolvedMethod() {
        return resolvedMethod;
    }

    public void setResolvedMethod(IEvalFunction resolvedMethod) {
        this.resolvedMethod = resolvedMethod;
    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_INVOKE_STATIC;
    }
}
