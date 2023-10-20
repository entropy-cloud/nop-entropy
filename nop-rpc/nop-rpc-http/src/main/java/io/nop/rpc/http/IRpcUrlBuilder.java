/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.http;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;

public interface IRpcUrlBuilder {
    String buildUrl(ApiRequest<?> req, String serviceMethod);

    default String toHttpHeader(String name) {
        if (name.startsWith(ApiConstants.TEMP_HEADER_PREFIX))
            return null;
        return name;
    }
}
