package io.nop.canvas2d;

import io.nop.api.core.exceptions.NopException;

/**
 * nop-canvas2d 模块级异常：English 消息 + 快速失败语义（roadmap Cross-Cutting #4 第二档）。
 */
public class NopCanvas2DException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopCanvas2DException(String message) {
        super(message, null, true, true);
    }

    public NopCanvas2DException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
