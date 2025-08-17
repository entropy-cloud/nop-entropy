package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._IfTaskStepModel;

public class IfTaskStepModel extends _IfTaskStepModel{
    public IfTaskStepModel(){

    }

    @Override
    public String getType(){
        return TaskConstants.STEP_TYPE_IF;
    }
}
