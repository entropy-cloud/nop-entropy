package io.nop.wf.scheduler;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.api.WfReference;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.service.WorkflowServiceSpi;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.IWorkflowStore;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 到期/提醒任务扫描器。
 *
 * plan 2514 MA-05（裁定④）：R02 分批范式——查询族 limit+orderBy 分页游标推进，
 * 单轮扫描在时间预算内推进多批，扫描会话内存峰值有界；毒步骤（due action 恒失败）
 * 以指数退避推进 dueTime 退出本轮扫描集，扫描集有界收敛。
 *
 * plan 2514 MA-23（裁定⑥）：扫描抢占 fencing——条件 UPDATE 抢占（holderId+租约），
 * rowsAffected==1 赢单飞，败者以 race skip INFO 形态显式跳过；remindCount 经 DB
 * 原子自增。扫描链 riding R1-01 绝缘层契约：单条失败仍走条级 catch，链存活与失败
 * 可见不回归。
 */
public class WfTaskScanner {
    static final Logger LOG = LoggerFactory.getLogger(WfTaskScanner.class);

    /**
     * 毒步骤退避基准（裁定④）：60s × 2^n，封顶 1h。
     */
    static final long BACKOFF_BASE_MS = 60_000L;
    static final long BACKOFF_MAX_MS = 3_600_000L;
    private static final int BACKOFF_MAX_SHIFT = 12;

    /**
     * 退避计数 map 有界兜底（裁定④）：超出即整体清空并 WARN，计数归零重启退避，
     * 只损退避精度不损收敛性。
     */
    static final int FAIL_COUNT_MAP_CAP = 10_000;

    private IWorkflowStore workflowStore;
    private IWorkflowManager workflowManager;
    private WorkflowServiceSpi workflowService;
    private IOrmTemplate ormTemplate;
    private List<IWfTaskReminderListener> reminderListeners = Collections.emptyList();

    /**
     * holderId 归因前缀（裁定⑥）：nodeId 取配置，缺省=主机名+进程启动随机后缀，装配期一次生成。
     */
    private String nodeId;

    @Inject
    public void setWorkflowStore(IWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @Inject
    public void setWorkflowService(WorkflowServiceSpi workflowService) {
        this.workflowService = workflowService;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setReminderListeners(List<IWfTaskReminderListener> reminderListeners) {
        if (reminderListeners != null) {
            this.reminderListeners = reminderListeners;
        }
    }

    @PostConstruct
    public void init() {
        // MA-05（裁定④）：配置键装配期 fail-fast（rule 24 显式失败，非静默钳制）
        if (getScanBatchSize() <= 0)
            throw new NopException(NopWfCoreErrors.ERR_WF_INVALID_SCHEDULER_CONFIG)
                    .param(NopWfCoreErrors.ARG_CONFIG_NAME, "nop.wf.scheduler.scan-batch-size")
                    .param(NopWfCoreErrors.ARG_CONFIG_VALUE, getScanBatchSize());
        if (getScanTimeBudgetMs() < 0)
            throw new NopException(NopWfCoreErrors.ERR_WF_INVALID_SCHEDULER_CONFIG)
                    .param(NopWfCoreErrors.ARG_CONFIG_NAME, "nop.wf.scheduler.scan-time-budget-ms")
                    .param(NopWfCoreErrors.ARG_CONFIG_VALUE, getScanTimeBudgetMs());
        resolveNodeId();
    }

    private static <T> T cfg(IConfigReference<T> ref) {
        return ref.get();
    }

    int getScanBatchSize() {
        return cfg(WfSchedulerConfigs.CFG_WF_SCAN_BATCH_SIZE);
    }

    long getScanTimeBudgetMs() {
        return cfg(WfSchedulerConfigs.CFG_WF_SCAN_TIME_BUDGET_MS);
    }

    long getScanLeaseMs() {
        return cfg(WfSchedulerConfigs.CFG_WF_SCAN_LEASE_MS);
    }

    String resolveNodeId() {
        if (nodeId != null)
            return nodeId;
        String configured = WfSchedulerConfigs.CFG_WF_SCHEDULER_NODE_ID.get();
        if (configured != null && !configured.isBlank()) {
            nodeId = configured;
        } else {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown-host";
            }
            nodeId = host + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        LOG.info("nop.wf.scheduler.node-id-resolved:nodeId={}", nodeId);
        return nodeId;
    }

    public void scanDueTasks() {
        ormTemplate.runInNewSession(session -> {
            scanDueTasksInCurrentSession();
            return null;
        });
    }

    public void scanRemindTasks() {
        ormTemplate.runInNewSession(session -> {
            scanRemindTasksInCurrentSession();
            return null;
        });
    }

    /**
     * MA-05（裁定④）：due 扫描主循环——按批次（limit+orderBy(dueTime) 游标推进）在时间
     * 预算内消费到期集合，无全量物化；预算耗尽剩余面留待下轮（cron 自补偿）。
     */
    void scanDueTasksInCurrentSession() {
        int batchSize = getScanBatchSize();
        long budgetMs = getScanTimeBudgetMs();
        long deadline = budgetMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + budgetMs;

        // 毒步骤连续失败轮次计数（有界 cap，裁定④）
        Map<String, Integer> failCounts = new HashMap<>();
        Timestamp cursor = null;
        int scanned = 0;

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                LOG.info("nop.wf.scheduler.scan-time-budget-exhausted:scanned={},budgetMs={}", scanned, budgetMs);
                break;
            }

            List<? extends IWorkflowStepRecord> batch = workflowStore.findDueActivatedSteps(batchSize, cursor);
            if (batch == null || batch.isEmpty())
                break;

            for (IWorkflowStepRecord stepRecord : batch) {
                scanDueTask(stepRecord, failCounts);
                scanned++;
            }

            if (batch.size() < batchSize)
                break;
            Timestamp last = batch.get(batch.size() - 1).getDueTime();
            if (last == null)
                break;
            cursor = last;
        }
    }

