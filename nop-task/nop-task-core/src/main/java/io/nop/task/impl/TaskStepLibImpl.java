package io.nop.task.impl;

import io.nop.api.core.util.Guard;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepLib;
import io.nop.task.utils.TaskFlowModelHelper;

import java.util.Map;

public class TaskStepLibImpl implements ITaskStepLib {
    private final String libName;
    private final long libVersion;
    private final String libModelPath;

    private final Map<String, ITaskStep> steps;

    public TaskStepLibImpl(String libName, long libVersion, String libModelPath, Map<String, ITaskStep> steps) {
        if (libModelPath != null) {
            if (libName == null)
                libName = TaskFlowModelHelper.getLibName(libModelPath);
            if (libVersion <= 0)
                libVersion = TaskFlowModelHelper.getLibVersion(libModelPath);
        }
        Guard.checkArgument(libVersion >= 0, "taskVersion");
        this.libName = Guard.notEmpty(libName, "taskName");
        this.libVersion = libVersion;
        this.libModelPath = libModelPath;
        this.steps = Guard.notNull(steps, "steps");
    }

    public String getLibModelPath() {
        return libModelPath;
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
