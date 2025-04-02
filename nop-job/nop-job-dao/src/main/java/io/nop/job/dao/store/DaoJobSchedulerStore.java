package io.nop.job.dao.store;

import io.nop.api.core.util.ICancellable;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.job.api.IJobScheduleStore;
import io.nop.job.api.ITriggerState;
import io.nop.job.api.JobDetail;
import io.nop.job.dao.entity.NopJobInstance;
import jakarta.inject.Inject;

import java.util.function.Consumer;

public class DaoJobSchedulerStore implements IJobScheduleStore {
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    IEntityDao<NopJobInstance> jobDao() {
        return daoProvider.daoFor(NopJobInstance.class);
    }

    @Override
    public ICancellable fetchPersistJobs(Consumer<JobDetail> processor) {
        return null;
    }

    @Override
    public ITriggerState loadTriggerState(String jobName) {
        return null;
    }

    @Override
    public JobDetail loadJobDetail(String jobName) {
        return null;
    }

    @Override
    public void saveTriggerState(ITriggerState state) {

    }
}
