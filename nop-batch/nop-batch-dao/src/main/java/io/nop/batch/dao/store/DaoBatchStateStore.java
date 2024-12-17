package io.nop.batch.dao.store;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancellable;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.exceptions.BatchCancelException;
import io.nop.batch.dao.NopBatchDaoConstants;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.AbstractDaoHandler;

import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_ID;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_KEY;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_NAME;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_STATUS;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_EXCEED_START_LIMIT;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskKey;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskName;

public class DaoBatchStateStore extends AbstractDaoHandler implements IBatchStateStore {
    protected IEntityDao<NopBatchTask> taskDao() {
        return daoFor(NopBatchTask.class);
    }

    @Override
    public void loadTaskState(IBatchTaskContext context) {
        runLocal(session -> {
            loadTaskState0(context);
            return null;
        });
    }

    private void loadTaskState0(IBatchTaskContext context) {
        IEntityDao<NopBatchTask> taskDao = taskDao();
        NopBatchTask task = loadExistingTask(taskDao, context);
        if (task == null) {
            task = newTask(taskDao);
            task.setTaskKey(context.getTaskKey());
            task.setTaskName(context.getTaskName());
            task.setFlowId(context.getFlowId());
            task.setFlowStepId(context.getFlowStepId());
            setTaskRecord(context, task);
            saveTask(taskDao, task);
            context.setTaskId(task.getSid());
            return;
        }

        if (task.getTaskStatus() == NopBatchDaoConstants.TASK_STATUS_KILLED)
            throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED)
                    .param(ARG_TASK_NAME, task.getTaskName())
                    .param(ARG_TASK_KEY, task.getTaskKey())
                    .param(ARG_TASK_ID, task.getSid())
                    .param(ARG_TASK_STATUS, task.getTaskStatus());

        if (context.getStartLimit() > 0 && task.getExecCount() >= context.getStartLimit())
            throw new NopException(ERR_BATCH_TASK_EXCEED_START_LIMIT)
                    .param(ARG_TASK_NAME, task.getTaskName())
                    .param(ARG_TASK_KEY, task.getTaskKey())
                    .param(ARG_TASK_ID, task.getSid())
                    .param(ARG_TASK_STATUS, task.getTaskStatus());

