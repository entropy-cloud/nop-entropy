package io.nop.datav.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopDatavConfigs {

    SourceLocation s_loc = SourceLocation.fromClass(NopDatavConfigs.class);

    @Description("单任务最大导出行数（超出抛 ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED，防 OOM）")
    IConfigReference<Integer> CFG_DATAV_EXPORT_MAX_ROWS = varRef(
            s_loc, "nop.datav.export.max-rows", Integer.class, 100000);

    @Description("单用户并发导出任务数上限（pending+running，超出抛 ERR_DATAV_EXPORT_CONCURRENCY_LIMIT）")
    IConfigReference<Integer> CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER = varRef(
            s_loc, "nop.datav.export.max-concurrent-per-user", Integer.class, 3);

    @Description("导出文件最大字节数（传给 IFileStore.saveFile 的 maxLength 上限）")
    IConfigReference<Long> CFG_DATAV_EXPORT_FILE_MAX_LENGTH = varRef(
            s_loc, "nop.datav.export.file-max-length", Long.class, 104857600L);

    // ===== D5-1 定时报告 =====

    @Description("定时报告默认 grace 期（分钟）。距预定时间超过 grace 则跳过本轮并写 skipped 交付记录（misfire 兜底）")
    IConfigReference<Integer> CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES = varRef(
            s_loc, "nop.datav.report.default-grace-minutes", Integer.class, 60);

    @Description("定时报告单任务最大导出行数（透传给 PanelDataExporter.exportDashboard 的 maxRows，复用 D3-3 取数阶段限额防 OOM）")
    IConfigReference<Integer> CFG_DATAV_REPORT_MAX_ROWS = varRef(
            s_loc, "nop.datav.report.max-rows", Integer.class, 100000);

    @Description("定时报告邮件发件人默认地址（为空时邮件渠道显式失败 ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED，不静默跳过）")
    IConfigReference<String> CFG_DATAV_REPORT_DEFAULT_SENDER = varRef(
            s_loc, "nop.datav.report.default-sender", String.class, "");

    @Description("定时报告邮件主题模板（{reportName} 占位符替换，渲染用 StringHelper.renderTemplate）")
    IConfigReference<String> CFG_DATAV_REPORT_DEFAULT_SUBJECT = varRef(
            s_loc, "nop.datav.report.default-subject", String.class, "Report: {reportName}");

    // ===== D5-2 轻量告警 =====

    @Description("告警默认冷静期秒数。0=仅状态转换时通知（OK→TRIGGERED 或 TRIGGERED→OK）；>0 时 TRIGGERED 持续满足距上次通知超过该秒数才重发")
    IConfigReference<Integer> CFG_DATAV_ALERT_DEFAULT_REARM_SECONDS = varRef(
            s_loc, "nop.datav.alert.default-rearm-seconds", Integer.class, 0);

    @Description("告警评估单面板最大查询行数（透传给 PanelDataBinder.queryPanelData 的 rowLimit，跨方言防 OOM）。聚合 sum/avg/min/max 基于返回行近似计算，精确聚合应让数据集 SQL 预聚合返回单行")
    IConfigReference<Integer> CFG_DATAV_ALERT_EVAL_MAX_ROWS = varRef(
            s_loc, "nop.datav.alert.eval-max-rows", Integer.class, 1000);

    @Description("告警通知邮件主题模板（{ruleName} 占位符替换，渲染用 StringHelper.renderTemplate）")
    IConfigReference<String> CFG_DATAV_ALERT_DEFAULT_SUBJECT = varRef(
            s_loc, "nop.datav.alert.default-subject", String.class, "Alert: {ruleName}");

    // ===== D6-1 ChatBI =====

    @Description("ChatBI tool-calling 循环最大轮次（超出抛 ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED）")
    IConfigReference<Integer> CFG_DATAV_CHATBI_MAX_ITERATIONS = varRef(
            s_loc, "nop.datav.chatbi.max-iterations", Integer.class, 5);

    @Description("ChatBI 单次数据集查询最大返回行数（经 LongRangeBean 在数据集层限行，跨方言防 OOM）")
    IConfigReference<Integer> CFG_DATAV_CHATBI_MAX_ROWS = varRef(
            s_loc, "nop.datav.chatbi.max-rows", Integer.class, 1000);

    @Description("ChatBI 默认 LLM model（为空时由 IChatService 实现决定 provider 默认）")
    IConfigReference<String> CFG_DATAV_CHATBI_DEFAULT_MODEL = varRef(
            s_loc, "nop.datav.chatbi.default-model", String.class, "");

    @Description("ChatBI 默认 provider（为空时由 IChatService 实现决定 provider 默认）")
    IConfigReference<String> CFG_DATAV_CHATBI_DEFAULT_PROVIDER = varRef(
            s_loc, "nop.datav.chatbi.default-provider", String.class, "");

    // ===== stuck-task 周期恢复扫描 =====

    @Description("是否启用 stuck-task 周期扫描（false 时不注册周期 job，仅保留重启 @PostConstruct 恢复）")
    IConfigReference<Boolean> CFG_DATAV_STUCK_SCAN_ENABLED = varRef(
            s_loc, "nop.datav.stuck-scan.enabled", Boolean.class, true);

    @Description("stuck-task 扫描间隔（分钟），经 TriggerSpec.repeatInterval 固定间隔周期触发")
    IConfigReference<Integer> CFG_DATAV_STUCK_SCAN_INTERVAL_MINUTES = varRef(
            s_loc, "nop.datav.stuck-scan.interval-minutes", Integer.class, 10);

    @Description("stuck 判定阈值（分钟）。交付按 startTime、导出按 createTime 判定；仅标记超过阈值的非终态记录为 FAILED（保守高值防误杀在途记录）")
    IConfigReference<Integer> CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES = varRef(
            s_loc, "nop.datav.stuck-scan.timeout-minutes", Integer.class, 60);

    // ===== 批量面板查询（getDashboardData） =====

    @Description("单次 getDashboardData 批量查询的最大面板数（纳入集超限抛 ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED，校验先于任何面板查询执行，防单请求放大为海量 SQL）")
    IConfigReference<Integer> CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS = varRef(
            s_loc, "nop.datav.dashboard-query.max-panels", Integer.class, 50);
}
