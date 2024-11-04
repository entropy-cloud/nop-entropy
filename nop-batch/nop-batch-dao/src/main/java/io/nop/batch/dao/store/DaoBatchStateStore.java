package io.nop.batch.dao.store;

import io.nop.api.core.time.CoreMetrics;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;

public class DaoBatchStateStore implements IBatchStateStore {
    private final IDaoProvider daoProvider;
    private final ITransactionTemplate transactionTemplate;

    public DaoBatchStateStore(IDaoProvider daoProvider, ITransactionTemplate transactionTemplate) {
        this.daoProvider = daoProvider;
        this.transactionTemplate = transactionTemplate;
    }

    IEntityDao<NopBatchTask> taskDao() {
        return daoProvider.daoFor(NopBatchTask.class);
    }

    ITransactionTemplate txn() {
        return transactionTemplate;
    }

    @Override
    public void loadTaskState(IBatchTaskContext context) {
        String taskId = context.getTaskId();
        if (StringHelper.isEmpty(taskId))
            return;

        IEntityDao<NopBatchTask> taskDao = taskDao();
        NopBatchTask task = taskDao.requireEntityById(taskId);
        context.setTaskName(task.getTaskName());
        context.setTaskKey(task.getTaskKey());
        context.setCompletedIndex(task.getCompletedIndex());
        context.setCompleteItemCount(task.getCompleteCount());
        context.setSkipItemCount(task.getSkipCount());
        context.setProcessItemCount(task.getProcessCount());
        context.setRecoverMode(true);
    }

    @Override
    public void saveTaskState(IBatchTaskContext context) {
        String taskId = context.getTaskId();
        if (StringHelper.isEmpty(taskId)) {
            saveRecord(context);
        } else {
            updateRecord(context);
        }
    }

    void saveRecord(IBatchTaskContext context) {
        IEntityDao<NopBatchTask> taskDao = taskDao();
        NopBatchTask task = taskDao.newEntity();
        task.setTaskKey(context.getTaskKey());
        task.setTaskName(context.getTaskName());
        task.setCompletedIndex(-1L);
        task.setCompleteCount(0L);
        task.setProcessCount(0L);
        task.setStartTime(CoreMetrics.currentTimestamp());
        task.setSkipCount(0L);
        task.setRetryCount(0);
        taskDao.saveEntity(task);
    }

    void updateRecord(IBatchTaskContext context) {

    }
}
