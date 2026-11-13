package io.nop.datav.service.entity;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.concurrent.semaphore.DefaultSemaphore;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;

import io.nop.datav.biz.INopDatavExportTaskBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.export.NopDatavExportTaskRecovery;
import io.nop.datav.service.export.NopDatavExportTaskStatus;
import io.nop.datav.service.export.PanelDataExporter;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_EXPORT_FILE_MAX_LENGTH;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_EXPORT_MAX_ROWS;
import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;
import static io.nop.datav.service.NopDatavErrors.ARG_CURRENT_CONCURRENT;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_FORMAT;
import static io.nop.datav.service.NopDatavErrors.ARG_MAX_CONCURRENT;
import static io.nop.datav.service.NopDatavErrors.ARG_SOURCE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_SOURCE_TYPE;
import static io.nop.datav.service.NopDatavErrors.ARG_TASK_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_USER_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_CONCURRENCY_LIMIT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_MISSING_SOURCE;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NOT_FINISHED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NOT_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_TASK_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;

/**
 * 数据导出任务 BizModel（D3-3）。提供面板/看板数据导出（CSV / xlsx）的异步任务管理：
 * 创建（继承 D3-1 来源权限 + 限额）→ 后台执行（复用 {@link PanelDataExporter}）→ 状态查询 → owner 下载。
 *
 * <p>异步执行：提交到 {@link GlobalExecutors#globalWorker()}，执行体内经 {@link IOrmTemplate#runInNewSession}
 * 开新 ORM session 写任务状态、调 {@link IFileStore#saveFile} 落盘。cancel 经 per-task 内存标志位，
 * 执行体轮询后主动中止。</p>
 */
