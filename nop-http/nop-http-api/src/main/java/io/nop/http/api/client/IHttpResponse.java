/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.client;

import io.nop.api.core.json.JSON;
import io.nop.http.api.IHttpHeaders;

public interface IHttpResponse extends IHttpHeaders {
    int getHttpStatus();

    String getContentType();

    String getCharset();

    byte[] getBodyAsBytes();

    String getBodyAsText();

    default <T> T getBodyAsBean(Class<T> beanClass) {
        return (T) JSON.parseToBean(null, getBodyAsText(), beanClass);
    }
}
