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
            // 单个到期任务失败不中断整批：getDueAction与动作执行都纳入隔离
            // （getDueAction内部的实例加载可能因实例被并发删除而抛错）
            String dueAction = null;
            try {
                dueAction = getDueAction(stepRecord);
                if (dueAction == null || dueAction.isBlank()) {
                    LOG.debug("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={}",
                            stepRecord.getWfId(), stepRecord.getStepId());
                    continue;
                }

                WfActionRequestBean request = new WfActionRequestBean();
                request.setWfId(stepRecord.getWfId());
                request.setStepId(stepRecord.getStepId());
                request.setActionName(dueAction);

                FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
            } catch (NopException e) {
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode()
                        .equals(e.getErrorCode())) {
                    LOG.info("nop.wf.scheduler.skip-due-task-race:wfId={},stepId={}",
                            stepRecord.getWfId(), stepRecord.getStepId());
                    continue;
                }
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER.getErrorCode()
                        .equals(e.getErrorCode())) {
                    // wf-scheduler固定身份对普通user/dept/role步骤必然通不过allowCallByUser：
                    // 超时动作的触发主体是系统而非用户，该拒绝属预期形态，记录后继续处理后续到期任务
                    LOG.warn("nop.wf.scheduler.due-action-blocked-by-user-check:wfId={},stepId={},dueAction={}",
                            stepRecord.getWfId(), stepRecord.getStepId(), dueAction);
                    continue;
                }
                LOG.error("nop.wf.scheduler.due-action-failed:wfId={},stepId={},dueAction={}",
                        stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);
            } catch (Exception e) {
                // 非NopException的运行时异常同样只影响单条任务
                LOG.error("nop.wf.scheduler.due-action-failed:wfId={},stepId={},dueAction={}",
                        stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);
            }
        }
    }

    void scanRemindTasksInCurrentSession() {
        for (IWorkflowStepRecord stepRecord : workflowStore.findRemindActivatedSteps()) {
            try {
                IWorkflowStep step = workflowManager.getWorkflow(stepRecord.getWfId()).getStepById(stepRecord.getStepId());
                reminderListeners.forEach(listener -> listener.onRemind(step));

                Integer remindCount = stepRecord.getRemindCount();
                if (remindCount == null) {
                    remindCount = 0;
                }

                stepRecord.setRemindCount(remindCount + 1);
                stepRecord.setRemindTime(null);
                workflowStore.saveStepRecord(stepRecord);
            } catch (Exception e) {
                // 单条提醒失败（实例被删除、listener抛错）不中断整批；失败的记录不更新提醒计数，
                // 下一轮扫描可重试
                LOG.error("nop.wf.scheduler.remind-task-failed:wfId={},stepId={}",
                        stepRecord.getWfId(), stepRecord.getStepId(), e);
            }
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
