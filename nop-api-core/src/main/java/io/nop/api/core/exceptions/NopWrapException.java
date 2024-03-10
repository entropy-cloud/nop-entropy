/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import static io.nop.api.core.ApiErrors.ERR_WRAP_EXCEPTION;

/**
 * 在捕获java的CheckedException后总是包装为RuntimeException抛出。
 */
public class NopWrapException extends NopException {
    private static final long serialVersionUID = 5678084739807907692L;

    public NopWrapException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NopWrapException(Throwable cause) {
        super(ERR_WRAP_EXCEPTION, cause);
    }

    @Override
    public boolean isWrapException() {
        return true;
    }
}