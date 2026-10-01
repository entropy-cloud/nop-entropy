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
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmSession;
import io.nop.orm.dao.AbstractDaoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_ID;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_KEY;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_NAME;
import static io.nop.batch.dao.NopBatchDaoErrors.ARG_TASK_STATUS;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_EXCEED_START_LIMIT;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_COMPLETED;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE;
import static io.nop.batch.dao.NopBatchDaoErrors.ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_sid;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskKey;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskName;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskStatus;

public class DaoBatchStateStore extends AbstractDaoHandler implements IBatchStateStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoBatchStateStore.class);

    protected IEntityDao<NopBatchTask> taskDao() {
        return daoFor(NopBatchTask.class);
    }

    @Override
    public void loadTaskState(IBatchTaskContext context) {
        runLocal(session -> {
            loadTaskState0(session, context);
            return null;
        });
    }

    private void loadTaskState0(IOrmSession session, IBatchTaskContext context) {
        IEntityDao<NopBatchTask> taskDao = taskDao();
        NopBatchTask task = loadExistingTask(taskDao, context);

        if (task == null) {
            task = tryInsertNewTask(context);
            if (task != null) {
                context.setTaskId(task.getSid());
                return;
            }

            // 输掉了(taskName,taskKey)唯一键的插入竞争：并发实例已提交同键任务行。
            // 重新加载后转入既有实例路径，由startExistingTask的启动闸门裁决唯一活跃实例
            task = loadExistingTask(taskDao, context);
            if (task == null) {
                // 理论不可达：唯一键冲突必然对应已提交行。防御性响亮失败，避免无行可启动时静默通过
                throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE)
                        .param(ARG_TASK_NAME, context.getTaskName())
                        .param(ARG_TASK_KEY, context.getTaskKey());
            }
        }

        startExistingTask(session, taskDao, task, context);
    }

    /**
     * 在独立事务（REQUIRES_NEW）中插入新任务行。数据库层的 (taskName,taskKey) 唯一键
     * （nop-batch.orm.xml UK_NOP_BATCH_TASK_NAME_KEY）把并发首发的插入竞态转变为可判定结果：
     * 冲突只回滚本次插入（避免污染外层会话/事务），返回null表示竞争失败。
     */
    protected NopBatchTask tryInsertNewTask(IBatchTaskContext context) {
        try {
            return runLocal(session -> {
                IEntityDao<NopBatchTask> taskDao = taskDao();
                NopBatchTask task = newTask(taskDao);
                task.setTaskKey(context.getTaskKey());
                task.setTaskName(context.getTaskName());
                task.setFlowId(context.getFlowId());
                task.setFlowStepId(context.getFlowStepId());
                setTaskRecord(context, task);
                saveTask(taskDao, task);
                return task;
            });
        } catch (Exception e) {
            if (isDuplicateKeyError(e)) {
                LOG.info("nop.batch.task-start-insert-race-lost:taskName={},taskKey={}",
                        context.getTaskName(), context.getTaskKey());
                return null;
            }
            throw e;
        }
    }

    private void startExistingTask(IOrmSession session, IEntityDao<NopBatchTask> taskDao,
                                   NopBatchTask task, IBatchTaskContext context) {
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

        // 常规路径的快失败检查（并发场景由下方启动闸门在DB层裁决）
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

        // 启动闸门（plan 2282 G7-04-01）：条件更新仅允许从可重启状态（taskStatus > RUNNING）
        // 原子迁移到RUNNING。affected-rows=0表示并发实例已先行启动，check-then-act竞态
        // 不再依赖读后判，而是由数据库层的单语句原子性判定
        if (!tryMarkTaskRunning(session, task)) {
            LOG.warn("nop.batch.task-start-conflict:taskId={},taskName={},taskKey={},taskStatus={}",
                    task.getSid(), task.getTaskName(), task.getTaskKey(), task.getTaskStatus());
            throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE)
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

        updateTask(taskDao, task);

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

    /**
     * 启动闸门：UPDATE ... SET taskStatus=RUNNING WHERE sid=? AND taskStatus > RUNNING。
     * 返回false表示并发实例已先行启动（或状态已变化），调用方按既有错误语义响亮失败。
     */
    protected boolean tryMarkTaskRunning(IOrmSession session, NopBatchTask task) {
        SQL sql = SQL.begin().update(NopBatchTask.class.getName())
                .set()
                .eq(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING)
                .where().eq(PROP_NAME_sid, task.getSid())
                .and().gt(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING)
                .end();
        return session.executeUpdate(sql) > 0;
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

    private static boolean isDuplicateKeyError(Throwable e) {
        return e instanceof NopException
                && DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(((NopException) e).getErrorCode());
    }
}
