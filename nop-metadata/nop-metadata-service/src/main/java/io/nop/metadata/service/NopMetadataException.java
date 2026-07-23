/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * nop-metadata 模块级异常类。
 *
 * <p>提供两个 ErrorCode 构造器：
 * <ul>
 *   <li>{@link #NopMetadataException(ErrorCode)} — 错误码</li>
 *   <li>{@link #NopMetadataException(ErrorCode, Throwable)} — 错误码 + cause</li>
 * </ul>
 */
public class NopMetadataException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopMetadataException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopMetadataException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
