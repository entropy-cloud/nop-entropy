/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.ApiErrors;

public class NopTimeoutException extends NopException {
    private static final long serialVersionUID = 4024368754301789024L;

    public NopTimeoutException() {
        super(ApiErrors.ERR_TIMEOUT);
    }

    public NopTimeoutException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopTimeoutException(ErrorCode errorCode, boolean writableStackTrace) {
        super(errorCode.getErrorCode(), null, false, writableStackTrace);
    }
}