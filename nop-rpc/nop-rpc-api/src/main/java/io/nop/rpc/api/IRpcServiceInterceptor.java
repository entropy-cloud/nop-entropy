/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IOrdered;

import java.util.concurrent.CompletionStage;

/**
 * 提供异步的AOP拦截接口
 */
public interface IRpcServiceInterceptor extends IOrdered, Comparable<IRpcServiceInterceptor> {

    @Override
    default int compareTo(IRpcServiceInterceptor o) {
        return Integer.compare(this.order(), o.order());
    }

    CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv);

    default ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        return FutureHelper.syncGet(interceptAsync(inv));
    }
}
