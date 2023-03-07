/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * 批处理过程中如果发现已经被cancel，则抛出此异常。Retry和Skip逻辑识别此异常，会自动中断处理。
 */
public class BatchCancelException extends NopException {
    public BatchCancelException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BatchCancelException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
