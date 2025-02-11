/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.scheduler;

import io.nop.job.api.JobDetail;
import io.nop.job.api.TriggerState;
import io.nop.job.api.TriggerStatus;
import io.nop.job.core.ITriggerAction;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerExecution;
import io.nop.job.core.ITriggerExecutor;

class JobExecution {
    private ResolvedJobSpec jobSpec;
    private ITriggerContext triggerContext;

    private ITriggerExecution triggerExecution;
    private boolean closed;

    public JobDetail toJobDetail() {
        JobDetail detail = new JobDetail();
        detail.setTriggerState(new TriggerState(triggerContext));
        detail.setJobSpec(jobSpec.getJobSpec());
        return detail;
    }

    public boolean isClosed() {
        return closed;
    }

    public long getJobVersion() {
        return jobSpec.getJobSpec().getVersion();
    }

    public ITriggerAction createTriggerAction() {
        ResolvedJobSpec resolved = this.jobSpec;
        return (state, cancelToken) -> resolved.getJobInvoker().invokeAsync(resolved.getJobName(), resolved.getJobParams(),
                state, cancelToken);
    }

    public boolean startTrigger(ITriggerExecutor executor, Runnable onComplete) {
        if (getTriggerStatus().isDone())
            return false;

        ResolvedJobSpec jobSpec = this.jobSpec;
        ITriggerExecution execution = executor.execute(false, jobSpec.getTrigger(), createTriggerAction(),
                getTriggerContext());
        this.triggerExecution = execution;

        execution.getFinishPromise().whenComplete((ret, err) -> {
            clearTriggerExecution(execution);
            onComplete.run();
        });
        return true;
    }

    public void fireNow(ITriggerExecutor executor) {
        ITriggerExecution trigger = this.triggerExecution;
        if (trigger != null) {
            trigger.fireNow();
            return;
        }

        ResolvedJobSpec jobSpec = this.jobSpec;

        ITriggerExecution execution = executor.execute(true, jobSpec.getTrigger(), createTriggerAction(),
                getTriggerContext());
        this.triggerExecution = execution;

        execution.getFinishPromise().whenComplete((ret, err) -> {
            clearTriggerExecution(execution);
        });
    }

    synchronized void clearTriggerExecution(ITriggerExecution execution) {
        if (this.triggerExecution == execution) {
            this.triggerExecution = null;
        }
    }

    public void pauseTrigger() {
        ITriggerExecution execution = triggerExecution;
        if (execution != null) {
            execution.pause();
        }
    }

    public void deactivate() {
        closed = true;
        ITriggerExecution execution = triggerExecution;
        if (execution != null) {
            execution.deactivate();
        }
    }

    public void cancelTrigger() {
        ITriggerExecution execution = triggerExecution;
        if (execution != null) {
            execution.cancel();
        }
    }

    public TriggerStatus getTriggerStatus() {
        return triggerContext.getTriggerStatus();
    }

    public ResolvedJobSpec getJobSpec() {
        return jobSpec;
    }

    public void setJobSpec(ResolvedJobSpec jobSpec) {
        this.jobSpec = jobSpec;
    }

    public ITriggerExecution getTriggerExecution() {
        return triggerExecution;
    }

    public void setTriggerExecution(ITriggerExecution triggerExecution) {
        this.triggerExecution = triggerExecution;
    }

    public ITriggerContext getTriggerContext() {
        return triggerContext;
    }

    public void setTriggerContext(ITriggerContext triggerContext) {
        this.triggerContext = triggerContext;
    }

}
