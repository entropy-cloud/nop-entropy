package io.nop.datav.service.export;

/**
 * 导出任务状态机常量（D3-3）。值与 {@code datav/export-status} dict 同步：
 * pending(0) → running(10) → succeeded(20) | failed(30) | cancelled(40)。
 */
public final class NopDatavExportTaskStatus {

    public static final int PENDING = 0;
    public static final int RUNNING = 10;
    public static final int SUCCEEDED = 20;
    public static final int FAILED = 30;
    public static final int CANCELLED = 40;

    public static boolean isTerminal(int status) {
        return status == SUCCEEDED || status == FAILED || status == CANCELLED;
    }

    private NopDatavExportTaskStatus() {
    }
}
