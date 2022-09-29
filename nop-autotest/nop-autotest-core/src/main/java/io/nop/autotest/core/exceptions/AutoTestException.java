package io.nop.autotest.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class AutoTestException extends NopException {
    public AutoTestException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AutoTestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
