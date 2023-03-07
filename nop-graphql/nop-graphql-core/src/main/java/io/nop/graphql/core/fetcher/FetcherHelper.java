/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.beans.ApiResponse;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.IFunctionModel;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public class FetcherHelper {

    public static Object processResponse(Object ret, IFunctionModel func, IServiceContext context) {
        if (ret == null)
            return null;

        if (func.getReturnType().getRawClass() == ApiResponse.class) {
            ApiResponse<Object> rep = (ApiResponse<Object>) ret;
            return processApiResponse(rep, context);
        } else if (func.getAsyncReturnType().getRawClass() == ApiResponse.class) {
            return ((CompletionStage) ret).thenApply(v -> processApiResponse((ApiResponse<Object>) v, context));
        } else {
            return ret;
        }
    }

    static Object processApiResponse(ApiResponse<Object> ret, IServiceContext context) {
        if (ret == null)
            return null;

        Map<String, Object> headers = ret.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            context.setResponseHeaders(headers);
        }

        return ret.getData();
    }
}
