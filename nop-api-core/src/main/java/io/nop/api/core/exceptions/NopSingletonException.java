/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.util.SourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 如果异常只有一个实例对象，需要禁止suppression机制，也不必保留程序堆栈
 */
public class NopSingletonException extends RuntimeException implements IException {
    public static final Map<String, NopSingletonException> SINGLETONS = new ConcurrentHashMap<>();

    private static final long serialVersionUID = -4181505268977854200L;

    private final String description;
    private final int status;

    protected NopSingletonException(ErrorCode errorCode) {
        super(errorCode.getErrorCode(), null, false, false);
        this.description = errorCode.getDescription();
        this.status = errorCode.getStatus();

        if (SINGLETONS.putIfAbsent(errorCode.getErrorCode(), this) != null)
            throw new NopException(ApiErrors.ERR_DUPLICATE_SINGLETON_EXCEPTION).param(ApiErrors.ARG_ERROR_CODE,
                    errorCode.getErrorCode());
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public boolean isBizFatal() {
        return true;
    }

    public String getErrorCode() {
        return super.getMessage();
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean isWrapException() {
        return false;
    }

    @Override
    public Map<String, Object> getParams() {
        return Collections.emptyMap();
    }

    @Override
    public SourceLocation getErrorLocation() {
        return null;
    }
}