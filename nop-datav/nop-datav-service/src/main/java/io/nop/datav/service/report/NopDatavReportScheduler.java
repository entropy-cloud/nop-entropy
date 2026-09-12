package io.nop.datav.service.report;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.datav.dao.entity.NopDatavReportTask;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时报告 cron 调度器（D5-1）。
 *
 * <p>普通 IoC bean（非 {@code @BizModel}），镜像 {@code MetaQualityCheckpointScheduler}
 * （{@code nop-metadata-service}）的调度集成范式：</p>
 * <ol>
 *   <li><b>启动 scanner</b>：{@link #init()} 读所有 {@code status=ENABLED} 报告任务，逐个经
 *       {@link IJobScheduler#addJob} 注册 cron job。单任务注册失败 try/catch 隔离，不抛崩启动。</li>
 *   <li><b>运行时增量</b>：{@link #registerTask(String)} / {@link #unregisterTask(String)} 供
 *       BizModel save/enable/disable/delete 调用，使配置变更即时生效。</li>
 *   <li><b>调用入口包装</b>：{@link #executeScheduledReport(Map)} 经 {@code beanMethod} invoker 调用，
 *       内部委托 {@link ReportDeliveryExecutor#execute}。</li>
 * </ol>
 *
 * <p><b>{@code IJobScheduler} 可空注入</b>：宿主 app 未注册调度器时 {@code scheduler == null}，
 * scanner/注册/触发全部显式跳过（INFO 日志，不抛崩）。手动 {@code triggerReportNow} 仍可用。</p>
 *
 * <p><b>LocalJobScheduler FAILED-brick 约定（schedule-report-design.md §3/§9）</b>：
 * {@link #executeScheduledReport} 必须 <b>吞业务错误返回正常结果对象</b>（catch 业务异常 → 记 delivery failed →
 * 返回带 error 信息的正常结果，不抛）。否则 invoker 转 {@code JobFireResult.ERROR} 后
 * LocalJobScheduler 将 job 永久置 FAILED（{@code addJob(allowUpdate=true)} 仅对 WAITING/SUSPENDED 重排程，
 * FAILED 不复活，唯一恢复手段是重启 JVM）。仅基础设施错误（{@code Error}）才抛。</p>
 *
 * <p><b>职责分离</b>：本 bean 只负责 cron job 注册/注销；stale running 交付记录的清理由独立
 * {@link NopDatavReportDeliveryRecovery} 负责（镜像 {@code NopDatavExportTaskRecovery} 模式）。</p>
 */
public class NopDatavReportScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavReportScheduler.class);

    /** jobName 前缀，避免与其它模块 job 冲突；后缀 reportTaskId。 */
    public static final String JOB_NAME_PREFIX = "nop-datav-report-";

    /** beanMethod invoker 约定：jobParams 中的 bean 名键 */
    static final String PARAM_BEAN_NAME = "beanName";
    /** beanMethod invoker 约定：jobParams 中的方法名键 */
    static final String PARAM_METHOD_NAME = "methodName";
    /** jobParams 中传递给包装方法的报告任务 ID 键 */
    static final String PARAM_REPORT_TASK_ID = "reportTaskId";

    /** 本 bean 在 IoC 容器中的注册名（与 app-service.beans.xml 一致） */
    public static final String BEAN_NAME = "nopDatavReportScheduler";
    /** beanMethod 调用的方法名 */
    public static final String SCHEDULED_METHOD_NAME = "executeScheduledReport";

    /** jobParams 中传递 scheduledFireTime 的键（用于 grace 检查） */
    static final String PARAM_SCHEDULED_FIRE_TIME = "scheduledFireTime";

    private IJobScheduler scheduler;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;
    private ReportDeliveryExecutor reportDeliveryExecutor;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setReportDeliveryExecutor(ReportDeliveryExecutor reportDeliveryExecutor) {
        this.reportDeliveryExecutor = reportDeliveryExecutor;
    }

    /**
     * 注入 {@link IJobScheduler}（{@code @Nullable}——宿主未注册调度器时不注入，scanner/注册/触发全部跳过）。
     * 生产 runtime 由宿主 app 经 {@code app-local-scheduler.beans.xml} 提供；仅测试需 test-scope 引入
     * {@code nop-job-local}。
     */
    @Inject
    public void setScheduler(@Nullable IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 启动 scanner：读所有 {@code status=ENABLED} 报告任务，非空合法 cron 则注册。单任务失败 try/catch 隔离。
     *
     * <p><b>表存在性守卫</b>：查询前先经 {@link IJdbcTemplate#existsTable} 判定表是否已建，
     * 避免在测试环境 schema 尚未建表阶段（容器 init 早于 @BeforeEach 建表）误报。
     * 镜像 {@code NopDatavExportTaskRecovery} 模式。</p>
     *
     * <p><b>activate 顺序</b>：{@code activate()} 在表检查之前调用——即使表未建（测试环境 IoC 启动早于
     * {@code @BeforeEach} 建表），调度器仍激活，后续运行时 {@link #registerTask} 的 {@code addJob} 可用。</p>
     */
    @PostConstruct
    public void init() {
        if (scheduler == null) {
            LOG.info("nop.datav.report-scheduler.no-scheduler: cron scheduling disabled (host app did not register IJobScheduler)");
            return;
        }
        // 防御性 activate：activate() 幂等（仅置 active=true），先激活以保证后续 addJob 可用
        scheduler.activate();
        if (!reportTaskTableExists()) {
            LOG.info("nop.datav.report-scheduler.table-not-exists: skip init scanner (table NOP_DATAV_REPORT_TASK not yet created)");
            return;
        }
        IEntityDao<NopDatavReportTask> dao = daoProvider.daoFor(NopDatavReportTask.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopDatavReportTask.PROP_NAME_status,
                NopDatavReportTaskStatus.ENABLED));
        @SuppressWarnings("unchecked")
        List<NopDatavReportTask> active = (List<NopDatavReportTask>) dao.findAllByQuery(q);
        int registered = 0;
        for (NopDatavReportTask task : active) {
            try {
                if (doRegister(task)) {
                    registered++;
                }
            } catch (Exception e) {
                LOG.error("nop.datav.report-scheduler.register-failed: reportTaskId={}",
                        task.getReportTaskId(), e);
            }
        }
        LOG.info("nop.datav.report-scheduler.init-done: activeTasks={} registered={}",
                active.size(), registered);
    }

    private boolean reportTaskTableExists() {
        if (jdbcTemplate == null) {
            return true;
        }
        try {
            return jdbcTemplate.existsTable(null, "nop_datav_report_task");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 运行时增量注册：报告任务 save/enable 后调用。ENABLED + 非空 cron → addJob(allowUpdate=true)；
     * 否则（非 ENABLED / 空 cron）→ removeJob（清理可能存在的旧 job）。
     */
    public void registerTask(String reportTaskId) {
        if (scheduler == null) {
            return;
        }
        IEntityDao<NopDatavReportTask> dao = daoProvider.daoFor(NopDatavReportTask.class);
        NopDatavReportTask task = dao.getEntityById(reportTaskId);
        if (task == null) {
            // 任务已被删除——清理可能残留的 job
            scheduler.removeJob(jobName(reportTaskId));
            return;
        }
        try {
            doRegister(task);
        } catch (Exception e) {
            LOG.error("nop.datav.report-scheduler.register-failed: reportTaskId={}", reportTaskId, e);
        }
    }

    /**
     * 运行时增量移除：报告任务 disable/delete 前调用，移除其定时 job。
     */
    public void unregisterTask(String reportTaskId) {
        if (scheduler == null) {
            return;
        }
        scheduler.removeJob(jobName(reportTaskId));
    }

    /**
     * beanMethod 调用入口：经 {@code BeanMethodJobInvoker} 反射调用，委托
     * {@link ReportDeliveryExecutor#execute} 执行报告生成 + 送达。
     *
     * <p><b>吞业务错误返回正常结果（schedule-report-design.md §9）</b>：所有业务异常 catch 后
     * 记 ERROR 日志并返回带 error 信息的正常结果 Map。不向外抛——否则 invoker 转
     * {@code JobFireResult.ERROR} 后 LocalJobScheduler 将 job 永久置 FAILED（FAILED-brick）。
     * 仅 {@code Error}（如 OOM）才抛。</p>
     *
     * @param params jobParams（移除 beanName/methodName 后）：{@code {reportTaskId: <id>, scheduledFireTime: <ms>}}
     * @return 执行结果 Map（reportTaskId/status/deliveryId/error）
     */
    public Map<String, Object> executeScheduledReport(Map<String, Object> params) {
        String reportTaskId = null;
        try {
            Object id = params != null ? params.get(PARAM_REPORT_TASK_ID) : null;
            if (id == null) {
                throw new IllegalArgumentException("missing reportTaskId in job params");
            }
            reportTaskId = String.valueOf(id);

            // scheduledFireTime 优先取 jobParams（fireNow 注入），缺省取 now
            long scheduledFireTime = CoreMetrics.currentTimeMillis();
            Object sft = params.get(PARAM_SCHEDULED_FIRE_TIME);
            if (sft instanceof Number) {
                scheduledFireTime = ((Number) sft).longValue();
            }

            String deliveryId = reportDeliveryExecutor.execute(
                    reportTaskId, NopDatavReportTriggerSource.SCHEDULE, scheduledFireTime);

            return buildResult(reportTaskId, "scheduled", deliveryId, null);
        } catch (Exception e) {
            // 业务异常吞掉，记 ERROR 日志，返回正常结果（避免 LocalJobScheduler FAILED-brick）
            // Dim14-04: catch (Exception) 而非 catch (Throwable)，允许 Error（OOM/StackOverflow）传播
            Throwable reason = NopException.adapt(e);
            LOG.error("nop.datav.report-scheduler.scheduled-exec-failed: reportTaskId={} error={}",
                    reportTaskId, safeMsg(reason), reason);
            return buildResult(reportTaskId, "failed", null, safeMsg(reason));
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    private boolean doRegister(NopDatavReportTask task) {
        String reportTaskId = task.getReportTaskId();
        String cron = task.getCronExpr();
        if (cron == null || cron.trim().isEmpty()) {
            scheduler.removeJob(jobName(reportTaskId));
            return false;
        }
        if (!NopDatavReportTaskStatus.isEnabled(task.getStatus())) {
            scheduler.removeJob(jobName(reportTaskId));
            return false;
        }
        JobSpec spec = buildJobSpec(reportTaskId, cron, task.getDisplayName());
        try {
            scheduler.addJob(spec, true);
        } catch (Exception e) {
            // addJob 失败（如 cron 非法）→ 清理旧 job 防过期调度残留
            LOG.error("nop.datav.report-scheduler.add-job-failed: reportTaskId={} cron={}",
                    reportTaskId, cron, e);
            try {
                scheduler.removeJob(jobName(reportTaskId));
            } catch (Exception re) {
                LOG.error("nop.datav.report-scheduler.remove-stale-job-failed: reportTaskId={}",
                        reportTaskId, re);
            }
            return false;
        }
        LOG.info("nop.datav.report-scheduler.registered: reportTaskId={} cron={}", reportTaskId, cron);
        return true;
    }

    private static JobSpec buildJobSpec(String reportTaskId, String cron, String displayName) {
        JobSpec spec = new JobSpec();
        spec.setJobName(jobName(reportTaskId));
        spec.setDisplayName(displayName != null ? displayName : JOB_NAME_PREFIX + reportTaskId);
        spec.setJobGroup("nop-datav");
        spec.setJobInvoker("beanMethod");

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put(PARAM_BEAN_NAME, BEAN_NAME);
        jobParams.put(PARAM_METHOD_NAME, SCHEDULED_METHOD_NAME);
        jobParams.put(PARAM_REPORT_TASK_ID, reportTaskId);
        spec.setJobParams(jobParams);

        TriggerSpec trigger = new TriggerSpec();
        trigger.setCronExpr(cron);
        spec.setTriggerSpec(trigger);
        return spec;
    }

    private static Map<String, Object> buildResult(String reportTaskId, String status,
                                                    String deliveryId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportTaskId", reportTaskId);
        result.put("status", status);
        result.put("deliveryId", deliveryId);
        result.put("error", error);
        return result;
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    /** jobName 约定：前缀 + reportTaskId */
    public static String jobName(String reportTaskId) {
        return JOB_NAME_PREFIX + reportTaskId;
    }

    /** 测试辅助：返回当前注册的 job 名集合（断言 scanner 注册成功） */
    public List<String> getRegisteredJobNames() {
        if (scheduler == null) {
            return Collections.emptyList();
        }
        return scheduler.getJobNames();
    }

    /** 测试辅助：暴露调度器实例（测试经 {@link IJobScheduler#fireNow} 同步触发） */
    @Nullable
    public IJobScheduler getScheduler() {
        return scheduler;
    }

    /** 测试辅助：手动触发 {@link #executeScheduledReport}（不经 scheduler，直接调本方法） */
    public Map<String, Object> fireScheduledForTest(String reportTaskId) {
        Map<String, Object> params = new HashMap<>();
        params.put(PARAM_REPORT_TASK_ID, reportTaskId);
        params.put(PARAM_SCHEDULED_FIRE_TIME, CoreMetrics.currentTimeMillis());
        return executeScheduledReport(params);
    }
}
