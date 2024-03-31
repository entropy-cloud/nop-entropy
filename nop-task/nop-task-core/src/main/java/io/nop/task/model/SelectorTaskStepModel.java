package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._SelectorTaskStepModel;

public class SelectorTaskStepModel extends _SelectorTaskStepModel {
    public SelectorTaskStepModel() {

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_SELECTOR;
    }
}
