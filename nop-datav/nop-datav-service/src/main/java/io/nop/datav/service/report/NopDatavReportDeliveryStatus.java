package io.nop.datav.service.report;

/**
 * 定时报告交付状态常量（D5-1）。
 *
 * <p>对应 dict {@code datav/delivery-status}（int 值）。镜像 {@code NopDatavExportTaskStatus}
 * 的状态机模式，但增加 SKIPPED（grace 超期显式记录，非静默跳过）。</p>
 */
public final class NopDatavReportDeliveryStatus {

    public static final int PENDING = 0;
    public static final int RUNNING = 10;
    public static final int SUCCEEDED = 20;
    public static final int FAILED = 30;
    public static final int SKIPPED = 40;

    private NopDatavReportDeliveryStatus() {
    }

    public static boolean isTerminal(int status) {
        return status == SUCCEEDED || status == FAILED || status == SKIPPED;
    }

    public static String label(int status) {
        switch (status) {
            case PENDING:
                return "pending";
            case RUNNING:
                return "running";
            case SUCCEEDED:
                return "succeeded";
            case FAILED:
                return "failed";
            case SKIPPED:
                return "skipped";
            default:
                return "unknown";
        }
    }
}
