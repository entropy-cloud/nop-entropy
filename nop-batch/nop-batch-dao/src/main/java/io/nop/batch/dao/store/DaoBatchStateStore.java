package io.nop.batch.dao.store;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.SourceLocation;
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
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_completedIndex;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_completeItemCount;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_endTime;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_execCount;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_processItemCount;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_resultCode;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_resultMsg;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_resultStatus;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_restartTime;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_sid;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_skipItemCount;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskKey;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskName;
import static io.nop.batch.dao.entity._gen._NopBatchTask.PROP_NAME_taskStatus;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

public class DaoBatchStateStore extends AbstractDaoHandler implements IBatchStateStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoBatchStateStore.class);

    /**
     * C7-001（plan 2516 裁定①，2514 裁定⑥投影）：RUNNING 行 stale 接管租约（毫秒）。
     * 活实例由 per-chunk checkpoint 续租（saveTaskState 刷新 restartTime），接管窗 =
     * checkpoint 间隔 + 租约余量。配置 <=0 时 stale 谓词退化为恒 false（关闭接管，等价现状）——
     * 运维逃生门显式声明，非静默降级。
     */
    public static final IConfigReference<Long> CFG_BATCH_TASK_STALE_LEASE_MS = AppConfig.varRef(
            SourceLocation.fromClass(DaoBatchStateStore.class), "nop.batch.task.stale-lease-ms",
            Long.class, 600000L);


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

        // staleCutoff 单点计算（plan 2516 裁定①）：闸门守卫与 tryMarkTaskRunning WHERE 谓词共用同一值，无二源
        long staleLeaseMs = CFG_BATCH_TASK_STALE_LEASE_MS.get();
        boolean fencingEnabled = staleLeaseMs > 0;
        long staleCutoff = CoreMetrics.currentTimeMillis() - staleLeaseMs;

        // 常规路径的快失败检查（并发场景由下方启动闸门在DB层裁决）。
        // C7-001（plan 2516 裁定①）：守卫 stale 感知化——收窄为 live 行快速路径。
        // taskStatus<=RUNNING 且租约未到期（restartTime 非空且大于 staleCutoff）维持 EXIST_RUNNING 响亮失败；
        // restartTime 为 NULL 或已过租约期的死区行放行进入 tryMarkTaskRunning，由条件 UPDATE rowsAffected 单飞裁决
        // （竞态下另一实例先行接管时 rowsAffected==0，下方既有响亮失败兜底）。错误码语义零回退，仅触发条件集合变化。
        // fencingEnabled=false（stale-lease-ms<=0）时保持现状语义（运维逃生门，等价现状显式声明）。
        if (task.getTaskStatus() <= NopBatchDaoConstants.TASK_STATUS_RUNNING
                && (!fencingEnabled || isLiveLease(task.getRestartTime(), staleCutoff))) {
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
        if (!tryMarkTaskRunning(session, task, fencingEnabled, staleCutoff)) {
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
     * 启动闸门：UPDATE ... SET taskStatus=RUNNING WHERE sid=? AND <抢占谓词>。
     * 返回false表示并发实例已先行启动（或状态已变化），调用方按既有错误语义响亮失败。
     * <p>
     * C7-001（plan 2516 裁定①，2514 裁定⑥投影——引用不重建）：fencingEnabled 时 WHERE 扩展 stale 谓词——
     * 可重启状态（taskStatus > RUNNING）照旧可进入；RUNNING/未启动死区行当租约过期
     * （restartTime <= staleCutoff）或从未持租约（restartTime IS NULL，NULL 判 stale）时可被接管。
     * rowsAffected==1 为单飞判据；KILLED 行在 startExistingTask 先行拒绝，不被 fencing 覆写。
     * fencingEnabled=false（stale-lease-ms<=0，运维逃生门）时维持仅 taskStatus > RUNNING 的现状谓词。
     */
    protected boolean tryMarkTaskRunning(IOrmSession session, NopBatchTask task, boolean fencingEnabled,
                                         long staleCutoff) {
        SQL.SqlBuilder sb = SQL.begin().update(NopBatchTask.class.getName())
                .set()
                .eq(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING)
                .where().eq(PROP_NAME_sid, task.getSid())
                .and();
        if (fencingEnabled) {
            sb.sql("(")
                    .gt(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING)
                    .sql(" or (").le(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING)
                    .sql(" and (").isNull(PROP_NAME_restartTime)
                    .sql(" or ").le(PROP_NAME_restartTime, new Timestamp(staleCutoff))
                    .sql(")))");
        } else {
            sb.gt(PROP_NAME_taskStatus, NopBatchDaoConstants.TASK_STATUS_RUNNING);
        }
        return session.executeUpdate(sb.end()) > 0;
    }

    /**
     * C7-001（plan 2516 裁定①）：live 租约判定——restartTime 非空且未过 staleCutoff 的 RUNNING 行
     * 视为活实例（快速路径响亮失败）；NULL 或过期的行交由 tryMarkTaskRunning 条件 UPDATE 单飞裁决。
     */
    private static boolean isLiveLease(Timestamp restartTime, long staleCutoff) {
        return restartTime != null && restartTime.getTime() > staleCutoff;
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

        // C7-001（plan 2516 裁定①）：续租写点——每次 checkpoint 刷新 restartTime（store 侧单点），
        // 活实例租约窗 = checkpoint 间隔 + 租约余量，防止长任务被 stale 接管误伤。
        Timestamp now = CoreMetrics.currentTimestamp();
        task.setRestartTime(now);

        // C7-001（plan 2516 裁定①）：saveTaskState 改条件 UPDATE——execCount 随每次进入 RUNNING 递增，
        // 为天然 fencing token（context 持有启动时的实例本地 execCount，非 DB 最新值）。
        // rowsAffected==0 说明本实例已被 stale 接管/重启（僵尸写者），本次状态写被丢弃，
        // 不得覆写新实例已刷新的 taskStatus/workerId/进度列。丢弃显式 ERROR 留痕、不抛出打断清理链
        // （失败事实可见、与裁定⑦不吞语义同型；行残留由 stale 接管承接）。
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(PROP_NAME_completeItemCount, task.getCompleteItemCount());
        props.put(PROP_NAME_skipItemCount, task.getSkipItemCount());
        props.put(PROP_NAME_completedIndex, task.getCompletedIndex());
        props.put(PROP_NAME_processItemCount, task.getProcessItemCount());
        props.put(PROP_NAME_restartTime, now);
        if (err != null) {
            props.put(PROP_NAME_resultCode, task.getResultCode());
            props.put(PROP_NAME_resultStatus, task.getResultStatus());
            props.put(PROP_NAME_resultMsg, task.getResultMsg());
        }
        if (complete) {
            props.put(PROP_NAME_taskStatus, task.getTaskStatus());
            props.put(PROP_NAME_endTime, task.getEndTime());
        }

        SQL sql = SQL.begin().update(NopBatchTask.class.getName())
                .set(null, props)
                .where().eq(PROP_NAME_sid, task.getSid())
                .and().eq(PROP_NAME_execCount, task.getExecCount())
                .end();

        long updated = runLocal(session -> session.executeUpdate(sql));
        if (updated <= 0) {
            LOG.error("nop.err.batch.task-state-write-lost-fencing:taskId={},execCount={},complete={}",
                    task.getSid(), task.getExecCount(), complete);
        }
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
        // C7-001（plan 2516 裁定①）：insert-path 租约初始写点——insert 即持租约。
        // 否则 NULL 判 stale 语义下，新插入的活行会在首 chunk checkpoint 前被并发实例接管（双重执行）。
        task.setRestartTime(CoreMetrics.currentTimestamp());
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
