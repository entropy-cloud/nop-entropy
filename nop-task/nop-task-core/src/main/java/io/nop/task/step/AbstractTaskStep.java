/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.SourceLocation;
import io.nop.task.ITaskStep;
import io.nop.task.model.ITaskInputModel;
import io.nop.task.model.ITaskOutputModel;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AbstractTaskStep implements ITaskStep {
    private SourceLocation location;
    private String stepType;

    private Set<String> persistVars;

    private boolean concurrent;

    private List<? extends ITaskInputModel> inputs = Collections.emptyList();

    private List<? extends ITaskOutputModel> outputs = Collections.emptyList();


    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @Override
    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    @Override
    public boolean isConcurrent() {
        return concurrent;
    }

    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    @Override
    public Set<String> getPersistVars() {
        return persistVars;
    }

    public void setPersistVars(Set<String> persistVars) {
        this.persistVars = persistVars;
    }

    @Override
    public List<? extends ITaskInputModel> getInputs() {
        return inputs;
    }

    public void setInputs(List<? extends ITaskInputModel> inputs) {
        this.inputs = inputs;
    }

    @Override
    public List<? extends ITaskOutputModel> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<? extends ITaskOutputModel> outputs) {
        this.outputs = outputs;
    }
}