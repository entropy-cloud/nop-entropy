/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * 对Api服务的弱类型调用接口。 RPC请求发出请求包，必须等待响应包到达。
 */
public interface IRpcService {

    /**
     * 异步调用API服务
     *
     * @param serviceMethod API服务方法名
     * @param request       请求对象
     * @return 异步返回的结果对象
     */
    CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken);

    default ApiResponse<?> call(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(callAsync(serviceMethod, request, cancelToken));
    }
}