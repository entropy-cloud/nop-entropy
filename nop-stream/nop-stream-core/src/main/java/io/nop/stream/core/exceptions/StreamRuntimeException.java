/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.exceptions;

import io.nop.api.core.exceptions.NopException;

public class StreamRuntimeException extends NopException {
    private static final long serialVersionUID = 1L;

    public StreamRuntimeException(String message) {
        super(message, null, true, true);
    }

    public StreamRuntimeException(String message, Throwable cause) {
        super(message, cause, true, true);
    }
}
