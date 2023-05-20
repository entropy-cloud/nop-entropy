package io.nop.api.core.exceptions;

public class NopConnectException extends NopException {

    public NopConnectException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NopConnectException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopConnectException(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }
}
