/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.exceptions;

import java.io.Serializable;

public class ErrorCode implements Serializable {
    private static final long serialVersionUID = -3484273080650693192L;

    private final String errorCode;
    private final String description;
    private final String[] argNames;
    private final int status;

    public ErrorCode(int status, String errorCode, String description, String... argNames) {
        this.status = status;
        this.errorCode = errorCode;
        this.description = description;
        this.argNames = argNames;
    }

    public static ErrorCode define(String errorCode, String message, String... argNames) {
        return new ErrorCode(-1, errorCode, message, argNames);
    }

    public static ErrorCode define(int status, String errorCode, String message, String... argNames) {
        return new ErrorCode(status, errorCode, message, argNames);
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    public String[] getArgNames() {
        return argNames;
    }
}