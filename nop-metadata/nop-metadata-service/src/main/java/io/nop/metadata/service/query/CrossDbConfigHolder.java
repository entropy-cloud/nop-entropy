package io.nop.metadata.service.query;

public class CrossDbConfigHolder {
    /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败）。 */
    public static int maxCrossDbRows = 10000;

    private CrossDbConfigHolder() {
    }
}
