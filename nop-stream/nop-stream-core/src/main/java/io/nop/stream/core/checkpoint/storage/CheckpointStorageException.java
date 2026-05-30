/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.stream.core.exceptions.StreamException;

public class CheckpointStorageException extends StreamException {
    private static final long serialVersionUID = 1L;

    public CheckpointStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public CheckpointStorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CheckpointStorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
