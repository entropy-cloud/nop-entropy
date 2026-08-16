package io.nop.datav.service.alert;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.datav.dao.entity.NopDatavAlertRule;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_RULE_NOT_FOUND;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警 cron 调度器（D5-2，schedule-report-design.md §20）。
 *
 * <p>普通 IoC bean（非 {@code @BizModel}），与 {@code NopDatavReportScheduler} 同模式：
 * <ol>
 *   <li><b>启动 scanner</b>：{@link #init()} 读所有 {@code status=ENABLED} 告警规则，逐个经
 *       {@link IJobScheduler#addJob} 注册 cron job。单任务注册失败 try/catch 隔离，不抛崩启动。</li>
 *   <li><b>运行时增量</b>：{@link #registerRule(String)} / {@link #unregisterRule(String)} 供
 *       BizModel save/enable/disable/delete 调用，使配置变更即时生效。</li>
 *   <li><b>调用入口包装</b>：{@link #executeScheduledAlert(Map)} 经 {@code beanMethod} invoker 调用，
 *       内部委托 {@link AlertEvaluator#evaluate}。</li>
 * </ol>
 * </p>
 *
 * <p><b>调度器分离裁定（schedule-report-design.md §20）</b>：本 bean 独立于 {@code NopDatavReportScheduler}
 * （关注点分离——告警评估逻辑与报告生成逻辑生命周期不同），但共用 {@code beanMethod} invoker 范式与
 * {@code IJobScheduler} 可空注入约定，各自有独立的 {@code jobName} 前缀（{@code nop-datav-alert-}）与
 * {@code BEAN_NAME}/{@code SCHEDULED_METHOD_NAME} 常量。</p>
 *
 * <p><b>{@code IJobScheduler} 可空注入</b>：宿主 app 未注册调度器时 {@code scheduler == null}，
 * scanner/注册/触发全部显式跳过（INFO 日志，不抛崩）。{@code evaluateAlertNow} 仍可用。</p>
 *
 * <p><b>LocalJobScheduler FAILED-brick 约定（与 D5-1 一致）</b>：
 * {@link #executeScheduledAlert} 必须 <b>吞业务错误返回正常结果对象</b>（catch 业务异常 → 返回带 error
 * 信息的正常结果，不抛）。否则 invoker 转 {@code JobFireResult.ERROR} 后 LocalJobScheduler 将 job 永久置 FAILED。
 * 仅基础设施错误（{@code Error}）才抛。</p>
 */
public class NopDatavAlertScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavAlertScheduler.class);

    /** jobName 前缀，避免与其它模块 job 冲突；后缀 alertRuleId。 */
    public static final String JOB_NAME_PREFIX = "nop-datav-alert-";

    /** beanMethod invoker 约定：jobParams 中的 bean 名键 */
    static final String PARAM_BEAN_NAME = "beanName";
    /** beanMethod invoker 约定：jobParams 中的方法名键 */
    static final String PARAM_METHOD_NAME = "methodName";
    /** jobParams 中传递给包装方法的告警规则 ID 键 */
    static final String PARAM_ALERT_RULE_ID = "alertRuleId";

    /** 本 bean 在 IoC 容器中的注册名（与 app-service.beans.xml 一致） */
    public static final String BEAN_NAME = "nopDatavAlertScheduler";
    /** beanMethod 调用的方法名 */
    public static final String SCHEDULED_METHOD_NAME = "executeScheduledAlert";

    private IJobScheduler scheduler;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;
    private AlertEvaluator alertEvaluator;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setAlertEvaluator(AlertEvaluator alertEvaluator) {
        this.alertEvaluator = alertEvaluator;
    }

    /**
     * 注入 {@link IJobScheduler}（{@code @Nullable}——宿主未注册调度器时不注入，
     * scanner/注册/触发全部跳过）。与 {@code NopDatavReportScheduler} 同模式。
     */
    @Inject
    public void setScheduler(@Nullable IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 启动 scanner：读所有 {@code status=ENABLED} 告警规则，非空合法 cron 则注册。单任务失败 try/catch 隔离。
     *
     * <p><b>表存在性守卫</b>（镜像 {@code NopDatavReportScheduler} 模式）：查询前先判表是否已建，
     * 避免测试环境 schema 尚未建表阶段误报。</p>
     */
    @PostConstruct
    public void init() {
        if (scheduler == null) {
            LOG.info("nop.datav.alert-scheduler.no-scheduler: cron scheduling disabled (host app did not register IJobScheduler)");
            return;
        }
        // 防御性 activate：activate() 幂等，先激活以保证后续 addJob 可用
        scheduler.activate();
        if (!alertRuleTableExists()) {
            LOG.info("nop.datav.alert-scheduler.table-not-exists: skip init scanner (table NOP_DATAV_ALERT_RULE not yet created)");
            return;
        }
        IEntityDao<NopDatavAlertRule> dao = daoProvider.daoFor(NopDatavAlertRule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopDatavAlertRule.PROP_NAME_status,
                io.nop.datav.service.report.NopDatavReportTaskStatus.ENABLED));
        @SuppressWarnings("unchecked")
        List<NopDatavAlertRule> active = (List<NopDatavAlertRule>) dao.findAllByQuery(q);
        int registered = 0;
        for (NopDatavAlertRule rule : active) {
            try {
                if (doRegister(rule)) {
                    registered++;
                }
            } catch (Exception e) {
                LOG.error("nop.datav.alert-scheduler.register-failed: alertRuleId={}",
                        rule.getAlertRuleId(), e);
            }
        }
        LOG.info("nop.datav.alert-scheduler.init-done: activeRules={} registered={}",
                active.size(), registered);
    }

    private boolean alertRuleTableExists() {
        if (jdbcTemplate == null) {
            return true;
        }
        try {
            return jdbcTemplate.existsTable(null, "nop_datav_alert_rule");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 运行时增量注册：告警规则 save/enable 后调用。ENABLED + 非空 cron → addJob(allowUpdate=true)；
     * 否则（非 ENABLED / 空 cron）→ removeJob。
     */
    public void registerRule(String alertRuleId) {
        if (scheduler == null) {
            return;
        }
        IEntityDao<NopDatavAlertRule> dao = daoProvider.daoFor(NopDatavAlertRule.class);
        NopDatavAlertRule rule = dao.getEntityById(alertRuleId);
        if (rule == null) {
            scheduler.removeJob(jobName(alertRuleId));
            return;
        }
        try {
            doRegister(rule);
        } catch (Exception e) {
            LOG.error("nop.datav.alert-scheduler.register-failed: alertRuleId={}", alertRuleId, e);
        }
    }

    /**
     * 运行时增量移除：告警规则 disable/delete 前调用。
     */
    public void unregisterRule(String alertRuleId) {
        if (scheduler == null) {
            return;
        }
        scheduler.removeJob(jobName(alertRuleId));
    }

    /**
     * beanMethod 调用入口：经 {@code BeanMethodJobInvoker} 反射调用，委托
     * {@link AlertEvaluator#evaluate} 执行告警评估。
     *
     * <p><b>吞业务错误返回正常结果（schedule-report-design.md §9，与 D5-1 一致）</b>：
     * 所有业务异常 catch 后记 ERROR 日志并返回带 error 信息的正常结果 Map。不向外抛——
     * 规避 LocalJobScheduler FAILED-brick。仅 {@code Error} 才抛。</p>
     *
     * @param params jobParams：{@code {alertRuleId: <id>}}
     * @return 执行结果 Map（alertRuleId/state/notified/error）
     */
    public Map<String, Object> executeScheduledAlert(Map<String, Object> params) {
        String alertRuleId = null;
        try {
            Object id = params != null ? params.get(PARAM_ALERT_RULE_ID) : null;
            if (id == null) {
                // Dim09-05: 使用结构化 NopException + ErrorCode 代替 raw IllegalArgumentException
                throw new NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND)
                        .param(ARG_ALERT_RULE_ID, "(absent from job params)");
            }
            alertRuleId = String.valueOf(id);

            AlertEvaluator.EvalResult result = alertEvaluator.evaluate(alertRuleId);

            Map<String, Object> m = result.toMap();
            m.put("status", "scheduled");
            return m;
        } catch (Exception e) {
            // 业务异常吞掉，记 ERROR 日志，返回正常结果（避免 LocalJobScheduler FAILED-brick）
            // Dim14-04: catch (Exception) 而非 catch (Throwable)，允许 Error（OOM/StackOverflow）传播
            Throwable reason = NopException.adapt(e);
            LOG.error("nop.datav.alert-scheduler.scheduled-exec-failed: alertRuleId={} error={}",
                    alertRuleId, safeMsg(reason), reason);
            return buildResult(alertRuleId, "failed", null, null, safeMsg(reason));
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    private boolean doRegister(NopDatavAlertRule rule) {
        String alertRuleId = rule.getAlertRuleId();
        String cron = rule.getCronExpr();
        if (cron == null || cron.trim().isEmpty()) {
            scheduler.removeJob(jobName(alertRuleId));
            return false;
        }
        if (!io.nop.datav.service.report.NopDatavReportTaskStatus.isEnabled(
                rule.getStatus() == null ? 0 : rule.getStatus())) {
            scheduler.removeJob(jobName(alertRuleId));
            return false;
        }
        JobSpec spec = buildJobSpec(alertRuleId, cron, rule.getDisplayName());
        try {
            scheduler.addJob(spec, true);
        } catch (Exception e) {
            LOG.error("nop.datav.alert-scheduler.add-job-failed: alertRuleId={} cron={}",
                    alertRuleId, cron, e);
            try {
                scheduler.removeJob(jobName(alertRuleId));
            } catch (Exception re) {
                LOG.error("nop.datav.alert-scheduler.remove-stale-job-failed: alertRuleId={}",
                        alertRuleId, re);
            }
            return false;
        }
        LOG.info("nop.datav.alert-scheduler.registered: alertRuleId={} cron={}", alertRuleId, cron);
        return true;
    }

    private static JobSpec buildJobSpec(String alertRuleId, String cron, String displayName) {
        JobSpec spec = new JobSpec();
        spec.setJobName(jobName(alertRuleId));
        spec.setDisplayName(displayName != null ? displayName : JOB_NAME_PREFIX + alertRuleId);
        spec.setJobGroup("nop-datav");
        spec.setJobInvoker("beanMethod");

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put(PARAM_BEAN_NAME, BEAN_NAME);
        jobParams.put(PARAM_METHOD_NAME, SCHEDULED_METHOD_NAME);
        jobParams.put(PARAM_ALERT_RULE_ID, alertRuleId);
        spec.setJobParams(jobParams);

        TriggerSpec trigger = new TriggerSpec();
        trigger.setCronExpr(cron);
        spec.setTriggerSpec(trigger);
        return spec;
    }

    private static Map<String, Object> buildResult(String alertRuleId, String status,
                                                    String state, Boolean notified, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alertRuleId", alertRuleId);
        result.put("status", status);
        result.put("state", state);
        result.put("notified", notified);
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

    /** jobName 约定：前缀 + alertRuleId */
    public static String jobName(String alertRuleId) {
        return JOB_NAME_PREFIX + alertRuleId;
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

    /** 测试辅助：手动触发 {@link #executeScheduledAlert}（不经 scheduler，直接调本方法） */
    public Map<String, Object> fireScheduledForTest(String alertRuleId) {
        Map<String, Object> params = new HashMap<>();
        params.put(PARAM_ALERT_RULE_ID, alertRuleId);
        return executeScheduledAlert(params);
    }
}
