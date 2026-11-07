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
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;

import java.util.Set;

public class CallTaskStep extends AbstractTaskStep {
    private String taskName;

    private long taskVersion;

    private String taskModelPath;

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

    public String getTaskModelPath() {
        return taskModelPath;
    }

    public void setTaskModelPath(String taskModelPath) {
        this.taskModelPath = taskModelPath;
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
        ITaskFlowManager taskManager = taskRt.getTaskManager();

        String taskId = stepRt.getStateBean(String.class);
        ITask task;
        ITaskRuntime subRt;
        if (StringHelper.isEmpty(taskId)) {
            task = getTask(taskManager);
            subRt = taskRt.newChildRuntime(task, stepRt.isSupportPersist());

            stepRt.setStateBean(subRt.getTaskInstanceId());
            stepRt.saveState();
        } else {
            task = getTask(taskManager);
            subRt = taskManager.getTaskRuntime(taskId, taskRt.getSvcCtx());
        }

        IEvalScope scope = stepRt.getEvalScope();
        for (String name : inputNames) {
            subRt.getEvalScope().setLocalValue(name, scope.getValue(name));
        }

        // W-04（CRA 8.48 R4-08，MA-11 裁定 A）：跨任务链 global throttle 持有键传播——子任务经
        // newChildRuntime 产生独立 runtime（无父引用），重入检测以 inherited-keys 显式传递
        // （taskName 限定键，与注册表键形态一致；resume 重建的子任务 runtime 同样回填）。
        // 仅 global 语义键参与（非 global 信号量为 taskRt attribute 私有，跨任务不共享池）
        subRt.setAttribute(TaskStepHelper.ATTR_THROTTLE_INHERITED_KEYS,
                TaskStepHelper.snapshotInheritedThrottleKeys(stepRt));

        return task.execute(subRt, outputNames);
    }

    ITask getTask(ITaskFlowManager taskFlowManager) {
        if (taskModelPath != null)
            return taskFlowManager.loadTaskFromPath(taskModelPath);
        return taskFlowManager.getTask(taskName, taskVersion);
    }
}