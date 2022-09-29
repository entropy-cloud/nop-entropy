package io.nop.autotest.core.exceptions;

import io.nop.api.core.ApiErrors;

public class AutoTestWrapException extends AutoTestException {
    public AutoTestWrapException(Throwable cause) {
        super(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        this.forWrap();
    }
}