package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_jobFireId;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_jobTaskId;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_partitionIndex;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_createTime;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_startTime;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_taskNo;
import static io.nop.job.dao.entity._gen._NopJobTask.PROP_NAME_taskStatus;

public class JobTaskStoreImpl implements IJobTaskStore {
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CLAIMED = 10;
    private static final int TASK_STATUS_RUNNING = 20;

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public NopJobTask newTask() {
        return taskDao().newEntity();
    }

    @Override
    public void saveTask(NopJobTask task) {
        taskDao().saveEntityDirectly(task);
    }

    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public void updateTask(NopJobTask task) {
        taskDao().updateEntityDirectly(task);
    }

    @Override
    public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) {
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, TASK_STATUS_WAITING));
        addPartitionFilter(query, partitions);
        query.addOrderField(PROP_NAME_createTime, false);
        query.addOrderField(PROP_NAME_jobTaskId, false);
        return taskDao().findAllByQuery(query);
    }

    @Override
    public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        java.sql.Timestamp lockTime = new java.sql.Timestamp(
                taskDao().getDbEstimatedClock().getMaxCurrentTimeMillis() + Math.max(lockTimeoutMs, 1L));
        for (NopJobTask task : tasks) {
            task.setTaskStatus(TASK_STATUS_CLAIMED);
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
        query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, TASK_STATUS_RUNNING));
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
