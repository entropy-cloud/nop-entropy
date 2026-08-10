package io.nop.datav.service.report;

/**
 * 定时报告触发来源常量（D5-1）。
 *
 * <p>对应 dict {@code datav/report-trigger-source}（string 值）。记录每次交付是被 cron 调度触发还是手动触发，
 * 用于交付历史溯源与 {@code NopDatavReportScheduler.executeScheduledReport} 吞业务错误的运维区分。</p>
 */
public final class NopDatavReportTriggerSource {

    /** cron 调度触发（经 beanMethod invoker） */
    public static final String SCHEDULE = "schedule";

    /** 手动触发（{@code triggerReportNow} action） */
    public static final String MANUAL = "manual";

    private NopDatavReportTriggerSource() {
    }
}