@BizModel("NopDatavExportTask")
public class NopDatavExportTaskBizModel extends CrudBizModel<NopDatavExportTask>
        implements INopDatavExportTaskBiz {

    private static final Logger LOG_EXPORT_FAILURE = LoggerFactory.getLogger("nop.datav.export");

    public static final String SOURCE_PANEL = "panel";
    public static final String SOURCE_DASHBOARD = "dashboard";

    private static final String BIZ_OBJ_NAME = "nopDatavExportTask";
    private static final String DASHBOARD_AUTH_OBJ = "NopDatavDashboard";

    @Inject
    protected IJdbcTemplate jdbcTemplate;

    @Inject
    protected IOrmTemplate ormTemplate;

    @Inject
    protected IFileStore fileStore;

    @Inject
    protected NopDatavExportTaskRecovery recovery;

    /**
     * AR-2 修复（plan 2026-08-15-2146-2 Phase 2）：事务模板复用基类 {@code CrudBizModel}
     * 已注入的 {@code transactionTemplate}（经 {@link #txn()} 访问，不重复声明字段——子类重复声明
     * 会被 NopIoC 经基类 setter 注入绕过，留下 null 影子字段）。事务上下文内异步提交经
     * {@code afterCommit} 注册（worker 消费时 pending 行必然已提交可见）；无事务上下文
     * （cron/恢复/裸调用路径）保持立即提交。注册前经 {@code isTransactionOpened} 守护——
     * 无事务时注册 listener 直接抛 {@code ERR_TXN_NOT_IN_TRANSACTION}。
     */

    /**
     * per-task cancel 标志位：taskId → true（已请求取消）。执行体在取数/写出循环中轮询此 map。
     * 进程重启后丢失（由 {@link NopDatavExportTaskRecovery} 清理对应任务为 failed）。
     */
    private final ConcurrentMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 裁定⑨（plan 2526）：per-user 导出并发限额进程内信号量注册表（userId → ISemaphore）。
     * 条目数受平台用户数有界（非请求驱动的无界增长面）；进程重启随内存清零，
     * 与 NopDatavExportTaskRecovery 启动标 FAILED 语义自洽。
     */
    private final ConcurrentMap<String, ISemaphore> userExportSemaphores = new ConcurrentHashMap<>();

    /**
     * 测试 seam（plan 2526 红②接线断言面）：条件更新 0 行冲突发生计数（run/terminal/failed/cancel
     * 各 fenced 分支共用）。断言「fencing 真拦截」（rule 23）——冲突分支真实到达且可观测。
     */
    private final AtomicInteger fencedConflictCount = new AtomicInteger();

    int getFencedConflictCountForTest() {
        return fencedConflictCount.get();
    }

    /** AR-2 短退避重查参数：最多 3 次 × 200ms。 */
    private static final int TASK_ROW_MAX_RETRIES = 3;
    private static final long TASK_ROW_RETRY_INTERVAL_MS = 200L;

    /**
     * AR-2 测试 seam：{@link #submitExecution} 实际触发次数（afterCommit 回调内递增）。
     * 断言「提交仅在事务 commit 后触发 / 回滚不触发」的时序锚点（package-private，仅测试可见）。
     */
    private final AtomicInteger submitExecutionInvokedCount = new AtomicInteger();

    /**
     * 测试 seam（D4 方案 A）：执行体在 RUNNING 持久化之后、调用 exporter 之前同步触发此 hook。
     * 仅测试可见（package-private setter），生产路径为 null 不阻塞。
     * 设计理由：放在 RUNNING 之后、exporter 之前这一稳定位置（不受 Phase 2 两个 cancel checkpoint 移除影响），
     * 使 cancel-during-execution E2E 测试在「同时移除两个 checkpoint」时确定性失败（mutate-fail 保护力），
     * 而「仅移除其一」时另一个 checkpoint 兜底 → 测试通过（mutate-fail 精确声明）。
     */
    private Runnable executionStartHook;

    public NopDatavExportTaskBizModel() {
        setEntityName(NopDatavExportTask.class.getName());
    }

    // ==================== 自定义 action ====================

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavExportTask:createExportTask")
    public NopDatavExportTask createExportTask(@Name("sourceType") String sourceType,
                                                @Name("sourceId") String sourceId,
                                                @Name("format") String format,
                                                @Name("params") Map<String, Object> params,
                                                IServiceContext context) {
        if (StringHelper.isEmpty(sourceType) || StringHelper.isEmpty(sourceId)) {
            throw new NopException(ERR_DATAV_EXPORT_MISSING_SOURCE)
                    .param(ARG_SOURCE_TYPE, sourceType).param(ARG_SOURCE_ID, sourceId);
        }
        validateFormat(format, sourceType);

        // 来源权限：继承 D3-1 RLS（能访问看板/面板者方可发起导出）
        requireSourceAccess(sourceType, sourceId, "createExportTask", context);

        String operator = NopDatavOperatorResolver.resolveOperator(context);

        // 并发限额校验（请求线程同步）：进程内 per-user ISemaphore（裁定⑨原子层）+ DB count（跨节点近似层）
        checkConcurrencyLimit(operator);

        // 裁定⑨：限额许可自此持有至任务在途期结束；submit 前的任何失败在此释放（提交成功后由
        // submitExecution lambda 最外层 finally 统一释放——收口点上移，见裁定④⑨）。
        boolean submitted = false;
        try {
            // 注：恢复中断任务仅由 NopDatavExportTaskRecovery 的 @PostConstruct 在容器启动期执行一次。
            // 此前在请求路径调用 recovery.recoverInterruptedTasks() 会把所有其他用户/同用户的在途任务误标 FAILED
            // （audit Dim14-01）。请求路径不再触碰恢复逻辑。

            // INSERT pending 任务
            NopDatavExportTask task = newTaskEntity(sourceType, sourceId, format, params, operator);
            daoProvider().daoFor(NopDatavExportTask.class).saveEntityDirectly(task);

            // 提交异步执行（AR-2：事务上下文内经 afterCommit 注册，commit 后 worker 才消费）
            submitExecutionAfterCommit(task.getTaskId(), operator);
            submitted = true;

            // AR-2 配套：返回 detached 副本（镜像 Phase 1 share 模式）。afterCommit 语义下 worker 在
            // commit 后立即开跑（RUNNING/SUCCEEDED 连续推高 version），mutation 返回 attached 实体时
            // GraphQL 响应装载会对 stale version 实体重组装并触发 entity-version-changed；
            // detached 副本不挂 session，响应字段与修复前一致。
            return toCreatedTaskView(task);
        } finally {
            if (!submitted) {
                releaseExportPermit(operator);
            }
        }
    }

    /**
     * AR-2 配套：{@link #createExportTask} 出参 detached 副本（不挂 session、不影响持久层）。
     * 字段全集复制（无 mask 需求——导出任务无敏感字段），实体本体不动。
     */
    private static NopDatavExportTask toCreatedTaskView(NopDatavExportTask task) {
        NopDatavExportTask view = new NopDatavExportTask();
        view.setTaskId(task.getTaskId());
        view.setSourceType(task.getSourceType());
        view.setSourceId(task.getSourceId());
        view.setFormat(task.getFormat());
        view.setStatus(task.getStatus());
        view.setParams(task.getParams());
        view.setFileRecordId(task.getFileRecordId());
        view.setRowCount(task.getRowCount());
        view.setErrorMsg(task.getErrorMsg());
        view.setDelFlag(task.getDelFlag());
        view.setVersion(task.getVersion());
        view.setCreatedBy(task.getCreatedBy());
        view.setCreateTime(task.getCreateTime());
        view.setUpdatedBy(task.getUpdatedBy());
        view.setUpdateTime(task.getUpdateTime());
        return view;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavExportTask:getExportTask")
    public NopDatavExportTask getExportTask(@Name("taskId") String taskId, IServiceContext context) {
        NopDatavExportTask task = requireTask(taskId);
        requireOwner(task, context);
        return task;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavExportTask:cancelExportTask")
    public NopDatavExportTask cancelExportTask(@Name("taskId") String taskId, IServiceContext context) {
        NopDatavExportTask task = requireTask(taskId);
        requireOwner(task, context);
        if (NopDatavExportTaskStatus.isTerminal(task.getStatus())) {
            return task;
        }
        // 裁定④（plan 2526）：putIfAbsent 防并发 cancel 双写标志；put 成功后 CANCELLED 终态写并入
        // 裁定③同族条件更新（同文件禁两套 fencing）。写失败（0 行）= 任务已被 worker/scanner 定终态
        // → 补偿摘除自置标志（putIfAbsent 保证只摘自置项）+ fresh 重载返回当前终态（cancel 输给完成）。
        boolean flagOwner = cancelFlags.putIfAbsent(taskId, Boolean.TRUE) == null;
        boolean written = false;
        try {
            task.setStatus(NopDatavExportTaskStatus.CANCELLED);
            task.setErrorMsg("cancelled by user");
            touchUpdate(task, NopDatavOperatorResolver.resolveOperator(context));
            // pending 直接转 cancelled；running 由执行体轮询标志位后转，这里也即时落库
            written = writeWithFencing(daoProvider().daoFor(NopDatavExportTask.class), task);
            if (!written) {
                LOG_EXPORT_FAILURE.warn("nop.datav.export.cancel-write-conflict:taskId={} "
                        + "(task already terminal by worker/scanner; cancel loses to completion)", taskId);
                return requireTask(taskId);
            }
            return task;
        } finally {
            if (flagOwner && !written) {
                // 补偿摘除：CAS 失败或写程异常均不得残留标志（残留兜底，防 map 单调增长）
                cancelFlags.remove(taskId);
            }
        }
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavExportTask:downloadExportFile")
    public IResource downloadExportFile(@Name("taskId") String taskId, IServiceContext context) {
        NopDatavExportTask task = requireTask(taskId);
        requireOwner(task, context);
        if (task.getStatus() != NopDatavExportTaskStatus.SUCCEEDED
                || StringHelper.isEmpty(task.getFileRecordId())) {
            throw new NopException(ERR_DATAV_EXPORT_NOT_FINISHED).param(ARG_TASK_ID, taskId);
        }
        IFileRecord record = fileStore.getFile(task.getFileRecordId());
        return record.getResource();
    }

    // ==================== 异步执行 ====================

    /**
     * AR-2 修复：异步提交时序——事务上下文内（{@code @BizMutation} 经 GraphQL 事务装饰器）
     * 经 {@code afterCommit} 注册 {@link #submitExecution}，pending 行 commit 后 worker 才消费，
     * 消除 READ_COMMITTED 下「worker 首查 null → 静默 return」竞态；事务回滚时 onAfterCommit
     * 不触发（语义恰好正确：回滚则不提交异步任务）。无事务上下文（cron/恢复/裸调用）保持
     * 立即提交——注册前经 {@code isTransactionOpened} 守护，避免
     * {@code ERR_TXN_NOT_IN_TRANSACTION}。
     */
    private void submitExecutionAfterCommit(String taskId, String operator) {
        ITransactionTemplate transactionTemplate = txn();
        if (transactionTemplate != null && transactionTemplate.isTransactionOpened(null)) {
            transactionTemplate.afterCommit(null, () -> submitExecution(taskId, operator));
        } else {
            submitExecution(taskId, operator);
        }
    }

    private void submitExecution(String taskId, String operator) {
        submitExecutionInvokedCount.incrementAndGet();
        try {
            GlobalExecutors.globalWorker().submit(() -> {
                try {
                    ormTemplate.runInNewSession(session -> executeTask(session, taskId, operator));
                } catch (Exception e) {
                    // executeTask 内部已捕获业务异常并落库 failed；此处仅兜底处理 session 基础设施故障
                    LOG_EXPORT_FAILURE.error("nop.datav.export.session-fail:taskId={}", taskId, e);
                    markFailedSafe(taskId, "async session error: " + safeMsg(e));
                } finally {
                    // 裁定④/⑨收口点上移（plan 2526）：取消标志与限额许可的权威清理点=提交 lambda
                    // 最外层 finally——覆盖 executeTask 全部退出路径（正常完成/加载失败早退/cancel-
                    // 先于-RUNNING 早退/基建故障）与 executeTask try 域 finally（原位保留为幂等冗余）。
                    cancelFlags.remove(taskId);
                    releaseExportPermit(operator);
                }
                return null;
            });
        } catch (Exception e) {
            // 提交失败（线程池拒绝等）：同步收口 + 显式失败（无静默跳过）
            LOG_EXPORT_FAILURE.error("nop.datav.export.submit-rejected:taskId={}", taskId, e);
            cancelFlags.remove(taskId);
            releaseExportPermit(operator);
            markFailedSafe(taskId, "export submit rejected: " + safeMsg(e));
        }
    }

    private Void executeTask(IOrmSession session, String taskId, String operator) {
        IEntityDao<NopDatavExportTask> dao = daoProvider().daoFor(NopDatavExportTask.class);
        NopDatavExportTask task = waitForTaskRow(dao, taskId);
        if (task == null) {
            // AR-2 可观测性：禁止静默 no-op。正常情况下 afterCommit 时序保证行必然可见；
            // 此分支只剩基础设施异常（如读副本延迟）——短退避重查后仍缺行则显式失败。
            LOG_EXPORT_FAILURE.error(
                    "nop.datav.export.task-row-not-visible-after-retries:taskId={} (worker cannot load pending row)",
                    taskId);
            markFailedSafe(taskId, "export task row not visible to worker after retries");
            return null;
        }
        // 终态短路（裁定③ face b 修复）：加载即终态（scanner/recovery 已抢先定终态）→ 放弃执行，
        // 禁把 FAILED 等终态"复活"为 RUNNING。
        if (NopDatavExportTaskStatus.isTerminal(task.getStatus())) {
            LOG_EXPORT_FAILURE.info("nop.datav.export.terminal-short-circuit:taskId={},status={} "
                    + "(row already terminal; abandon execution)", taskId, task.getStatus());
            return null;
        }
        // pending → running
        if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
            markCancelled(dao, task, operator);
            return null;
        }
        task.setStatus(NopDatavExportTaskStatus.RUNNING);
        touchUpdate(task, operator);
        if (!writeWithFencing(dao, task)) {
            // 0 行 = scanner/cancel 已推进该行（假阳性标记粘滞语义）→ 放弃重活（终态归抢先写者）
            fencedConflictCount.incrementAndGet();
            LOG_EXPORT_FAILURE.warn("nop.datav.export.running-write-conflict:taskId={} "
                    + "(row advanced concurrently; abandon execution)", taskId);
            return null;
        }

        // D4 测试 seam：RUNNING 持久化之后、exporter 之前同步触发（仅测试用，生产路径 hook=null 跳过）。
        // 选此稳定位置而非 exporter 内 checkpoint，避免 mutate-fail 时（移除 exporter checkpoint）test seam
        // 同时被移除导致测试无法同步。
        if (executionStartHook != null) {
            executionStartHook.run();
        }

        // 裁定⑤：saveExportFile 成功后任何不落 fileRecordId 关联的出口一律回收（不变式：
        // fileId 不被任何可达持久化行引用）。fileId 提升到 try 外以便 catch 分支可见。
        String fileId = null;

        // D2 §312：执行体内经 BooleanSupplier 轮询 cancelFlags，命中时 exporter 抛 ERR_DATAV_EXPORT_FAILED
        BooleanSupplier cancelChecker = () -> Boolean.TRUE.equals(cancelFlags.get(taskId));

        try {
            PanelDataExporter exporter = new PanelDataExporter(daoProvider(), jdbcTemplate);
            int maxRows = CFG_DATAV_EXPORT_MAX_ROWS.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> params = task.getParams() == null
                    ? null : JsonTool.parseMap(task.getParams());

            PanelDataExporter.ExportFile file;
            if (SOURCE_DASHBOARD.equalsIgnoreCase(task.getSourceType())) {
                file = exporter.exportDashboard(task.getSourceId(), params, maxRows, cancelChecker);
            } else {
                NopDatavPanel panel = daoProvider().daoFor(NopDatavPanel.class)
                        .getEntityById(task.getSourceId());
                if (panel == null) {
                    throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", task.getSourceId());
                }
                file = exporter.exportPanel(panel, params, task.getFormat(), maxRows, cancelChecker);
            }

            // 落盘
            fileId = saveExportFile(file, taskId);

            // D2 §312：pre-SUCCEEDED 确定性兜底点——cancel 线程已写 CANCELLED 时跳过 SUCCEEDED 写入，
            // 保留 cancel 线程的终态（errorMsg 已由 cancel 线程写入 "cancelled by user"）。
            // 裁定⑤（plan 2526）：已落盘 fileId 不被任何可达持久化行引用 → 一律回收（防孤儿）。
            if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
                LOG_EXPORT_FAILURE.warn("nop.datav.export.cancelled-pre-success:taskId={},fileId={} "
                        + "(reclaiming unreferenced export file)", taskId, fileId);
                reclaimExportFile(fileId, taskId, "cancel-pre-succeeded");
                return null;
            }

            task.setStatus(NopDatavExportTaskStatus.SUCCEEDED);
            task.setFileRecordId(fileId);
            task.setRowCount(file.getRowCount());
            task.setErrorMsg(null);
            touchUpdate(task, operator);
            if (!writeWithFencing(dao, task)) {
                // 裁定③ 0 行放弃分支（R1 Major-1 合流裁定⑤）：已被 cancel/scanner 抢先定终态，
                // fileId 不落 fileRecordId 关联 → 回收（防孤儿），冲突结构化可见。
                fencedConflictCount.incrementAndGet();
                LOG_EXPORT_FAILURE.warn("nop.datav.export.terminal-write-conflict:taskId={},fileId={} "
                        + "(fenced SUCCEEDED write 0-row; reclaiming unreferenced export file)", taskId, fileId);
                reclaimExportFile(fileId, taskId, "succeeded-write-conflict");
                return null;
            }
        } catch (Exception e) {
            Throwable reason = NopException.adapt(e);
            // D2 §312：catch 分流必须在 setStatus(FAILED) 之前判定 cancelFlags
            if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
                // cancel 命中：status 已由 cancel 线程写为 CANCELLED，执行体不覆盖、不写 FAILED；
                // errorMsg 已由 cancel 线程写入 "cancelled by user"。仅记录日志。
                LOG_EXPORT_FAILURE.info("nop.datav.export.cancelled-in-flight:taskId={}", taskId);
                if (fileId != null) {
                    reclaimExportFile(fileId, taskId, "cancelled-in-flight-after-save");
                }
            } else {
                task.setStatus(NopDatavExportTaskStatus.FAILED);
                task.setErrorMsg(safeMsg(reason));
                touchUpdate(task, operator);
                if (!writeWithFencing(dao, task)) {
                    fencedConflictCount.incrementAndGet();
                    LOG_EXPORT_FAILURE.warn("nop.datav.export.failed-write-conflict:taskId={} "
                            + "(row already terminal; keep winner state)", taskId);
                }
                if (fileId != null) {
                    // FAILED 行不写 fileRecordId → 已落盘文件不可达 → 回收（防孤儿）
                    reclaimExportFile(fileId, taskId, "failed-after-save");
                }
                if (reason instanceof NopException) {
                    LOG_EXPORT_FAILURE.warn("nop.datav.export.task-failed:taskId={}", taskId, reason);
                }
            }
        } finally {
            // 原位幂等冗余（裁定④收口点上移后保留）：权威清理点=submitExecution lambda 最外层 finally
            cancelFlags.remove(taskId);
        }
        return null;
    }

    /**
     * AR-2 短退避重查：worker 新 session 首查 miss 时按 {@link #TASK_ROW_RETRY_INTERVAL_MS}
     * 间隔重查至多 {@link #TASK_ROW_MAX_RETRIES} 次。dao 的 miss 不落 session 缓存，
     * 每次重查都直达数据库。
     */
    private static NopDatavExportTask waitForTaskRow(IEntityDao<NopDatavExportTask> dao, String taskId) {
        NopDatavExportTask task = dao.getEntityById(taskId);
        for (int i = 0; i < TASK_ROW_MAX_RETRIES && task == null; i++) {
            try {
                Thread.sleep(TASK_ROW_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            task = dao.getEntityById(taskId);
        }
        return task;
    }

    private String saveExportFile(PanelDataExporter.ExportFile file, String taskId) {
        IResource resource = file.getResource();
        long length = resource.length() > 0 ? resource.length() : file.getRowCount();
        InputStream is = null;
        try {
            is = resource.getInputStream();
            UploadRequestBean bean = new UploadRequestBean(
                    is, file.getFileName(), length, file.getMimeType());
            bean.setBizObjName(BIZ_OBJ_NAME);
            bean.setBizObjId(taskId);
            return fileStore.saveFile(bean, CFG_DATAV_EXPORT_FILE_MAX_LENGTH.get());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    LOG_EXPORT_FAILURE.debug("nop.datav.export.close-stream-fail", e);
                }
            }
            // 删除临时写出资源（nop-file 已持久化）
            try {
                resource.delete();
            } catch (Exception e) {
                LOG_EXPORT_FAILURE.debug("nop.datav.export.delete-temp-fail", e);
            }
        }
    }

    private void markCancelled(IEntityDao<NopDatavExportTask> dao, NopDatavExportTask task, String operator) {
        task.setStatus(NopDatavExportTaskStatus.CANCELLED);
        if (StringHelper.isEmpty(task.getErrorMsg())) {
            task.setErrorMsg("cancelled by user");
        }
        touchUpdate(task, operator);
        if (!writeWithFencing(dao, task)) {
            LOG_EXPORT_FAILURE.warn("nop.datav.export.cancel-mark-conflict:taskId={} "
                    + "(row already terminal; keep winner state)", task.getTaskId());
        }
    }

    private void markFailedSafe(String taskId, String reason) {
        try {
            ormTemplate.runInNewSession(session -> {
                IEntityDao<NopDatavExportTask> dao = daoProvider().daoFor(NopDatavExportTask.class);
                NopDatavExportTask task = dao.getEntityById(taskId);
                if (task != null && !NopDatavExportTaskStatus.isTerminal(task.getStatus())) {
                    task.setStatus(NopDatavExportTaskStatus.FAILED);
                    task.setErrorMsg(reason);
                    touchUpdate(task, "system");
                    if (!writeWithFencing(dao, task)) {
                        LOG_EXPORT_FAILURE.warn("nop.datav.export.mark-failed-conflict:taskId={} "
                                + "(row advanced concurrently; keep winner state)", taskId);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            // 兜底路径：session 基础设施本身故障时无法再写库，记录日志（已尽最大努力，非静默吞异常）
            LOG_EXPORT_FAILURE.error("nop.datav.export.mark-failed-fail:taskId={}", taskId, e);
        }
    }

    // ==================== 权限/限额校验 ====================

    private void validateFormat(String format, String sourceType) {
        if (format == null) {
            throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED).param(ARG_FORMAT, "null");
        }
        String f = format.toLowerCase();
        if ("pdf".equals(f) || "png".equals(f) || "jpg".equals(f) || "image".equals(f)) {
            throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED).param(ARG_FORMAT, format);
        }
        if (!PanelDataExporter.FORMAT_CSV.equals(f) && !PanelDataExporter.FORMAT_XLSX.equals(f)) {
            throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED).param(ARG_FORMAT, format);
        }
        // 看板级仅支持 xlsx（csv 单文件仅支持单面板）
        if (SOURCE_DASHBOARD.equalsIgnoreCase(sourceType) && PanelDataExporter.FORMAT_CSV.equals(f)) {
            throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED).param(ARG_FORMAT, format);
        }
    }

    /**
     * 来源权限：继承 D3-1 RLS。对来源 panel/dashboard 校验当前用户经数据权限可访问，
     * 失败抛 ERR_AUTH_NO_DATA_AUTH。dashboard 直接校验；panel 经其所属 dashboard 校验。
     */
    private void requireSourceAccess(String sourceType, String sourceId, String action, IServiceContext context) {
        String dashboardId;
        if (SOURCE_PANEL.equalsIgnoreCase(sourceType)) {
            NopDatavPanel panel = daoProvider().daoFor(NopDatavPanel.class).getEntityById(sourceId);
            if (panel == null) {
                throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", sourceId);
            }
            dashboardId = panel.getDashboardId();
        } else if (SOURCE_DASHBOARD.equalsIgnoreCase(sourceType)) {
            dashboardId = sourceId;
        } else {
            throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED)
                    .param(ARG_SOURCE_TYPE, sourceType);
        }
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_DASHBOARD_NOT_FOUND).param(ARG_DASHBOARD_ID, dashboardId);
        }
        IDataAuthChecker checker = context == null ? null : context.getDataAuthChecker();
        if (checker == null) {
            return;
        }
        if (!checker.isPermitted(DASHBOARD_AUTH_OBJ, action, dashboard, context)) {
            throw new NopException(ERR_AUTH_NO_DATA_AUTH).param(ARG_BIZ_OBJ_NAME, DASHBOARD_AUTH_OBJ);
        }
    }

    private void checkConcurrencyLimit(String operator) {
        int max = CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER.get();
        if (max <= 0) {
            return;
        }
        // 裁定⑨（plan 2026）：进程内 per-user ISemaphore 原子层——tryAcquire 原子性使同用户并发
        // 提交无法越过限额（check-then-act TOCTOU 闭合）；许可由 submitExecution lambda 最外层
        // finally（或 createExportTask 提交前失败 finally）释放。
        ISemaphore semaphore = userExportSemaphores.computeIfAbsent(operator,
                k -> new DefaultSemaphore(max));
        if (!semaphore.tryAcquire(1, 0L)) {
            throw new NopException(ERR_DATAV_EXPORT_CONCURRENCY_LIMIT)
                    .param(ARG_CURRENT_CONCURRENT, (long) max)
                    .param(ARG_MAX_CONCURRENT, max);
        }
        // DB count 校验保持为跨节点近似第二层（信号量赢则 DB 层不触发）
        try {
            IEntityDao<NopDatavExportTask> dao = daoProvider().daoFor(NopDatavExportTask.class);
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("createdBy", operator));
            query.addFilter(FilterBeans.in("status", java.util.Arrays.asList(
                    NopDatavExportTaskStatus.PENDING, NopDatavExportTaskStatus.RUNNING)));
            long current = dao.countByQuery(query);
            if (current >= max) {
                throw new NopException(ERR_DATAV_EXPORT_CONCURRENCY_LIMIT)
                        .param(ARG_CURRENT_CONCURRENT, current)
                        .param(ARG_MAX_CONCURRENT, max);
            }
        } catch (Exception e) {
            releaseExportPermit(operator);
            throw e;
        }
    }

    private void releaseExportPermit(String operator) {
        ISemaphore semaphore = userExportSemaphores.get(operator);
        if (semaphore != null) {
            semaphore.release(1);
        }
    }

    /**
     * 裁定③统一条件更新形态（plan 2526）：状态行写点全部经本方法——
     * {@code orm_disableVersionCheckError(true)} 使 ORM 生成 UPDATE 恒带 {@code AND version=?}
     * 隐式 CAS、0 行结果降级为 readonly 标记（平台原生条件更新形态，2525 elector 先例）。
     *
     * @return true=写入成功；false=0 行冲突（已有并发写者推进版本，调用方按放弃语义处置）
     */
    private boolean writeWithFencing(IEntityDao<NopDatavExportTask> dao, NopDatavExportTask task) {
        task.orm_disableVersionCheckError(true);
        dao.updateEntityDirectly(task);
        return !task.orm_readonly();
    }

    /**
     * 裁定⑤（plan 2526，Option A 模块内回收）：导出产物回收——镜像 DaoResourceFileStore.detachFileInSession
     * 的「行删→物删」序列（daoFor(NopFileRecord) 删行 + fileStore.getFile().getResource() 删物理文件）。
     * 导出产物 originFileId=fileId 唯一引用、无共享引用删除安全。
     * 回收失败显式 WARN + path/fileId 结构化日志（不抛、不重试——对账兜底归 Non-Blocking Follow-up，
     * 不引入 removeResource 修复域新语义）。
     */
    private void reclaimExportFile(String fileId, String taskId, String phase) {
        if (StringHelper.isEmpty(fileId)) {
            return;
        }
        try {
            NopFileRecord row = daoProvider().daoFor(NopFileRecord.class).getEntityById(fileId);
            if (row != null) {
                daoProvider().daoFor(NopFileRecord.class).deleteEntity(row);
            }
            IFileRecord fileRecord = fileStore.getFile(fileId);
            IResource resource = fileRecord == null ? null : fileRecord.getResource();
            if (resource != null && resource.exists() && !resource.delete()) {
                LOG_EXPORT_FAILURE.warn("nop.datav.export.reclaim-file-delete-fail:fileRecordId={},path={},taskId={},phase={}",
                        fileId, resource.getPath(), taskId, phase);
            }
        } catch (Exception e) {
            LOG_EXPORT_FAILURE.warn("nop.datav.export.reclaim-fail:fileRecordId={},taskId={},phase={}",
                    fileId, taskId, phase, e);
        }
    }

    private NopDatavExportTask requireTask(String taskId) {
        NopDatavExportTask task = daoProvider().daoFor(NopDatavExportTask.class).getEntityById(taskId);
        if (task == null) {
            throw new NopException(ERR_DATAV_EXPORT_TASK_NOT_FOUND).param(ARG_TASK_ID, taskId);
        }
        return task;
    }

    private void requireOwner(NopDatavExportTask task, IServiceContext context) {
        String userName = NopDatavOperatorResolver.resolveOperator(context);
        if (task.getCreatedBy() == null || !task.getCreatedBy().equals(userName)) {
            throw new NopException(ERR_DATAV_EXPORT_NOT_OWNER)
                    .param(ARG_USER_NAME, userName)
                    .param(ARG_TASK_ID, task.getTaskId());
        }
    }

    // ==================== helpers ====================

    private static final String ARG_DASHBOARD_ID_FOR_ACCESS = "dashboardId";

    private NopDatavExportTask newTaskEntity(String sourceType, String sourceId, String format,
                                             Map<String, Object> params, String operator) {
        Timestamp now = CoreMetrics.currentTimestamp();
        NopDatavExportTask task = new NopDatavExportTask();
        task.setTaskId(StringHelper.generateUUID());
        task.setSourceType(sourceType.toLowerCase());
        task.setSourceId(sourceId);
        task.setFormat(format.toLowerCase());
        task.setStatus(NopDatavExportTaskStatus.PENDING);
        task.setParams(params == null ? null : JsonTool.stringify(params));
        task.setDelFlag((byte) 0);
        task.setVersion(0L);
        task.setCreatedBy(operator);
        task.setCreateTime(now);
        task.setUpdatedBy(operator);
        task.setUpdateTime(now);
        return task;
    }

    private void touchUpdate(NopDatavExportTask task, String operator) {
        task.setUpdatedBy(operator);
        task.setUpdateTime(CoreMetrics.currentTimestamp());
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    /**
     * 供测试/管理直接触发重启清理。
     */
    public void recoverInterruptedTasks() {
        recovery.recoverInterruptedTasks();
    }

    /**
     * 供测试断言 cancel 标志位设置（内部状态可见性）。
     */
    boolean isCancelFlagged(String taskId) {
        return Boolean.TRUE.equals(cancelFlags.get(taskId));
    }

    /**
     * D4 方案 A 测试 seam：在 RUNNING 持久化之后、exporter 调用之前同步触发此 hook。
     * 仅测试可见（package-private）。生产路径不调用此方法（hook=null）。
     */
    void setExecutionStartHookForTest(Runnable hook) {
        this.executionStartHook = hook;
    }

    /**
     * AR-2 测试 seam 读取：{@link #submitExecution} 实际触发次数（afterCommit 回调内递增）。
     * 断言「注册后未触发（事务内）/ commit 后触发 / 回滚不触发」的时序锚点（package-private，仅测试可见）。
     */
    int getSubmitExecutionInvokedCountForTest() {
        return submitExecutionInvokedCount.get();
    }

    /**
     * 返回当前配置的最大行数（供测试断言配置生效）。
     */
    public int getMaxRowsConfig() {
        return CFG_DATAV_EXPORT_MAX_ROWS.get();
    }

    /**
     * 提供给外部读取 ORM 模板（测试辅助）。
     */
    public IOrmTemplate getOrmTemplate() {
        return ormTemplate;
    }
}
