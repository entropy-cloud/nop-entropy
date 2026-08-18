package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.core.NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobQueryHelper;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.mapper.NopJobTaskMapper;
import io.nop.job.dao.mapper.ReservedCostRow;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;

import java.sql.Timestamp;
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
        return taskDao().tryUpdateWithVersionCheck(task);
    }

    @Override
    public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) {
        return fetchWaitingTasks(limit, partitions, null, false);
    }

    @Override
    public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                              String workerInstanceId, boolean enforceAttribution) {
        // plan 340 §2.11 (P3-e): enforce-attribution with unset hostId is a config error —
        // throwing prevents a null-hostId worker from silently seeing all tasks (which would
        // defeat the dedicated-pool isolation that enforceAttribution is meant to provide).
        if (enforceAttribution && (workerInstanceId == null || workerInstanceId.isEmpty())) {
            throw new NopException(JobCoreErrors.ERR_JOB_WORKER_INSTANCE_ID_REQUIRED)
                    .param(JobCoreErrors.ARG_ENFORCE_ATTRIBUTION, true);
        }
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, _NopJobCoreConstants.TASK_STATUS_WAITING));
        if (enforceAttribution) {
            query.addFilter(FilterBeans.or(
                    FilterBeans.eq(PROP_NAME_workerInstanceId, workerInstanceId),
                    FilterBeans.isNull(PROP_NAME_workerInstanceId)
            ));
        }
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
        query.addOrderField(PROP_NAME_priority, true);
        query.addOrderField(PROP_NAME_createTime, false);
        query.addOrderField(PROP_NAME_jobTaskId, false);
        return taskDao().findPageByQuery(query);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        for (NopJobTask task : tasks) {
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CLAIMED);
            task.setWorkerInstanceId(workerInstanceId);
        }
        return taskDao().tryUpdateManyWithVersionCheck(tasks);
    }

    @Override
    public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions,
                                              Timestamp cursorTime, String cursorId) {
        validateCursor(cursorTime, cursorId);
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.in(PROP_NAME_taskStatus, JobTaskStateMachine.RUNNING_LIKE_STATUSES));
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
        if (cursorTime != null) {
            query.addFilter(FilterBeans.or(
                    FilterBeans.lt(PROP_NAME_startTime, cursorTime),
                    FilterBeans.and(
                            FilterBeans.eq(PROP_NAME_startTime, cursorTime),
                            FilterBeans.lt(PROP_NAME_jobTaskId, cursorId)
                    )
            ));
        }
        query.addOrderField(PROP_NAME_startTime, true);
        query.addOrderField(PROP_NAME_jobTaskId, true);
        return taskDao().findPageByQuery(query);
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
        query.addFilter(FilterBeans.in(PROP_NAME_taskStatus, JobTaskStateMachine.IN_FLIGHT_STATUSES));
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

    @Override
    public List<WorkerReservedCost> sumReservedCostByWorker() {
        return taskMapper.sumReservedCostByWorker(NopJobCoreConstants.RESERVED_TASK_STATUSES);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobTask> resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs,
                                                   Timestamp cursorTime, String cursorId) {
        validateCursor(cursorTime, cursorId);
        QueryBean query = new QueryBean();
        query.setLimit(batchSize);
        query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, _NopJobCoreConstants.TASK_STATUS_WAITING));
        query.addFilter(FilterBeans.lt(PROP_NAME_createTime, new java.sql.Timestamp(deadlineMs)));
        JobQueryHelper.addPartitionFilter(query, partitions, PROP_NAME_partitionIndex);
        if (cursorTime != null) {
            query.addFilter(FilterBeans.or(
                    FilterBeans.lt(PROP_NAME_createTime, cursorTime),
                    FilterBeans.and(
                            FilterBeans.eq(PROP_NAME_createTime, cursorTime),
                            FilterBeans.lt(PROP_NAME_jobTaskId, cursorId)
                    )
            ));
        }
        query.addOrderField(PROP_NAME_createTime, true);
        query.addOrderField(PROP_NAME_jobTaskId, true);

        List<NopJobTask> stale = taskDao().findPageByQuery(query);
        if (stale.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Re-dispatch: clear workerInstanceId so any worker (competing-consumer) can claim.
        // Keep taskStatus=WAITING (no FAILED) to preserve the executable opportunity.
        // Optimistic version check (tryUpdateManyWithVersionCheck) protects against concurrent
        // transitions (e.g. a worker that just claimed a task flips it to CLAIMED + bumps version,
        // failing our update so we don't clobber live state).
        for (NopJobTask task : stale) {
            task.setWorkerInstanceId(null);
        }
        taskDao().tryUpdateManyWithVersionCheck(stale);
        // Return the fetched stale list (entities mutated: workerInstanceId=null, but
        // createTime/jobTaskId retained for caller cursor advancement). Cursor must advance
        // past these rows even if some had version-conflict (they'll have transitioned to
        // CLAIMED via worker, leaving the WAITING result set on next fetch anyway).
        return stale;
    }

    private static void validateCursor(Timestamp cursorTime, String cursorId) {
        if (cursorTime == null && cursorId != null) {
            throw new IllegalArgumentException("cursorId requires cursorTime");
        }
    }

    private IOrmEntityDao<NopJobTask> taskDao() {
        return (IOrmEntityDao<NopJobTask>) daoProvider.daoFor(NopJobTask.class);
    }
}
