/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.exceptions;

public class StreamRuntimeException extends RuntimeException {
    public StreamRuntimeException(String message) {
        super(message);
    }

    public StreamRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
