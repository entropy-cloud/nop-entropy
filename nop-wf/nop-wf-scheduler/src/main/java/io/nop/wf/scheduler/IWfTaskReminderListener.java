package io.nop.wf.scheduler;

import io.nop.wf.core.IWorkflowStep;

public interface IWfTaskReminderListener {
    void onRemind(IWorkflowStep step);
}
