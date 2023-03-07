/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class DaoException extends NopException {
    private static final long serialVersionUID = 8786280569819469973L;

    public DaoException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DaoException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DaoException(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }
}