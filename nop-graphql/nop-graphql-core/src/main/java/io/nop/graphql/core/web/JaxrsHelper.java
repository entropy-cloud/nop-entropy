/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.web;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;

import jakarta.ws.rs.core.Response;

public class JaxrsHelper {
    public static Response buildJaxrsResponse(ApiResponse<?> res) {
        String str = JSON.stringify(res.cloneInstance(false));

        int status = res.getHttpStatus();
        if (status == 0)
            status = 200;

        Response.ResponseBuilder builder = Response.status(status).entity(str);
        if (res.getHeaders() != null) {
            res.getHeaders().forEach(builder::header);
        }
        return builder.build();
    }
}
