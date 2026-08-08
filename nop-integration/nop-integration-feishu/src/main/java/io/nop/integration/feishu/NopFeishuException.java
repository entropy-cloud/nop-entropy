package io.nop.integration.feishu;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Module-level exception for {@code nop-integration-feishu} internals (Pbbp2
 * protocol codec errors, stream lifecycle errors). Follows the AGENTS.md
 * "module internals" error-handling strategy: English string messages for
 * module-internal failures, {@code ErrorCode}-based constructor available for
 * cases that surface to public API consumers.
 */
public class NopFeishuException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopFeishuException(String message) {
        super(message, null, true, true);
    }

    public NopFeishuException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopFeishuException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopFeishuException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
