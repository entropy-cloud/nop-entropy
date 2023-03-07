/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.util.Map;

import static io.nop.api.core.ApiErrors.ARG_HEADER;

public interface IHttpHeaders {

    Map<String, String> getHeaders();

    String getHeader(String name);

    default Long getHeaderAsLong(String name) {
        return ConvertHelper.toLong(getHeader(name), err -> new NopException(err).param(ARG_HEADER, name));
    }

    default Integer getHeaderAsInt(String name) {
        return ConvertHelper.toInt(getHeader(name), err -> new NopException(err).param(ARG_HEADER, name));
    }
}
