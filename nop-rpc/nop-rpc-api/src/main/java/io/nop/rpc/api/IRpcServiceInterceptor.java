/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

/**
 * 提供异步的AOP拦截接口
 */
public interface IRpcServiceInterceptor {

    CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv);

    default ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        return FutureHelper.syncGet(interceptAsync(inv));
    }
}
