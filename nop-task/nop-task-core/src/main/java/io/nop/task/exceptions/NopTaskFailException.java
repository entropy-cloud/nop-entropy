package io.nop.task.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopTaskFailException extends NopException {
    public NopTaskFailException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NopTaskFailException(ErrorCode errorCode) {
        super(errorCode);
    }
}
