package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._CustomTaskStepModel;

public class CustomTaskStepModel extends _CustomTaskStepModel{
    public CustomTaskStepModel(){

    }

    public String getType(){
        return TaskConstants.STEP_TYPE_CUSTOM;
    }
}
