package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.datav.biz.INopDatavReportTaskBiz;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.NopDatavDashboardOwnerGuard;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import io.nop.datav.service.report.ReportDeliveryExecutor;

import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.util.List;

import static io.nop.datav.service.NopDatavErrors.ARG_CRON_EXPR;
import static io.nop.datav.service.NopDatavErrors.ARG_REPORT_TASK_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_CRON_INVALID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_TASK_NOT_FOUND;

/**
 * 定时报告任务 BizModel（D5-1）。
 *
 * <p>CRUD 继承 {@link CrudBizModel}（自动提供 query/mutation）+ 自定义 action：
 * {@link #enableReportTask}/{@link #disableReportTask}/{@link #triggerReportNow}/{@link #getReportDeliveryHistory}。
 * action 经 {@code @Auth} + 看板 owner 校验（{@link NopDatavDashboardOwnerGuard}），
 * 报告任务属看板 owner 可管理对象（schedule-report-design.md §8）。</p>
 *
 * <p><b>调度注册联动</b>：save（status=ENABLED）/enableReportTask 调 {@link NopDatavReportScheduler#registerTask}；
 * disableReportTask/delete 调 {@link NopDatavReportScheduler#unregisterTask}。使配置变更即时生效（无需重启）。</p>
 *
 * <p><b>手动触发</b>：{@link #triggerReportNow} 直接调 {@link ReportDeliveryExecutor#execute}
 * （triggerSource=manual），不依赖 cron 触发。</p>
 */
@BizModel("NopDatavReportTask")
public class NopDatavReportTaskBizModel extends CrudBizModel<NopDatavReportTask>
        implements INopDatavReportTaskBiz {

    @Inject
    protected NopDatavReportScheduler reportScheduler;

    @Inject
    protected ReportDeliveryExecutor reportDeliveryExecutor;

    public NopDatavReportTaskBizModel() {
        setEntityName(NopDatavReportTask.class.getName());
    }

    // ==================== save / delete override（联动调度注册） ====================

    @Override
    protected void afterEntityChange(NopDatavReportTask entity, String action, IServiceContext context) {
        // 实体变更后联动调度注册：ENABLED → register；非 ENABLED → unregister
        if (reportScheduler != null && entity != null) {
            if (NopDatavReportTaskStatus.isEnabled(entity.getStatus())) {
                reportScheduler.registerTask(entity.getReportTaskId());
            } else {
                reportScheduler.unregisterTask(entity.getReportTaskId());
            }
        }
    }

    // ==================== 自定义 action ====================

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavReportTask:enableReportTask")
    public NopDatavReportTask enableReportTask(@Name("id") String reportTaskId, IServiceContext context) {
        NopDatavReportTask task = requireTaskWithOwnership(reportTaskId, context);
        if (!StringHelper.isEmpty(task.getCronExpr())) {
            validateCron(task.getCronExpr());
        }
        task.setStatus(NopDatavReportTaskStatus.ENABLED);
        touchUpdate(task, NopDatavOperatorResolver.resolveOperator(context));
        daoProvider().daoFor(NopDatavReportTask.class).updateEntityDirectly(task);
        // 联动调度注册
        if (reportScheduler != null) {
            reportScheduler.registerTask(reportTaskId);
        }
        return task;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavReportTask:disableReportTask")
    public NopDatavReportTask disableReportTask(@Name("id") String reportTaskId, IServiceContext context) {
        NopDatavReportTask task = requireTaskWithOwnership(reportTaskId, context);
        // 先注销调度再落库（防 ENABLED 状态残留期间 cron 触发）
        if (reportScheduler != null) {
            reportScheduler.unregisterTask(reportTaskId);
        }
        task.setStatus(NopDatavReportTaskStatus.DISABLED);
        touchUpdate(task, NopDatavOperatorResolver.resolveOperator(context));
        daoProvider().daoFor(NopDatavReportTask.class).updateEntityDirectly(task);
        return task;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavReportTask:triggerReportNow")
    public String triggerReportNow(@Name("id") String reportTaskId, IServiceContext context) {
        NopDatavReportTask task = requireTaskWithOwnership(reportTaskId, context);
        return reportDeliveryExecutor.execute(
                reportTaskId, NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavReportTask:getReportDeliveryHistory")
    public List<NopDatavReportDelivery> getReportDeliveryHistory(@Name("id") String reportTaskId,
                                                                  IServiceContext context) {
        // owner 校验
        requireTaskWithOwnership(reportTaskId, context);
        IEntityDao<NopDatavReportDelivery> dao = daoProvider().daoFor(NopDatavReportDelivery.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("reportTaskId", reportTaskId));
        q.addOrderField("startTime", false);
        return (List<NopDatavReportDelivery>) dao.findAllByQuery(q);
    }

    // ==================== 校验 helpers ====================

    /**
     * 加载任务 + 看板 owner 校验：经 task.dashboardId 加载看板，校验当前用户为 owner 或 admin。
     * 失败抛 {@link NopDatavErrors#ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER}。
     */
    private NopDatavReportTask requireTaskWithOwnership(String reportTaskId, IServiceContext context) {
        IEntityDao<NopDatavReportTask> dao = daoProvider().daoFor(NopDatavReportTask.class);
        NopDatavReportTask task = dao.getEntityById(reportTaskId);
        if (task == null) {
            throw new NopException(ERR_DATAV_REPORT_TASK_NOT_FOUND).param(ARG_REPORT_TASK_ID, reportTaskId);
        }
        // 经 NopDatavDashboardOwnerGuard 校验 task.dashboardId 的 owner
        NopDatavDashboardOwnerGuard.requireDashboardOwnership(
                daoProvider(), task.getDashboardId(), context);
        return task;
    }

    /**
     * cron 表达式合法性校验：交给 LocalJobScheduler addJob 验证（非法 cron 在 addJob 时抛错）。
     * 这里做轻量级空值检查（深度校验由 scheduler.registerTask 内 addJob 完成）。
     */
    private static void validateCron(String cronExpr) {
        if (StringHelper.isEmpty(cronExpr)) {
            throw new NopException(ERR_DATAV_REPORT_CRON_INVALID).param(ARG_CRON_EXPR, cronExpr);
        }
        // 简单结构校验：至少 5 段（quartz cron 格式 "分 时 日 月 周"）
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 7) {
            throw new NopException(ERR_DATAV_REPORT_CRON_INVALID).param(ARG_CRON_EXPR, cronExpr);
        }
    }

    private static void touchUpdate(NopDatavReportTask task, String operator) {
        task.setUpdatedBy(operator);
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * 测试辅助：暴露调度器（断言 registerTask/unregisterTask 被调用）。
     */
    public NopDatavReportScheduler getReportScheduler() {
        return reportScheduler;
    }

    /**
     * 测试辅助：暴露 executor（断言 execute 被调用）。
     */
    public ReportDeliveryExecutor getReportDeliveryExecutor() {
        return reportDeliveryExecutor;
    }
}
