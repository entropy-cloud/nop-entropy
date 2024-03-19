package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._CallTaskStepModel;

public class CallTaskStepModel extends _CallTaskStepModel{
    public CallTaskStepModel(){

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_CALL_TASK;
    }
}
