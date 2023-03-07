/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.exceptions;

import io.nop.api.core.exceptions.ErrorCode;

public class JdbcException extends DaoException {
    private static final long serialVersionUID = -8236600934628729279L;

    public JdbcException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public JdbcException(ErrorCode errorCode) {
        super(errorCode);
    }

    public JdbcException(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }
}