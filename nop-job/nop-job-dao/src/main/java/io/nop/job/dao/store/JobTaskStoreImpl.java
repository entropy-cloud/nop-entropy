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

    /**
     * CAS认领（WAITING→CLAIMED + workerInstanceId）。注意：{@code lockTimeoutMs}参数当前不参与
     * 认领判定——CLAIMED态的回收依赖worker存活链（SUSPICIOUS→TIMEOUT）或执行超时配置，
     * 而非认领租约超时（租约机制需设计引入，接口保留参数以兼容既有签名）。
     * <p>
     * check2 [P2-1]: 认领时写入 startTime（= claim 时刻）。此前 CLAIMED 行的 startTime 为 null
     * （进入 RUNNING 才写入），被 {@link #fetchRunningTasks} 的 {@code not(isNull(startTime))}
     * 过滤排除——worker 在认领后、置 RUNNING 前崩溃则该行对超时扫描与存活链永久不可见，
     * 任务永不终结、所属 fire 永久 RUNNING。认领时写入后 CLAIMED 行纳入存活链扫描
     * （isInFlight 含 CLAIMED），worker 消失即 SUSPICIOUS→TIMEOUT 回收；worker 正常推进时
     * {@code JobWorkerScannerImpl.executeTask} 在 CLAIMED→RUNNING 转换会以真实开始时刻覆写。
     */
    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        Timestamp claimTime = new Timestamp(taskDao().getDbEstimatedClock().getMaxCurrentTimeMillis());
        for (NopJobTask task : tasks) {
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CLAIMED);
            task.setWorkerInstanceId(workerInstanceId);
            task.setStartTime(claimTime);
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
        // CLAIMED∈RUNNING_LIKE_STATUSES但其startTime为null（进入RUNNING前才写入）：超时检查对
        // null startTime本就跳过（tryMarkTimeout提前return），纳入扫描只会在满批尾部把cursor推进为
        // (null,id)，下一轮fetchRunningTasks抛IllegalArgumentException使整轮扫描停滞。
        // 排除null行同时消除崩溃面与无效扫描（CLAIMED滞留回收属另一课题）
        query.addFilter(FilterBeans.not(FilterBeans.isNull(PROP_NAME_startTime)));
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
