package io.nop.datav.service.report;

/**
 * 定时报告任务状态常量（D5-1）。
 *
 * <p>对应 dict {@code datav/report-task-status}（int 值）。只有 ENABLED 状态的任务才会被
 * {@code NopDatavReportScheduler} 注册 cron job。</p>
 */
public final class NopDatavReportTaskStatus {

    public static final int DISABLED = 0;
    public static final int ENABLED = 10;

    private NopDatavReportTaskStatus() {
    }

    public static boolean isEnabled(int status) {
        return status == ENABLED;
    }
}
