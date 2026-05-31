package io.nop.code.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopCodeException extends NopException {
    public NopCodeException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopCodeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
