/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * RPC调用超时
 */
public class RpcTimeoutException extends NopException {
    public RpcTimeoutException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public RpcTimeoutException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RpcTimeoutException(String errorCode, Throwable cause, boolean enableSuppression,
                               boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }
}
