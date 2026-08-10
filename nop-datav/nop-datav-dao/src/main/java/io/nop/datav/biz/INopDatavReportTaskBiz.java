package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;

import java.util.List;

/**
 * 定时报告任务 BizModel 接口（D5-1）。
 *
 * <p>CRUD 继承 {@link ICrudBiz}；自定义 action：
 * <ul>
 *   <li>{@link #enableReportTask} / {@link #disableReportTask}：启用/禁用任务（注册/注销 cron job）</li>
 *   <li>{@link #triggerReportNow}：手动立即触发一次报告执行（triggerSource=manual）</li>
 *   <li>{@link #getReportDeliveryHistory}：查询交付历史（按 startTime 倒序）</li>
 * </ul>
 * </p>
 *
 * <p>action 经 {@code @Auth} + 看板 owner RLS（{@code NopDatavDashboardOwnerGuard} 校验 dashboardId owner）。
 * 报告任务属看板 owner 可管理对象（schedule-report-design.md §8）。</p>
 */
public interface INopDatavReportTaskBiz extends ICrudBiz<NopDatavReportTask> {

    @BizMutation("enableReportTask")
    NopDatavReportTask enableReportTask(@Name("id") String reportTaskId, IServiceContext context);

    @BizMutation("disableReportTask")
    NopDatavReportTask disableReportTask(@Name("id") String reportTaskId, IServiceContext context);

    @BizMutation("triggerReportNow")
    String triggerReportNow(@Name("id") String reportTaskId, IServiceContext context);

    @BizQuery("getReportDeliveryHistory")
    List<NopDatavReportDelivery> getReportDeliveryHistory(@Name("id") String reportTaskId,
                                                          IServiceContext context);
}
