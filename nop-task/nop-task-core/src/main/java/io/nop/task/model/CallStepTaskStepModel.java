package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._CallStepTaskStepModel;

public class CallStepTaskStepModel extends _CallStepTaskStepModel {
    public CallStepTaskStepModel() {

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_CALL_STEP;
    }
}
