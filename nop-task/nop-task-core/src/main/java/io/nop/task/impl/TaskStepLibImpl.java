package io.nop.task.impl;

import io.nop.api.core.util.Guard;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepLib;

import java.util.Map;

public class TaskStepLibImpl implements ITaskStepLib {
    private final String libName;
    private final long libVersion;

    private final Map<String, ITaskStep> steps;

    public TaskStepLibImpl(String libName, long libVersion, Map<String, ITaskStep> steps) {
        Guard.checkArgument(libVersion > 0, "taskVersion");
        this.libName = Guard.notEmpty(libName, "taskName");
        this.libVersion = libVersion;
        this.steps = Guard.notNull(steps, "steps");
    }

    @Override
    public String getLibName() {
        return libName;
    }

    @Override
    public long getLibVersion() {
        return libVersion;
    }

    @Override
    public ITaskStep getStep(String stepName) {
        return steps.get(stepName);
    }
}
