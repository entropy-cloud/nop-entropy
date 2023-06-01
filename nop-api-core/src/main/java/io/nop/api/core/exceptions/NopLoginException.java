package io.nop.api.core.exceptions;

public class NopLoginException extends NopException {
    public NopLoginException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NopLoginException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopLoginException(String errorCode, Throwable cause,
                             boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }
}
