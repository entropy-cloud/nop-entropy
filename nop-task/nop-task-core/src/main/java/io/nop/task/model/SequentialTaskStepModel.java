package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._SequentialTaskStepModel;

public class SequentialTaskStepModel extends _SequentialTaskStepModel{
    public SequentialTaskStepModel(){

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_SEQUENTIAL;
    }
}
