package io.nop.find.core;

import io.nop.api.core.exceptions.NopException;

/**
 * nop-find 模块级异常（英文消息，见 docs-for-ai 两层错误处理策略）。
 */
public class NopFindException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopFindException(String message) {
        super(message, null, true, true);
    }

    public NopFindException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}