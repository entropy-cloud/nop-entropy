/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.impl;

import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowCoordinator;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.IWorkflowEngine;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface IWorkflowImplementor extends IWorkflow {
    IWorkflowStore getStore();

    IWorkflowEngine getEngine();

    IWorkflowCoordinator getCoordinator();

    /**
     * 根据步骤定义id获取到最近一次步骤执行对应的实例对象
     */
    IWorkflowStepImplementor getLatestStepByName(String stepName);

    IWorkflowStepImplementor getStepById(String stepId);

    IWorkflowStepImplementor getStepByRecord(IWorkflowStepRecord stepRecord);

    List<? extends IWorkflowStepImplementor> getActivatedSteps();

    List<? extends IWorkflowStepImplementor> getWaitingSteps();

    List<? extends IWorkflowStepImplementor> getStepsByRecords(Collection<? extends IWorkflowStepRecord> stepRecords);

    void delayExecute(Runnable command);

    <T> T executeNow(Supplier<T> task);
}
