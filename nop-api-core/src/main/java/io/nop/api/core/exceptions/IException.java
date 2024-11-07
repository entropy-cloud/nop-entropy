/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.SourceLocation;

import java.util.Map;

public interface IException {
    int getStatus();

    /**
     * 异常码
     *
     * @return
     */
    String getErrorCode();

    String getDescription();

    /**
     * CheckedException被包装为NopWrapException后继续抛出。在转换为异常消息时，会使用NopWrapException.getCause()
     *
     * @return
     */
    boolean isWrapException();

    /**
     * bizFatal的异常一般不支持重试。
     */
    boolean isBizFatal();

    /**
     * 异常发生时的参数环境
     *
     * @return
     */
    Map<String, Object> getParams();

    /**
     * @return
     */
    SourceLocation getErrorLocation();

    default Map<String, ErrorBean> getDetails() {
        return null;
    }
}