/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import io.nop.http.api.IHttpHeaders;

public interface IHttpResponse extends IHttpHeaders {
    int getHttpStatus();

    String getContentType();

    String getCharset();

    byte[] getBodyAsBytes();

    String getBodyAsString();

    <T> T getBodyAsBean(Class<T> beanClass);

    Object getBody();
}
