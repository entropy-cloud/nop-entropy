/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.web;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.utils.GraphQLResponseHelper;
import jakarta.ws.rs.core.Response;

public class JaxrsHelper {

    public static Response buildJaxrsResponse(ApiResponse<?> res) {
        return GraphQLResponseHelper.consumeJsonResponse(res, JaxrsHelper::buildResponse);
    }

    public static Response buildJaxrsResponse(Map<String, Object> headers, Object body, int status) {
        return buildResponse((headerSet) -> {
            if (headers != null) {
                headers.forEach(headerSet);
            }
        }, body, status);
    }

    private static Response buildResponse(
            Consumer<BiConsumer<String, Object>> invokeHeaderSet, Object body, int status
    ) {
        Response.ResponseBuilder builder = Response.status(status).entity(body);

        invokeHeaderSet.accept(builder::header);

        return builder.build();
    }
}
