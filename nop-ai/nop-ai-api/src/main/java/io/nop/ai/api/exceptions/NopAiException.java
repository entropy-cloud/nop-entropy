package io.nop.ai.api.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopAiException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopAiException(String message) {
        super(message, null, true, true);
    }

    public NopAiException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopAiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopAiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