        if (task.getTaskStatus() <= NopBatchDaoConstants.TASK_STATUS_RUNNING) {
            throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE)
                    .param(ARG_TASK_NAME, task.getTaskName())
                    .param(ARG_TASK_KEY, task.getTaskKey())
                    .param(ARG_TASK_ID, task.getSid())
                    .param(ARG_TASK_STATUS, task.getTaskStatus());
        }

        if (!Boolean.TRUE.equals(context.getAllowStartIfComplete()) && task.getTaskStatus() == NopBatchDaoConstants.TASK_STATUS_COMPLETED) {
            throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED)
                    .param(ARG_TASK_NAME, task.getTaskName())
                    .param(ARG_TASK_KEY, task.getTaskKey())
                    .param(ARG_TASK_ID, task.getSid())
                    .param(ARG_TASK_STATUS, task.getTaskStatus());
        }

        task.setTaskStatus(NopBatchDaoConstants.TASK_STATUS_RUNNING);
        task.setRestartTime(CoreMetrics.currentTimestamp());
        task.setResultMsg(null);
        task.setResultStatus(null);
        task.setResultCode(null);
        task.incExecCount();
        task.setWorkerId(AppConfig.hostId());

        if (context.getFlowId() != null) {
            task.setFlowId(context.getFlowId());
        }

        if (context.getFlowStepId() != null) {
            task.setFlowStepId(context.getFlowStepId());
        }

        taskDao.updateEntityDirectly(task);

        context.setTaskName(task.getTaskName());
        context.setTaskKey(task.getTaskKey());
        context.setTaskId(task.getSid());
        context.setCompletedIndex(task.getCompletedIndex());
        context.setCompleteItemCount(task.getCompleteItemCount());
        context.setSkipItemCount(task.getSkipItemCount());
        context.setProcessItemCount(task.getProcessItemCount());
        context.setRecoverMode(true);
        context.setFlowId(task.getFlowId());
        context.setFlowStepId(task.getFlowStepId());
        setTaskRecord(context, task);
    }

    void setTaskRecord(IBatchTaskContext context, NopBatchTask task) {
        context.setAttribute(NopBatchTask.class.getSimpleName(), task);
    }

    NopBatchTask getTaskRecord(IBatchTaskContext context) {
        return (NopBatchTask) context.getAttribute(NopBatchTask.class.getSimpleName());
    }

    protected NopBatchTask loadExistingTask(IEntityDao<NopBatchTask> dao, IBatchTaskContext context) {
        String taskId = context.getTaskId();
        if (!StringHelper.isEmpty(taskId))
            return dao.requireEntityById(taskId);

        if (!StringHelper.isEmpty(context.getTaskKey())) {
            String taskName = context.getTaskName();
            String taskKey = context.getTaskKey();

            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(PROP_NAME_taskName, taskName));
            query.addFilter(FilterBeans.eq(PROP_NAME_taskKey, taskKey));
            query.addOrderField(NopBatchTask.PROP_NAME_execCount, true);

            return dao.findFirstByQuery(query);
        }
        return null;
    }

    protected void saveTask(IEntityDao<NopBatchTask> dao, NopBatchTask task) {
        dao.saveEntityDirectly(task);
    }

    protected void updateTask(IEntityDao<NopBatchTask> dao, NopBatchTask task) {
        // task此时有可能在session之外
        dao.updateEntityDirectly(task);
    }

    @Override
    public synchronized void saveTaskState(boolean complete, Throwable err, IBatchTaskContext context) {
        NopBatchTask task = getTaskRecord(context);
        task.setCompleteItemCount(context.getCompleteItemCount());
        task.setSkipItemCount(context.getSkipItemCount());
        task.setCompletedIndex(context.getCompletedIndex());
        task.setProcessItemCount(context.getProcessItemCount());

        if (err != null) {
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, err);
            task.setResultCode(errorBean.getErrorCode());
            task.setResultStatus(errorBean.getStatus());
            task.setResultMsg(errorBean.getDescription());
        }

        if (complete) {
            int taskStatus = getTaskStatus(err, context);
            task.setTaskStatus(taskStatus);
            task.setEndTime(CoreMetrics.currentTimestamp());
        }
        IEntityDao<NopBatchTask> taskDao = taskDao();
        updateTask(taskDao, task);
    }

    int getTaskStatus(Throwable err, IBatchTaskContext context) {
        if (err != null) {
            if (err instanceof BatchCancelException) {
                // 暂时挂起执行
                if (ICancellable.CANCEL_REASON_SUSPEND.equals(context.getCancelReason()))
                    return NopBatchDaoConstants.TASK_STATUS_SUSPENDED;
                // 主动取消执行
                if (ICancellable.CANCEL_REASON_SKIP.equals(context.getCancelReason()))
                    return NopBatchDaoConstants.TASK_STATUS_CANCELLED;
                return NopBatchDaoConstants.TASK_STATUS_KILLED;
            }

            // 执行失败
            return NopBatchDaoConstants.TASK_STATUS_FAILED;
        }
        // 即使成功完成，也可能会跳过部分执行条目，导致skipCount不为0
        return NopBatchDaoConstants.TASK_STATUS_COMPLETED;
    }

    protected NopBatchTask newTask(IEntityDao<NopBatchTask> taskDao) {
        NopBatchTask task = taskDao.newEntity();
        task.setStartTime(CoreMetrics.currentTimestamp());
        task.setExecCount(1);
        task.setTaskStatus(NopBatchDaoConstants.TASK_STATUS_RUNNING);
        task.setCompletedIndex(-1L);
        task.setCompleteItemCount(0L);
        task.setProcessItemCount(0L);
        task.setSkipItemCount(0L);
        task.setWriteItemCount(0L);
        task.setRetryItemCount(0);
        task.setLoadRetryCount(0);
        task.setLoadSkipCount(0L);
        task.setWorkerId(AppConfig.hostId());
        return task;
    }
}
