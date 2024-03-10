/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.exceptions;

import io.nop.api.core.ApiErrors;

public class AutoTestWrapException extends AutoTestException {
    public AutoTestWrapException(Throwable cause) {
        super(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        this.forWrap();
    }
}