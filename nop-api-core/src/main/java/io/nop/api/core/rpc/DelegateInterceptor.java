/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiResponse;

import java.util.concurrent.CompletionStage;

/**
 * 用于改变interceptor的缺省优先级
 */
public class DelegateInterceptor implements IRpcServiceInterceptor {
    private final int priority;
    private final IRpcServiceInterceptor interceptor;

    public DelegateInterceptor(int priority, IRpcServiceInterceptor interceptor) {
        this.priority = priority;
        this.interceptor = interceptor;
    }

    @Override
    public int order() {
        return priority;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        return interceptor.interceptAsync(inv);
    }

    @Override
    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        return interceptor.intercept(inv);
    }
}
