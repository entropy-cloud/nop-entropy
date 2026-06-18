package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.mapper.NopJobTaskMapper;
import io.nop.job.dao.mapper.ReservedCostRow;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_createTime;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_jobFireId;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_jobTaskId;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_partitionIndex;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_priority;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_startTime;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_taskNo;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_taskStatus;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_workerInstanceId;

public class JobTaskStoreImpl implements IJobTaskStore {

    private IDaoProvider daoProvider;

    private NopJobTaskMapper taskMapper;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setTaskMapper(NopJobTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public boolean updateTask(NopJobTask task) {
        return !taskDao().tryUpdateManyWithVersionCheck(java.util.Collections.singletonList(task)).isEmpty();
    }

    @Override
    public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) {
        return fetchWaitingTasks(limit, partitions, null, false);
    }

    @Override
    public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                              String workerInstanceId, boolean enforceAttribution) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, _NopJobCoreConstants.TASK_STATUS_WAITING));
        if (enforceAttribution && workerInstanceId != null) {
            query.addFilter(FilterBeans.or(
                    FilterBeans.eq(PROP_NAME_workerInstanceId, workerInstanceId),
                    FilterBeans.isNull(PROP_NAME_workerInstanceId)
            ));
        }
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_priority, true);
        query.addOrderField(PROP_NAME_createTime, false);
        query.addOrderField(PROP_NAME_jobTaskId, false);
        return taskDao().findAllByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        java.sql.Timestamp lockTime = new java.sql.Timestamp(
                taskDao().getDbEstimatedClock().getMaxCurrentTimeMillis() + Math.max(lockTimeoutMs, 1L));
        for (NopJobTask task : tasks) {
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CLAIMED);
            task.setWorkerInstanceId(workerInstanceId);
            task.setUpdatedBy("system");
            task.setUpdateTime(lockTime);
        }
        return taskDao().tryUpdateManyWithVersionCheck(tasks);
    }

    @Override
    public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.in(PROP_NAME_taskStatus,
                List.of(_NopJobCoreConstants.TASK_STATUS_RUNNING, _NopJobCoreConstants.TASK_STATUS_CLAIMED, _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS)));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_startTime, false);
        query.addOrderField(PROP_NAME_jobTaskId, false);
        return taskDao().findAllByQuery(query);
    }

    @Override
    public List<NopJobTask> findTasksByFireId(String jobFireId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_jobFireId, jobFireId));
        query.addOrderField(PROP_NAME_taskNo, false);
        query.addOrderField(PROP_NAME_jobTaskId, false);
        return taskDao().findAllByQuery(query);
    }

    @Override
    public NopJobTask loadTask(String jobTaskId) {
        return taskDao().requireEntityById(jobTaskId);
    }

    @Override
    public long countInFlightTasks(String workerInstanceId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in(PROP_NAME_taskStatus,
                List.of(_NopJobCoreConstants.TASK_STATUS_RUNNING, _NopJobCoreConstants.TASK_STATUS_CLAIMED)));
        query.addFilter(FilterBeans.eq(PROP_NAME_workerInstanceId, workerInstanceId));
        return taskDao().countByQuery(query);
    }

    @Override
    public ResourceVector sumReservedCost(String workerInstanceId) {
        ReservedCostRow row = taskMapper.sumReservedCost(
                workerInstanceId, NopJobCoreConstants.RESERVED_TASK_STATUSES);
        if (row == null) {
            return ResourceVector.ZERO;
        }
        int cpu = row.getCpu() != null ? row.getCpu() : 0;
        int memory = row.getMemory() != null ? row.getMemory() : 0;
        return new ResourceVector(cpu, memory);
    }

    private void addPartitionFilter(QueryBean query, IntRangeSet partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : partitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(PROP_NAME_partitionIndex, range.getOffset(), range.getLast()));
        }
        query.addFilter(FilterBeans.or(rangeFilters));
    }

    private IOrmEntityDao<NopJobTask> taskDao() {
        return (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
    }
}
