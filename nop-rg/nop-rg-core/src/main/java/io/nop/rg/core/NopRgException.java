package io.nop.rg.core;

import io.nop.api.core.exceptions.NopException;

/**
 * nop-rg 模块级异常（英文消息，见 docs-for-ai 两层错误处理策略）。
 */
public class NopRgException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopRgException(String message) {
        super(message, null, true, true);
    }

    public NopRgException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
