/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.scheduler;

import io.nop.job.api.JobDetail;
import io.nop.job.api.JobInstanceState;
import io.nop.job.core.ITriggerAction;
import io.nop.job.core.ITriggerContext;
import io.nop.job.core.ITriggerExecution;
import io.nop.job.core.ITriggerExecutor;

class JobExecution {
    private ResolvedJobSpec jobSpec;
    private ITriggerContext triggerContext;

    private ITriggerExecution triggerExecution;

    private ITriggerExecution fireNowExecution;

    private boolean scheduledTrigger;

    private boolean closed;

    public JobDetail toJobDetail() {
        JobDetail detail = new JobDetail();
        detail.setInstanceState(new JobInstanceState(triggerContext));
        detail.setJobSpec(jobSpec.getJobSpec());
        return detail;
    }

    public String getJobName() {
        return jobSpec.getJobName();
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isJobFinished() {
        return triggerContext.isJobFinished();
    }

    public boolean isInstanceRunning() {
        return triggerContext.isInstanceRunning();
    }

    public long getJobVersion() {
        return jobSpec.getJobSpec().getJobVersion();
    }

    public ITriggerAction createTriggerAction() {
        ResolvedJobSpec resolved = this.jobSpec;
        return (forceFire, state, cancelToken) ->
                resolved.getJobInvoker().invokeAsync(triggerContext);
    }

    public void startTrigger(ITriggerExecutor executor, Runnable onComplete) {
        if (getTriggerContext().isJobFinished() || isClosed())
            return;

        if (this.triggerExecution != null)
            return;

        if (fireNowExecution != null) {
            scheduledTrigger = true;
            return;
        }

        scheduledTrigger = false;
        triggerContext.setScheduleEnabled(true);

        ResolvedJobSpec jobSpec = this.jobSpec;
        ITriggerExecution execution = executor.execute(jobSpec.getTrigger(), createTriggerAction(),
                getTriggerContext());
        this.triggerExecution = execution;

        execution.getFinishPromise().whenComplete((ret, err) -> {
            synchronized (getTriggerContext()) {
                clearTriggerExecution(execution);
                onComplete.run();
            }
        });
    }

    public void fireNow(ITriggerExecutor executor, Runnable onComplete) {
        ITriggerExecution trigger = this.triggerExecution;
        if (trigger != null) {
            return;
        }

        ITriggerExecution execution = executor.fireNow(createTriggerAction(), getTriggerContext());
        this.fireNowExecution = execution;

        execution.getFinishPromise().whenComplete((ret, err) -> {
            synchronized (getTriggerContext()) {
                clearFireNowExecution(execution, executor, onComplete);
                onComplete.run();
            }
        });
    }

    void clearFireNowExecution(ITriggerExecution execution, ITriggerExecutor executor, Runnable onComplete) {
        if (this.fireNowExecution == execution)
            this.fireNowExecution = null;

        if (scheduledTrigger)
            startTrigger(executor, onComplete);
    }

    void clearTriggerExecution(ITriggerExecution execution) {
        if (this.triggerExecution == execution) {
            this.triggerExecution = null;
        }
    }

    public void pauseTrigger() {
        scheduledTrigger = false;
        ITriggerExecution execution = triggerExecution;
        if (execution != null) {
            execution.suspend();
        } else {

        }
    }

    public void deactivate() {
        closed = true;
        scheduledTrigger = false;

        triggerContext.setScheduleEnabled(false);
    }

    public void cancelTrigger() {
        scheduledTrigger = false;

        ITriggerExecution execution = triggerExecution;
        if (execution != null) {
            execution.cancel();
        }

        ITriggerExecution fireNowExecution = this.fireNowExecution;
        if (fireNowExecution != null) {
            fireNowExecution.cancel();
        }
    }

    public int getTriggerStatus() {
        return triggerContext.getInstanceStatus();
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
