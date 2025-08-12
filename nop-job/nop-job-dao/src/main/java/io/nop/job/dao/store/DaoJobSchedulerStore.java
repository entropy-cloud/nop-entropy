package io.nop.job.dao.store;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.job.api.IJobInstanceState;
import io.nop.job.api.IJobScheduleStore;
import io.nop.job.api.JobDetail;
import io.nop.job.core.NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobAssignment;
import io.nop.job.dao.entity.NopJobInstance;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.le;
import static io.nop.job.dao.entity._gen._NopJobDefinition.PROP_NAME_partitionIndex;
import static io.nop.job.dao.entity._gen._NopJobDefinition.PROP_NAME_status;
import static io.nop.job.dao.entity._gen._NopJobInstance.PROP_NAME_jobInstanceId;
import static io.nop.job.dao.entity._gen._NopJobInstance.PROP_NAME_scheduledExecTime;

public class DaoJobSchedulerStore implements IJobScheduleStore {
    private IDaoProvider daoProvider;

    private int scanIntervalSeconds = 10000;

    private boolean enableCluster;

    @InjectValue("@cfg:nop.job.scheduler.enable-cluster|false")
    public void setEnableCluster(boolean enableCluster) {
        this.enableCluster = enableCluster;
    }

    @InjectValue("@cfg:nop.job.scheduler.scan-interval-seconds|10000}")
    public void setScanIntervalSeconds(int scanIntervalSeconds) {
        this.scanIntervalSeconds = scanIntervalSeconds;
    }

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public boolean isEnableCluster() {
        return enableCluster;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopJobInstance> jobDao() {
        return daoProvider.daoFor(NopJobInstance.class);
    }

    @Override
    public ICancellable fetchPersistJobs(Consumer<JobDetail> processor) {
        Cancellable cancellable = new Cancellable();
        Future<?> future = getExecutor().scheduleWithFixedDelay(() -> doScanJobs(processor), 0,
                scanIntervalSeconds, TimeUnit.MILLISECONDS);
        cancellable.appendOnCancelTask(() -> future.cancel(false));
        return cancellable;
    }

    @SingleSession
    protected void doScanJobs(Consumer<JobDetail> processor) {
        IEntityDao<NopJobInstance> jobDao = jobDao();
        long now = jobDao.getDbEstimatedClock().getMinCurrentTimeMillis();

        QueryBean query = new QueryBean();
        query.addFilter(and(
                eq(PROP_NAME_status, NopJobCoreConstants.JOB_INSTANCE_STATUS_WAITING),
                le(PROP_NAME_scheduledExecTime, now + scanIntervalSeconds * 1000L)
        ));
        addPartitionFilter(query);
        query.addOrderField(PROP_NAME_scheduledExecTime, false);
        query.addOrderField(PROP_NAME_jobInstanceId, false);

        List<NopJobInstance> jobs = jobDao().findAllByQuery(query);
        jobDao.batchLoadProps(jobs, Arrays.asList(NopJobInstance.PROP_NAME_jobDefinition));
        for (NopJobInstance job : jobs) {
            processor.accept(JobDaoHelper.toJobDetail(job));
            job.setStatus(NopJobCoreConstants.JOB_INSTANCE_STATUS_SCHEDULED);
        }
    }

    protected void addPartitionFilter(QueryBean query) {
        if (isEnableCluster())
            return;

        IEntityDao<NopJobAssignment> assignmentDao = daoProvider.daoFor(NopJobAssignment.class);
        String hostId = AppConfig.hostId();
        NopJobAssignment assignment = assignmentDao.getEntityById(hostId);
        if (assignment != null) {
            IntRangeSet ranges = IntRangeSet.parse(assignment.getAssignment());
            query.addFilter(FilterBeans.inRanges(PROP_NAME_partitionIndex, ranges));
        } else {
            query.addFilter(FilterBeans.alwaysFalse());
        }
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }

    @Override
    public JobDetail loadJobDetail(String jobName) {
        return null;
    }

    @Override
    public void saveInstanceState(IJobInstanceState state) {

    }
}
