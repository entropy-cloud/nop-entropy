/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.task.ITaskManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepLib;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepResult;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.task.TaskErrors.ARG_LIB_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_STEP_IN_LIB;

public class CallStepTaskStep extends AbstractTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(CallStepTaskStep.class);
    private String libName;

    private long libVersion;

    private String stepName;

    public long getLibVersion() {
        return libVersion;
    }

    public void setLibVersion(long libVersion) {
        this.libVersion = libVersion;
    }

    public String getLibName() {
        return libName;
    }

    public void setLibName(String libName) {
        this.libName = libName;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {
        ITaskManager taskManager = stepRt.getTaskRuntime().getTaskManager();
        ITaskStepLib lib = taskManager.getTaskStepLib(libName, libVersion);
        ITaskStep step = lib.getStep(stepName);
        if (step == null) {
            throw TaskStepHelper.newError(getLocation(), stepRt, ERR_TASK_UNKNOWN_STEP_IN_LIB)
                    .param(ARG_LIB_NAME, libName).param(ARG_STEP_NAME, stepName);
        }

        ITaskRuntime taskRt = stepRt.getTaskRuntime();
        LOG.debug("nop.task.step.run:taskName={},taskInstanceId={},stepId={},runId={},loc={}",
                taskRt.getTaskName(), taskRt.getTaskInstanceId(),
                stepRt.getStepId(), stepRt.getRunId(), step.getLocation());

        return step.execute(stepRt);
    }
}