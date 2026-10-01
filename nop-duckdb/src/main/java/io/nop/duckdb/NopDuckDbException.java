package io.nop.duckdb;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Module exception for the nop-duckdb analysis execution layer.
 * String messages must be in English (see docs-for-ai/02-core-guides/error-handling.md).
 */
public class NopDuckDbException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopDuckDbException(String message) {
        super(message, null, true, true);
    }

    public NopDuckDbException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopDuckDbException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopDuckDbException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
