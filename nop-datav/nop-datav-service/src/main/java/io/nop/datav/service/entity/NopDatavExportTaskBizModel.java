package io.nop.datav.service.entity;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
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
     * per-task cancel 标志位：taskId → true（已请求取消）。执行体在取数/写出循环中轮询此 map。
     * 进程重启后丢失（由 {@link NopDatavExportTaskRecovery} 清理对应任务为 failed）。
     */
    private final ConcurrentMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

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

        // 并发限额校验（请求线程同步）
        checkConcurrencyLimit(operator);

        // 注：恢复中断任务仅由 NopDatavExportTaskRecovery 的 @PostConstruct 在容器启动期执行一次。
        // 此前在请求路径调用 recovery.recoverInterruptedTasks() 会把所有其他用户/同用户的在途任务误标 FAILED
        // （audit Dim14-01）。请求路径不再触碰恢复逻辑。

        // INSERT pending 任务
        NopDatavExportTask task = newTaskEntity(sourceType, sourceId, format, params, operator);
        daoProvider().daoFor(NopDatavExportTask.class).saveEntityDirectly(task);

        // 提交异步执行
        submitExecution(task.getTaskId(), operator);

        return task;
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
        cancelFlags.put(taskId, Boolean.TRUE);
        // pending 直接转 cancelled；running 由执行体轮询标志位后转，这里也即时落库
        task.setStatus(NopDatavExportTaskStatus.CANCELLED);
        task.setErrorMsg("cancelled by user");
        touchUpdate(task, NopDatavOperatorResolver.resolveOperator(context));
        daoProvider().daoFor(NopDatavExportTask.class).updateEntityDirectly(task);
        return task;
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

    private void submitExecution(String taskId, String operator) {
        GlobalExecutors.globalWorker().submit(() -> {
            try {
                ormTemplate.runInNewSession(session -> executeTask(session, taskId, operator));
            } catch (Exception e) {
                // executeTask 内部已捕获业务异常并落库 failed；此处仅兜底处理 session 基础设施故障
                LOG_EXPORT_FAILURE.error("nop.datav.export.session-fail:taskId={}", taskId, e);
                markFailedSafe(taskId, "async session error: " + safeMsg(e));
            }
            return null;
        });
    }

    private Void executeTask(IOrmSession session, String taskId, String operator) {
        IEntityDao<NopDatavExportTask> dao = daoProvider().daoFor(NopDatavExportTask.class);
        NopDatavExportTask task = dao.getEntityById(taskId);
        if (task == null) {
            return null;
        }
        // pending → running
        if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
            markCancelled(dao, task, operator);
            return null;
        }
        task.setStatus(NopDatavExportTaskStatus.RUNNING);
        touchUpdate(task, operator);
        dao.updateEntityDirectly(task);

        // D4 测试 seam：RUNNING 持久化之后、exporter 之前同步触发（仅测试用，生产路径 hook=null 跳过）。
        // 选此稳定位置而非 exporter 内 checkpoint，避免 mutate-fail 时（移除 exporter checkpoint）test seam
        // 同时被移除导致测试无法同步。
        if (executionStartHook != null) {
            executionStartHook.run();
        }

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
            String fileId = saveExportFile(file, taskId);

            // D2 §312：pre-SUCCEEDED 确定性兜底点——cancel 线程已写 CANCELLED 时跳过 SUCCEEDED 写入，
            // 保留 cancel 线程的终态（errorMsg 已由 cancel 线程写入 "cancelled by user"）。
            if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
                LOG_EXPORT_FAILURE.info("nop.datav.export.cancelled-pre-success:taskId={}", taskId);
                return null;
            }

            task.setStatus(NopDatavExportTaskStatus.SUCCEEDED);
            task.setFileRecordId(fileId);
            task.setRowCount(file.getRowCount());
            task.setErrorMsg(null);
            touchUpdate(task, operator);
            dao.updateEntityDirectly(task);
        } catch (Exception e) {
            Throwable reason = NopException.adapt(e);
            // D2 §312：catch 分流必须在 setStatus(FAILED) 之前判定 cancelFlags
            if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {
                // cancel 命中：status 已由 cancel 线程写为 CANCELLED，执行体不覆盖、不写 FAILED；
                // errorMsg 已由 cancel 线程写入 "cancelled by user"。仅记录日志。
                LOG_EXPORT_FAILURE.info("nop.datav.export.cancelled-in-flight:taskId={}", taskId);
            } else {
                task.setStatus(NopDatavExportTaskStatus.FAILED);
                task.setErrorMsg(safeMsg(reason));
                touchUpdate(task, operator);
                dao.updateEntityDirectly(task);
                if (reason instanceof NopException) {
                    LOG_EXPORT_FAILURE.warn("nop.datav.export.task-failed:taskId={}", taskId, reason);
                }
            }
        } finally {
            cancelFlags.remove(taskId);
        }
        return null;
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
        dao.updateEntityDirectly(task);
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
                    dao.updateEntityDirectly(task);
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
        Timestamp now = new Timestamp(System.currentTimeMillis());
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
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
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
