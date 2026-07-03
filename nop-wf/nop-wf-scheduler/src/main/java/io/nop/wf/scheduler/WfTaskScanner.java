package io.nop.wf.scheduler;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.service.WorkflowServiceSpi;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

public class WfTaskScanner {
    static final Logger LOG = LoggerFactory.getLogger(WfTaskScanner.class);

    private IWorkflowStore workflowStore;
    private IWorkflowManager workflowManager;
    private WorkflowServiceSpi workflowService;
    private IOrmTemplate ormTemplate;
    private List<IWfTaskReminderListener> reminderListeners = Collections.emptyList();

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @Inject
    public void setWorkflowService(WorkflowServiceSpi workflowService) {
        this.workflowService = workflowService;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setReminderListeners(List<IWfTaskReminderListener> reminderListeners) {
        if (reminderListeners != null) {
            this.reminderListeners = reminderListeners;
        }
    }

    public void scanDueTasks() {
        ormTemplate.runInNewSession(session -> {
            scanDueTasksInCurrentSession();
            return null;
        });
    }

    public void scanRemindTasks() {
        ormTemplate.runInNewSession(session -> {
            scanRemindTasksInCurrentSession();
            return null;
        });
    }

    void scanDueTasksInCurrentSession() {
        for (IWorkflowStepRecord stepRecord : workflowStore.findDueActivatedSteps()) {
            String dueAction = getDueAction(stepRecord);
            if (dueAction == null || dueAction.isBlank()) {
                LOG.debug("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={}",
                        stepRecord.getWfId(), stepRecord.getStepId());
                continue;
            }

            WfActionRequestBean request = new WfActionRequestBean();
            request.setWfId(stepRecord.getWfId());
            request.setStepId(stepRecord.getStepId());
            request.setActionName(dueAction);

            try {
                FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
            } catch (NopException e) {
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode()
                        .equals(e.getErrorCode())) {
                    LOG.info("nop.wf.scheduler.skip-due-task-race:wfId={},stepId={}",
                            stepRecord.getWfId(), stepRecord.getStepId());
                    continue;
                }
                throw e;
            }
        }
    }

    void scanRemindTasksInCurrentSession() {
        for (IWorkflowStepRecord stepRecord : workflowStore.findRemindActivatedSteps()) {
            IWorkflowStep step = workflowManager.getWorkflow(stepRecord.getWfId()).getStepById(stepRecord.getStepId());
            reminderListeners.forEach(listener -> listener.onRemind(step));

            Integer remindCount = stepRecord.getRemindCount();
            if (remindCount == null) {
                remindCount = 0;
            }

            stepRecord.setRemindCount(remindCount + 1);
            stepRecord.setRemindTime(null);
            workflowStore.saveStepRecord(stepRecord);
        }
    }

    private String getDueAction(IWorkflowStepRecord stepRecord) {
        IWorkflowModel workflowModel = workflowManager.getWorkflow(stepRecord.getWfId()).getModel();
        IWorkflowStepModel stepModel = workflowModel.getStep(stepRecord.getStepName());
        return stepModel == null ? null : stepModel.getDueAction();
    }

    private IServiceContext newSchedulerContext() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.getContext().setUserId("wf-scheduler");
        return context;
    }
}
