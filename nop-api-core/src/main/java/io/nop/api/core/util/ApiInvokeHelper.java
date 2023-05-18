/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Api统一采用ApiRequest作为请求参数，以ApiResponse作为返回结果。如果在业务编程中不需要处理request和response的header信息，
 * 则可以通过这里的帮助函数来简化调用。
 * <pre>{@code
 *      MyResponse res = invokeApi(myApi.methodA, myRequest)
 * }</pre>
 */
public class ApiInvokeHelper {
    public static <T> T getResponseData(ApiResponse<T> res) {
        if (res == null)
            return null;
        return res.get();
    }

    public static Void ignoreResult(ApiResponse res) {
        getResponseData(res);
        return null;
    }

    public static <R, T> T invokeApi(Function<ApiRequest<R>, ApiResponse<T>> fn, R request) {
        ApiRequest<R> req = new ApiRequest<>();
        req.setData(request);
        ApiResponse<T> res = fn.apply(req);
        return getResponseData(res);
    }

    public static <R, T> CompletionStage<T> invokeApiAsync(
            Function<ApiRequest<R>, ? extends ResolvedPromise<ApiResponse<T>>> fn, R request) {
        ApiRequest<R> req = new ApiRequest<>();
        req.setData(request);
        return fn.apply(req).thenApply(ApiInvokeHelper::getResponseData);
    }

//    public static void destroyProxy(Object o) {
//        if (o instanceof IRpcProxy) {
//            ((IRpcProxy) o).proxy_destroy();
//        }
//    }
}