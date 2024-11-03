package io.nop.batch.dao.store;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

public class DaoBatchStateStore implements IBatchStateStore {
    private final IDaoProvider daoProvider;

    public DaoBatchStateStore(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    IEntityDao<NopBatchTask> taskDao() {
        return daoProvider.daoFor(NopBatchTask.class);
    }

    @Override
    public void loadTaskState(IBatchTaskContext context) {
        String taskId = context.getTaskId();
        if(StringHelper.isEmpty(taskId))
            return;

        IEntityDao<NopBatchTask> taskDao = taskDao();
        NopBatchTask task = taskDao.requireEntityById(taskId);
        context.setCompletedIndex(task.getCompletedIndex());

    }

    @Override
    public void saveTaskState(IBatchTaskContext context) {

    }

    @Override
    public void loadChunkState(IBatchChunkContext context) {

    }
}
