package io.nop.task.step;

import io.nop.api.core.util.SourceLocation;
import io.nop.task.ITaskStep;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;

import java.util.List;
import java.util.Set;

public abstract class DelegateTaskStep implements ITaskStep {
    private final ITaskStep taskStep;

    public DelegateTaskStep(ITaskStep taskStep) {
        this.taskStep = taskStep;
    }

    @Override
    public SourceLocation getLocation() {
        return taskStep.getLocation();
    }

    public ITaskStep getTaskStep() {
        return taskStep;
    }

    @Override
    public String getStepType() {
        return taskStep.getStepType();
    }

    @Override
    public Set<String> getPersistVars() {
        return taskStep.getPersistVars();
    }

    @Override
    public List<? extends ITaskInputModel> getInputs() {
        return taskStep.getInputs();
    }

    @Override
    public List<? extends ITaskOutputModel> getOutputs() {
        return taskStep.getOutputs();
    }

    @Override
    public boolean isConcurrent() {
        return taskStep.isConcurrent();
    }
}
