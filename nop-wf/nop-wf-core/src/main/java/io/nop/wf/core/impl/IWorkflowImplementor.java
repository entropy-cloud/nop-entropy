/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.impl;

import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.engine.IWorkflowEngine;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;

import java.util.Collection;
import java.util.List;

public interface IWorkflowImplementor extends IWorkflow {
    IWorkflowStore getStore();

    IWorkflowEngine getEngine();

    IWorkflowStepImplementor getStepById(String stepId);

    IWorkflowStepImplementor getStepByRecord(IWorkflowStepRecord stepRecord);

    List<? extends IWorkflowStepImplementor> getStepsByRecords(Collection<? extends IWorkflowStepRecord> stepRecords);

    void delayExecute(Runnable command);
}
