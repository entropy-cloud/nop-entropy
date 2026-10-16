/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

/**
 * REPL 服务门控异常：携带 HTTP 状态码语义（401 未登录 / 403 无权限或非属主 /
 * 400 沙箱拒绝 / 413 尺寸超限 / 429 限流）；宿主壳按状态映射响应。
 */
public class ReplAccessException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final String responseMessage;

    public ReplAccessException(int httpStatus, String message) {
        this(httpStatus, null, message);
    }

    public ReplAccessException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.responseMessage = message;
    }

    /** 线上面消息（与 Throwable.getMessage 解耦，供响应构造）。 */
    public String getResponseMessage() {
        return responseMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
