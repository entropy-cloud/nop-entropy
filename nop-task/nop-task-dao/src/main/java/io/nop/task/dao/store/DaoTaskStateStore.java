package io.nop.task.dao.store;

import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.dao.entity.NopTaskInstance;
import jakarta.inject.Inject;

public class DaoTaskStateStore implements ITaskStateStore {

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopTaskInstance> dao() {
        return daoProvider.daoFor(NopTaskInstance.class);
    }

    @Override
    public boolean isSupportPersist() {
        return true;
    }

    @Override
    public ITaskStepState newMainStepState(ITaskState taskState) {
        return null;
    }

    @Override
    public ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {

    }

    @Override
    public ITaskState newTaskState(String taskName, long taskVersion, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public ITaskState loadTaskState(String taskInstanceId, ITaskRuntime taskRt) {
        return null;
    }

    @Override
    public void saveTaskState(ITaskRuntime taskRt) {

    }
}
