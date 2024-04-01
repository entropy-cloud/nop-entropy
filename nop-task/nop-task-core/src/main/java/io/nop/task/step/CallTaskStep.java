/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class CallTaskStep extends AbstractTaskStep {
    private String taskName;

    private long taskVersion;

    private Set<String> inputNames;

    private Set<String> outputNames;

    public long getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(long taskVersion) {
        this.taskVersion = taskVersion;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Set<String> getInputNames() {
        return inputNames;
    }

    public void setInputNames(Set<String> inputNames) {
        this.inputNames = inputNames;
    }

    public Set<String> getOutputNames() {
        return outputNames;
    }

    public void setOutputNames(Set<String> outputNames) {
        this.outputNames = outputNames;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        ITaskRuntime taskRt = stepRt.getTaskRuntime();
        ITaskManager taskManager = taskRt.getTaskManager();

        String taskId = stepRt.getStateBean(String.class);
        ITask task;
        ITaskRuntime subRt;
        if (StringHelper.isEmpty(taskId)) {
            task = taskManager.getTask(taskName, taskVersion);
            subRt = taskRt.newChildRuntime(task, stepRt.isSupportPersist());

            stepRt.setStateBean(subRt.getTaskInstanceId());
            stepRt.saveState();
        } else {
            task = taskManager.getTask(taskName, taskVersion);
            subRt = taskManager.getTaskRuntime(taskId, taskRt.getSvcCtx());
        }

        IEvalScope scope = stepRt.getEvalScope();
        for (String name : inputNames) {
            subRt.getEvalScope().setLocalValue(name, scope.getValue(name));
        }

        return task.execute(subRt, outputNames);
    }
}