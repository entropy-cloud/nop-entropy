package io.nop.task.model;

import io.nop.api.core.util.ISourceLocationGetter;

import java.util.List;
import java.util.Set;

public interface IGraphTaskStepModel extends ISourceLocationGetter {

    boolean isGraphMode();

    String getName();

    Set<String> getEnterSteps();

    Set<String> getExitSteps();

    List<TaskStepModel> getSteps();

    TaskStepModel getStep(String stepName);

    boolean hasStep(String stepName);
}
