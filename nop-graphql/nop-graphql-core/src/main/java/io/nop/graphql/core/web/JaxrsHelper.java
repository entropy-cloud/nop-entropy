/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.web;

import java.util.Map;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.json.JSON;

import jakarta.ws.rs.core.Response;

public class JaxrsHelper {

    public static Response buildJaxrsResponse(ApiResponse<?> res) {
        String body = JSON.stringify(res.cloneInstance(false));

        int status = res.getHttpStatus();
        if (status == 0) {
            status = 200;
        }

        return buildJaxrsResponse(res.getHeaders(), body, status);
    }

    public static Response buildJaxrsResponse(Map<String, Object> headers, Object body, int status) {
        Response.ResponseBuilder builder = Response.status(status).entity(body);

        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder.build();
    }
}
