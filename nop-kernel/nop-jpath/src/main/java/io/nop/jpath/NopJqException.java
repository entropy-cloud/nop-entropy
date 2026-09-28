package io.nop.jpath;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopJqException extends NopException {
    public NopJqException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopJqException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
