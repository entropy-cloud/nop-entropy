package io.nop.chart.echarts;

import io.nop.api.core.exceptions.NopException;

/**
 * nop-chart-echarts 模块级异常：English 消息 + 快速失败语义（roadmap Cross-Cutting #4 第二档）。
 */
public class NopChartEchartsException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopChartEchartsException(String message) {
        super(message, null, true, true);
    }

    public NopChartEchartsException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