    /**
     * 单条到期任务处理：先抢占（MA-23 裁定⑥ fencing）再执行，单条失败不中断整批
     * （R1-01 绝缘层条级 catch 形态零回退）；失败步骤按指数退避推进 dueTime 退出本轮
     * 扫描集（MA-05 裁定④毒步骤收敛，dueTime 兼作下次重试时间——MA-06 接缝字段语义）。
     */
    private void scanDueTask(IWorkflowStepRecord stepRecord, Map<String, Integer> failCounts) {
        String stepKey = stepRecord.getWfId() + "/" + stepRecord.getStepId();
        String holderId = resolveNodeId() + ":" + UUID.randomUUID();
        Timestamp leaseUntil = new Timestamp(System.currentTimeMillis() + getScanLeaseMs());
        if (!workflowStore.tryClaimScanLease(stepRecord, holderId, leaseUntil)) {
            // 裁定⑥：抢占失败显式 skip（race skip INFO 形态复用，非吞）
            LOG.info("nop.wf.scheduler.skip-due-task-scan-lease-lost:wfId={},stepId={}",
                    stepRecord.getWfId(), stepRecord.getStepId());
            return;
        }

        try {
            // 单个到期任务失败不中断整批：getDueAction与动作执行都纳入隔离
            // （getDueAction内部的实例加载可能因实例被并发删除而抛错）
            String dueAction = null;
            try {
                dueAction = getDueAction(stepRecord);
                if (dueAction == null || dueAction.isBlank()) {
                    LOG.debug("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={}",
                            stepRecord.getWfId(), stepRecord.getStepId());
                    return;
                }

                WfActionRequestBean request = new WfActionRequestBean();
                request.setWfId(stepRecord.getWfId());
                request.setStepId(stepRecord.getStepId());
                request.setActionName(dueAction);

                FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
            } catch (NopException e) {
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode()
                        .equals(e.getErrorCode())) {
                    LOG.info("nop.wf.scheduler.skip-due-task-race:wfId={},stepId={}",
                            stepRecord.getWfId(), stepRecord.getStepId());
                    return;
                }
                if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER.getErrorCode()
                        .equals(e.getErrorCode())) {
                    // P1-3.1-002 修复后 scanner context 携带系统触发标记，allowCallByUser 应放行；
                    // 此分支仅剩防御意义（并发步骤状态变迁竞态下 invokeAction 前置校验仍可能拒绝）
                    LOG.warn("nop.wf.scheduler.due-action-blocked-by-user-check:wfId={},stepId={},dueAction={}",
                            stepRecord.getWfId(), stepRecord.getStepId(), dueAction);
                    return;
                }
                postponeFailedDueTask(stepRecord, failCounts, stepKey, dueAction, e);
            } catch (Exception e) {
                // 非NopException的运行时异常同样只影响单条任务
                postponeFailedDueTask(stepRecord, failCounts, stepKey, dueAction, e);
            }
            // 成功路径（含 race skip/无 dueAction）退出失败计数：entry 随步骤离开失败态摘除
            failCounts.remove(stepKey);
        } finally {
            workflowStore.releaseScanLease(stepRecord, holderId);
        }
    }

    /**
     * MA-05（裁定④）毒步骤退避：dueTime 推进 now + min(60s × 2^n, 1h)（n=连续失败轮次，
     * 扫描器内存计数 + dueTime 推进量自恢复；重启后计数归零按基准重启退避，可接受）。
     */
    private void postponeFailedDueTask(IWorkflowStepRecord stepRecord, Map<String, Integer> failCounts,
                                       String stepKey, String dueAction, Exception e) {
        LOG.error("nop.wf.scheduler.due-action-failed:wfId={},stepId={},dueAction={}",
                stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);

        if (failCounts.size() >= FAIL_COUNT_MAP_CAP) {
            LOG.warn("nop.wf.scheduler.due-fail-count-map-cap-reached:cap={},map-cleared",
                    FAIL_COUNT_MAP_CAP);
            failCounts.clear();
        }
        int failures = failCounts.merge(stepKey, 1, Integer::sum);
        long backoff = Math.min(BACKOFF_BASE_MS * (1L << Math.min(failures - 1, BACKOFF_MAX_SHIFT)), BACKOFF_MAX_MS);
        Timestamp newDueTime = new Timestamp(System.currentTimeMillis() + backoff);
        // 失败退避路径显式可见（ERROR + 退避量留痕，非空 catch）
        LOG.warn("nop.wf.scheduler.due-task-backoff-postpone:wfId={},stepId={},failures={},backoffMs={},newDueTime={}",
                stepRecord.getWfId(), stepRecord.getStepId(), failures, backoff, newDueTime);
        workflowStore.postponeStepDueTime(stepRecord, newDueTime);
    }

    /**
     * MA-05（裁定④）：remind 扫描主循环，与 due 半区同构同修（分批游标 + 时间预算）。
     * 精度备注：remindTime 引擎侧无写入点（remind 半区当前恒空集），同构同修不豁免。
     */
    void scanRemindTasksInCurrentSession() {
        int batchSize = getScanBatchSize();
        long budgetMs = getScanTimeBudgetMs();
        long deadline = budgetMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + budgetMs;

        Timestamp cursor = null;
        int scanned = 0;

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                LOG.info("nop.wf.scheduler.scan-time-budget-exhausted:scanned={},budgetMs={}", scanned, budgetMs);
                break;
            }

            List<? extends IWorkflowStepRecord> batch = workflowStore.findRemindActivatedSteps(batchSize, cursor);
            if (batch == null || batch.isEmpty())
                break;

            for (IWorkflowStepRecord stepRecord : batch) {
                scanRemindTask(stepRecord);
                scanned++;
            }

            if (batch.size() < batchSize)
                break;
            Timestamp last = batch.get(batch.size() - 1).getRemindTime();
            if (last == null)
                break;
            cursor = last;
        }
    }

    /**
     * 单条提醒处理：先抢占（MA-23 裁定⑥）再通知 listener；remindCount 经
     * incrementRemindCount 原子自增（DaoWorkflowStore 直发 COALESCE+1 UPDATE，消除
     * 双会话读-改-写丢失更新），不再经实体逐字段落盘。
     */
    private void scanRemindTask(IWorkflowStepRecord stepRecord) {
        String holderId = resolveNodeId() + ":" + UUID.randomUUID();
        Timestamp leaseUntil = new Timestamp(System.currentTimeMillis() + getScanLeaseMs());
        if (!workflowStore.tryClaimScanLease(stepRecord, holderId, leaseUntil)) {
            LOG.info("nop.wf.scheduler.skip-remind-task-scan-lease-lost:wfId={},stepId={}",
                    stepRecord.getWfId(), stepRecord.getStepId());
            return;
        }

        try {
            IWorkflowStep step = workflowManager.getWorkflow(stepRecord.getWfId()).getStepById(stepRecord.getStepId());
            reminderListeners.forEach(listener -> listener.onRemind(step));

            workflowStore.incrementRemindCount(stepRecord);
        } catch (Exception e) {
            // 单条提醒失败（实例被删除、listener抛错）不中断整批；失败的记录不更新提醒计数，
            // 下一轮扫描可重试
            LOG.error("nop.wf.scheduler.remind-task-failed:wfId={},stepId={}",
                    stepRecord.getWfId(), stepRecord.getStepId(), e);
        } finally {
            workflowStore.releaseScanLease(stepRecord, holderId);
        }
    }

    private String getDueAction(IWorkflowStepRecord stepRecord) {
        IWorkflowModel workflowModel = workflowManager.getWorkflow(stepRecord.getWfId()).getModel();
        IWorkflowStepModel stepModel = workflowModel.getStep(stepRecord.getStepName());
        return stepModel == null ? null : stepModel.getDueAction();
    }

    private IServiceContext newSchedulerContext() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.getContext().setUserId("wf-scheduler");
        // P1-3.1-002（plan 2379）：dueAction 的触发主体是系统而非用户，携带系统触发标记使
        // 引擎 allowCallByUser 对步骤动作调用放行（attr 仅服务端可达，用户请求不可伪造）
        context.getContext().setAttribute(NopWfCoreConstants.ATTR_WF_SYSTEM_ACTION_TRIGGER, Boolean.TRUE);
        return context;
    }
}
