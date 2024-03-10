/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.ApiConfigs;

import java.util.Map;

/**
 * xpl语言执行时只需要记录xpl堆栈，而不需要记录java堆栈
 */
public class NopEvalException extends NopException {

    private static final long serialVersionUID = 6840750562761897289L;

    public NopEvalException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NopEvalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopEvalException(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }

    public NopEvalException(String errorCode, Object params, Throwable cause) {
        this(errorCode, cause, true, true);
        if (params != null) {
            if (params instanceof Map) {
                this.params((Map<String, Object>) params);
            } else {
                this.param("params", params);
            }
        }
    }

    /**
     * 已经通过xplStack记录EL表达式堆栈，一般情况下没有必要再记录java堆栈
     */
    public synchronized Throwable fillInStackTrace() {
        if (ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE.get())
            return super.fillInStackTrace();
        return this;
    }
}