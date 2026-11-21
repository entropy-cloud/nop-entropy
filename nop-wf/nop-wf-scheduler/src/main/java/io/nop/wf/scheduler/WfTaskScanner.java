package io.nop.wf.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.metrics.GlobalMeterRegistry;
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
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

    // W-01(b)（CRA 8.48 R4-08，MA-06 残留面裁定 E / C8-015 协同半面最小落地）：due 扫描
    // 观测 gauge——最近一轮消费条数/失败条数。载体为静态 AtomicLong（扫描器生产形态为单例
    // bean；多实例测试共享载体），注册以静态守卫防 Micrometer 同 id 重复注册。
    // MA-09 全量观测治理归 R-G 零触碰（本两枚不扩 stats 面）
    private static final AtomicLong DUE_SCAN_SCANNED = new AtomicLong();
    private static final AtomicLong DUE_SCAN_FAILURES = new AtomicLong();
    private static final AtomicBoolean GAUGES_REGISTERED = new AtomicBoolean();

    // plan 2542 W-06(a)（CRA 8.49 R4-09，1.5-C4-007 裁定 A）：扫描轮失败计数 gauge 载体——
    // 入口异常边界收敛后（catch(Exception) 留痕+正常返回），单轮瞬态故障以本 gauge 反映，
    // fire 以成功收尾、下一轮 cron 自补偿。Error 不拦截（直传宿主）
    private static final AtomicLong SCAN_ROUND_FAILURES = new AtomicLong();

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
        if (getActionDeadlineMs() < 0)
            throw new NopException(NopWfCoreErrors.ERR_WF_INVALID_SCHEDULER_CONFIG)
                    .param(NopWfCoreErrors.ARG_CONFIG_NAME, "nop.wf.scheduler.action-deadline-ms")
                    .param(NopWfCoreErrors.ARG_CONFIG_VALUE, getActionDeadlineMs());
        checkDeadlineVsLease();
        registerScanGauges();
        resolveNodeId();
    }

    /**
     * plan 2585 Phase 2：装配期校验 deadline 与 lease 的配比——action-deadline-ms 大于
     * scan-lease-ms 时，due action 在租约过期后仍可能继续占用（他节点可重复抢占同一步骤，
     * fencing 单飞失效窗口 = deadline - lease）。deadline<=0（禁用）不校验。
     * 取 WARN 而非 fail-fast：该配比只放大重复执行风险面，不破坏状态机正确性
     * （引擎侧 status 守卫兜底），避免存量部署启动失败。
     */
    private void checkDeadlineVsLease() {
        long deadlineMs = getActionDeadlineMs();
        long leaseMs = getScanLeaseMs();
        if (deadlineMs > 0 && leaseMs > 0 && deadlineMs > leaseMs) {
            LOG.warn("nop.wf.scheduler.action-deadline-exceeds-scan-lease:deadlineMs={},leaseMs={}; "
                    + "a due action may keep running after its scan lease expires, "
                    + "allowing another node to re-claim the same step",
                    deadlineMs, leaseMs);
        }
    }

    /**
     * W-01(b)（MA-06 残留面裁定 E）：due 扫描 backlog gauge 注册（静态载体一次注册）。
     * 命名沿 2514 gauge「前缀.对象.指标」纪律。
     */
    private void registerScanGauges() {
        if (GAUGES_REGISTERED.compareAndSet(false, true)) {
            MeterRegistry registry = GlobalMeterRegistry.instance();
            Gauge.builder("nop.wf.scheduler.due-scan-scanned", DUE_SCAN_SCANNED, AtomicLong::get)
                    .description("Steps consumed by the most recent due scan round (plan 2541 W-01 MA-06)")
                    .register(registry);
            Gauge.builder("nop.wf.scheduler.due-scan-failures", DUE_SCAN_FAILURES, AtomicLong::get)
                    .description("Failed steps in the most recent due scan round (plan 2541 W-01 MA-06)")
                    .register(registry);
            // W-06(a)（裁定 A）：扫描轮失败次数（累计）——入口异常边界收敛后的观测载体
            Gauge.builder("nop.wf.scheduler.scan-round-failures", SCAN_ROUND_FAILURES, AtomicLong::get)
                    .description("Failed scan rounds since startup (plan 2542 W-06 entry exception boundary)")
                    .register(registry);
        }
    }

    private static <T> T cfg(IConfigReference<T> ref) {
        return ref.get();
    }

    /**
     * R4-32（1.5-C7-006，plan 2566 [R1-M4]）双缝之墙钟缝：绝对时间域（leaseUntil/dueTime/
     * remindTime 写值，与 DB 可比）取值入口。默认 CoreMetrics::currentTimeMillis（epoch 毫秒）。
     * 回拨语义注记：绝对域写值随墙钟回拨将使租约/dueTime 相对变长（回拨=租约变长），自愈型
     * 有界（扫描集游标推进+到期重扫），不引入 DB 钟改造（防扩：1.5-C4-007/MA-05 归 R4-09）。
     */
    protected long nowMillis() {
        return CoreMetrics.currentTimeMillis();
    }

    /**
     * R4-32（1.5-C7-006 [R1-M4]）双缝之单调缝：纯相对预算域（扫描时间预算 deadline）取值入口。
     * 默认 CoreMetrics::nanoTime（单调纳秒）。预算毫秒阈值类内经 TimeUnit.MILLISECONDS.toNanos
     * 换算[R2-m6]，与 nanoTime 同单位比较。
     */
    protected long nowNanos() {
        return CoreMetrics.nanoTime();
    }

    int getScanBatchSize() {
        return cfg(WfSchedulerConfigs.CFG_WF_SCAN_BATCH_SIZE);
    }

    long getScanTimeBudgetMs() {
        return cfg(WfSchedulerConfigs.CFG_WF_SCAN_TIME_BUDGET_MS);
    }

    long getActionDeadlineMs() {
        return cfg(WfSchedulerConfigs.CFG_WF_ACTION_DEADLINE_MS);
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

    /**
     * W-06(a)（CRA 8.49 R4-09，1.5-C4-007 裁定 A）：入口异常边界收敛——单轮扫描失败
     * （分批查询 DB 异常/租约释放异常/会话级异常）不再外泄至 job fire 层，以 ERROR 留痕
     * + 轮失败 gauge 反映后正常返回，fire 以成功收尾、下一轮 cron 自补偿。
     *
     * <p>Error 不拦截（catch(Exception) 而非 Throwable）：job fire 线程无 promise 终结义务，
     * Error 直传宿主由其失败路径呈现——Error 转业务成功=致命降级；宿主侧 FAILED 终态语义
     * 归 nop-job-C7-006（跨单元，零触碰）。
     */
    public void scanDueTasks() {
        try {
            ormTemplate.runInNewSession(session -> {
                scanDueTasksInCurrentSession();
                return null;
            });
        } catch (Exception e) {
            LOG.error("nop.wf.scheduler.scan-round-failed:half=due", e);
            SCAN_ROUND_FAILURES.incrementAndGet();
        }
    }

    /**
     * W-06(a)（裁定 A）：remind 半区入口异常边界收敛，与 due 半区同构。
     */
    public void scanRemindTasks() {
        try {
            ormTemplate.runInNewSession(session -> {
                scanRemindTasksInCurrentSession();
                return null;
            });
        } catch (Exception e) {
            LOG.error("nop.wf.scheduler.scan-round-failed:half=remind", e);
            SCAN_ROUND_FAILURES.incrementAndGet();
        }
    }

    /**
     * MA-05（裁定④）：due 扫描主循环——按批次（limit+orderBy(dueTime) 游标推进）在时间
     * 预算内消费到期集合，无全量物化；预算耗尽剩余面留待下轮（cron 自补偿）。
     */
    void scanDueTasksInCurrentSession() {
        int batchSize = getScanBatchSize();
        long budgetMs = getScanTimeBudgetMs();
        // R4-32（C7-006）：预算为纯相对计时域——单调缝取值，毫秒阈值换算纳秒
        long deadline = budgetMs <= 0 ? Long.MAX_VALUE : nowNanos() + TimeUnit.MILLISECONDS.toNanos(budgetMs);

        // 毒步骤连续失败轮次计数（有界 cap，裁定④）
        Map<String, Integer> failCounts = new HashMap<>();
        // plan 2585 Phase 2：二元组游标 (dueTime, stepId)——同毫秒 dueTime 超批不再整批跳过
        Timestamp cursorTime = null;
        String cursorStepId = null;
        int scanned = 0;
        // W-01(b)（MA-06 残留面裁定 E）：最近一轮 due 扫描观测（每轮重置）
        DUE_SCAN_SCANNED.set(0);
        DUE_SCAN_FAILURES.set(0);

        while (true) {
            if (nowNanos() >= deadline) {
                LOG.info("nop.wf.scheduler.scan-time-budget-exhausted:scanned={},budgetMs={}", scanned, budgetMs);
                break;
            }

            List<? extends IWorkflowStepRecord> batch =
                    workflowStore.findDueActivatedSteps(batchSize, cursorTime, cursorStepId);
            if (batch == null || batch.isEmpty())
                break;

            for (IWorkflowStepRecord stepRecord : batch) {
                scanDueTask(stepRecord, failCounts);
                scanned++;
                DUE_SCAN_SCANNED.incrementAndGet();
            }

            if (batch.size() < batchSize)
                break;
            IWorkflowStepRecord last = batch.get(batch.size() - 1);
            if (last.getDueTime() == null || last.getStepId() == null)
                break;
            cursorTime = last.getDueTime();
            cursorStepId = last.getStepId();
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
        // R4-32（C7-006）：leaseUntil 为 DB 可比绝对时间——墙钟缝取值（绝对域保留 epoch）
        Timestamp leaseUntil = new Timestamp(nowMillis() + getScanLeaseMs());
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
                    // W-01(c)（CRA 8.48 R4-08，MA-06 残留面裁定 E）：dueTimeExpr 配置存在而
                    // dueAction 缺失——VFS 热更新漂移路径/部署盲区显式化（WARN 周期性语义：每轮
                    // 该步骤一次，量级受分批上界约束；否决硬失败——模型加载期校验触批外模块且
                    // 存量升级破坏风险真实，缺 dueAction 步骤本身无副作用）
                    LOG.warn("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={},stepName={}; "
                            + "dueTimeExpr is configured but dueAction is missing - "
                            + "the step will never be auto-driven, check the workflow model",
                            stepRecord.getWfId(), stepRecord.getStepId(), stepRecord.getStepName());
                    return;
                }

                WfActionRequestBean request = new WfActionRequestBean();
                request.setWfId(stepRecord.getWfId());
                request.setStepId(stepRecord.getStepId());
                request.setActionName(dueAction);

                invokeDueActionWithDeadline(request, stepRecord, dueAction);
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
     * W-06(b)×W-09（CRA 8.49 R4-09，1.5-C7-004 裁定 A，同簇一次设计）：单条 dueAction 带
     * deadline 同步等待——挂死动作不再永久占位扫描线程（共享调度池 pool-size=4 全冻结面
     * 收敛为 ≤action-deadline-ms）；超时条目经既有毒步骤退避路径推进 dueTime 退出本轮
     * 扫描集（60s×2^n 封顶 1h）。
     *
     * <p>契约：{@code ExecutionException} 解包 cause 后原样抛回，由调用方既有分类链
     * （race-skip / allowCallByUser / postpone）逐字处理；{@code TimeoutException} 走
     * deadline 跳条+退避；{@code InterruptedException} 恢复中断位后同超时形态退避；
     * {@code CancellationException} 为任务侧取消，直接跳条。
     *
     * <p>诚实登记：deadline 不终止底层 action 执行（宿主无 cancel 设施——cancelRunning
     * 空实现=形态 A 宿主面归 nop-job-C7-006），僵尸动作残留以「每挂死条目至多一个、
     * fire 正常收尾、退避退出扫描集」为收敛声明。
     */
    private void invokeDueActionWithDeadline(WfActionRequestBean request, IWorkflowStepRecord stepRecord,
                                             String dueAction) {
        CompletionStage<Object> stage = workflowService.invokeActionAsync(request, null, newSchedulerContext());
        long deadlineMs = getActionDeadlineMs();
        if (deadlineMs <= 0 || stage == null) {
            // 0=禁用（长动作部署显式逃生门）；null stage 与既有 syncGet(null)=null 语义一致
            FutureHelper.syncGet(stage);
            return;
        }

        try {
            stage.toCompletableFuture().get(deadlineMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("nop.wf.scheduler.due-action-deadline-exceeded:wfId={},stepId={},dueAction={},deadlineMs={}",
                    stepRecord.getWfId(), stepRecord.getStepId(), dueAction, deadlineMs);
            // 交由调用方既有分类链的 postponeFailedDueTask 统一退避（保持 failCounts 指数递增语义）
            throw NopException.adapt(e);
        } catch (InterruptedException e) {
            // 恢复中断位后归入跳条退避（同 TimeoutException 形态）
            Thread.currentThread().interrupt();
            LOG.warn("nop.wf.scheduler.due-action-wait-interrupted:wfId={},stepId={},dueAction={}",
                    stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);
            throw NopException.adapt(e);
        } catch (CancellationException e) {
            // 任务侧取消：直接跳条（非失败，不推进退避）
            LOG.info("nop.wf.scheduler.skip-due-task-action-cancelled:wfId={},stepId={},dueAction={}",
                    stepRecord.getWfId(), stepRecord.getStepId(), dueAction);
        } catch (ExecutionException e) {
            // 解包 cause 后按既有分类链处理（race-skip / allowCallByUser / postpone 逐字复用）
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof NopException)
                throw (NopException) cause;
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof Error)
                throw (Error) cause;
            throw NopException.adapt(cause);
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
        // R4-32（C7-006）：dueTime 为 DB 可比绝对时间——墙钟缝取值
        Timestamp newDueTime = new Timestamp(nowMillis() + backoff);
        // 失败退避路径显式可见（ERROR + 退避量留痕，非空 catch）
        LOG.warn("nop.wf.scheduler.due-task-backoff-postpone:wfId={},stepId={},failures={},backoffMs={},newDueTime={}",
                stepRecord.getWfId(), stepRecord.getStepId(), failures, backoff, newDueTime);
        workflowStore.postponeStepDueTime(stepRecord, newDueTime);
        // W-01(b)：最近一轮失败条数观测
        DUE_SCAN_FAILURES.incrementAndGet();
    }

    /**
     * MA-05（裁定④）：remind 扫描主循环，与 due 半区同构同修（分批游标 + 时间预算）。
     * 精度备注（W-01 更新，MA-06 残留面裁定 E）：remindTime 引擎侧唯一写入点=本类失败退避
     * 推进（postponeFailedRemindTask）；业务侧写入仍可注入扫描集。remind 半区失败退避与
     * due 半区语义等价（持久化推进退出工作集），remindTime 注入行为空集时退避自然空转。
     */
    void scanRemindTasksInCurrentSession() {
        int batchSize = getScanBatchSize();
        long budgetMs = getScanTimeBudgetMs();
        // R4-32（C7-006）：预算为纯相对计时域——单调缝取值，毫秒阈值换算纳秒
        long deadline = budgetMs <= 0 ? Long.MAX_VALUE : nowNanos() + TimeUnit.MILLISECONDS.toNanos(budgetMs);

        // W-01(a)（MA-06 残留面裁定 E）：remind 半区毒步骤连续失败轮次计数（与 due 半区
        // failCounts 同构，有界 cap 同款）
        Map<String, Integer> failCounts = new HashMap<>();
        // plan 2585 Phase 2：二元组游标 (remindTime, stepId)，与 due 半区同构同修
        Timestamp cursorTime = null;
        String cursorStepId = null;
        int scanned = 0;

        while (true) {
            if (nowNanos() >= deadline) {
                LOG.info("nop.wf.scheduler.scan-time-budget-exhausted:scanned={},budgetMs={}", scanned, budgetMs);
                break;
            }

            List<? extends IWorkflowStepRecord> batch =
                    workflowStore.findRemindActivatedSteps(batchSize, cursorTime, cursorStepId);
            if (batch == null || batch.isEmpty())
                break;

            for (IWorkflowStepRecord stepRecord : batch) {
                scanRemindTask(stepRecord, failCounts);
                scanned++;
            }

            if (batch.size() < batchSize)
                break;
            IWorkflowStepRecord last = batch.get(batch.size() - 1);
            if (last.getRemindTime() == null || last.getStepId() == null)
                break;
            cursorTime = last.getRemindTime();
            cursorStepId = last.getStepId();
        }
    }

    /**
     * 单条提醒处理：先抢占（MA-23 裁定⑥）再通知 listener；remindCount 经
     * incrementRemindCount 原子自增（DaoWorkflowStore 直发 COALESCE+1 UPDATE，消除
     * 双会话读-改-写丢失更新），不再经实体逐字段落盘。
     */
    private void scanRemindTask(IWorkflowStepRecord stepRecord, Map<String, Integer> failCounts) {
        String stepKey = stepRecord.getWfId() + "/" + stepRecord.getStepId();
        String holderId = resolveNodeId() + ":" + UUID.randomUUID();
        // R4-32（C7-006）：leaseUntil 为 DB 可比绝对时间——墙钟缝取值（绝对域保留 epoch）
        Timestamp leaseUntil = new Timestamp(nowMillis() + getScanLeaseMs());
        if (!workflowStore.tryClaimScanLease(stepRecord, holderId, leaseUntil)) {
            LOG.info("nop.wf.scheduler.skip-remind-task-scan-lease-lost:wfId={},stepId={}",
                    stepRecord.getWfId(), stepRecord.getStepId());
            return;
        }

        try {
            IWorkflowStep step = workflowManager.getWorkflow(stepRecord.getWfId()).getStepById(stepRecord.getStepId());
            reminderListeners.forEach(listener -> listener.onRemind(step));

            workflowStore.incrementRemindCount(stepRecord);
            // 成功路径退出失败计数（与 due 半区对称）
            failCounts.remove(stepKey);
        } catch (Exception e) {
            // W-01(a)（CRA 8.48 R4-08，MA-06 残留面裁定 E）：单条提醒失败不中断整批，但不再
            // 仅 LOG.error 滞留工作集每轮重扫——同构 due 半区毒步骤退避，remindTime 持久化推进
            // 退出本轮扫描集（与 due 半区收敛语义等价；修复前失败记录不退出工作集、每轮重放 ERROR）
            postponeFailedRemindTask(stepRecord, failCounts, stepKey, e);
        } finally {
            workflowStore.releaseScanLease(stepRecord, holderId);
        }
    }

    /**
     * W-01(a)（MA-06 残留面裁定 E）remind 半区毒步骤退避：remindTime 推进
     * now + min(60s × 2^n, 1h)（与 due 半区 postponeFailedDueTask 同构；退避载体=
     * remindTime 持久化推进——真正退出扫描集，否决内存 failCounts-only 抑制分支）。
     * 计数为扫描器内存同构 map（有界 cap=10000 整体清空 WARN 一次），重启归零按基准重启
     * 退避，可接受。
     */
    private void postponeFailedRemindTask(IWorkflowStepRecord stepRecord, Map<String, Integer> failCounts,
                                          String stepKey, Exception e) {
        LOG.error("nop.wf.scheduler.remind-task-failed:wfId={},stepId={}",
                stepRecord.getWfId(), stepRecord.getStepId(), e);

        if (failCounts.size() >= FAIL_COUNT_MAP_CAP) {
            LOG.warn("nop.wf.scheduler.remind-fail-count-map-cap-reached:cap={},map-cleared",
                    FAIL_COUNT_MAP_CAP);
            failCounts.clear();
        }
        int failures = failCounts.merge(stepKey, 1, Integer::sum);
        long backoff = Math.min(BACKOFF_BASE_MS * (1L << Math.min(failures - 1, BACKOFF_MAX_SHIFT)), BACKOFF_MAX_MS);
        // R4-32（C7-006）：remindTime 为 DB 可比绝对时间——墙钟缝取值
        Timestamp newRemindTime = new Timestamp(nowMillis() + backoff);
        // 失败退避路径显式可见（ERROR + 退避量留痕，非空 catch）
        LOG.warn("nop.wf.scheduler.remind-task-backoff-postpone:wfId={},stepId={},failures={},backoffMs={},newRemindTime={}",
                stepRecord.getWfId(), stepRecord.getStepId(), failures, backoff, newRemindTime);
        workflowStore.postponeStepRemindTime(stepRecord, newRemindTime);
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
