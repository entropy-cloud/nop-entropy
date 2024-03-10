/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
