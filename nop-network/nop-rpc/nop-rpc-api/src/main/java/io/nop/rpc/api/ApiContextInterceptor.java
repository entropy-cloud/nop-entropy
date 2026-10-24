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
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;

import java.util.concurrent.CompletionStage;

/**
 * 根据ApiRequest中的信息初始化IContext。当请求执行完毕之后自动清除context
 */
public class ApiContextInterceptor implements IRpcServiceInterceptor {

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(
            IRpcServiceInvocation inv) {

        ApiRequest<?> request = inv.getRequest();
        ContextBinder binder = new ContextBinder(request);

        try {
            return inv.proceedAsync().whenComplete(
                    (ret, err) -> {
                        binder.close();
                    });
        } catch (Throwable e) {
            // C3-006 面同步抛保护（4.3-C3-007 随单）：proceedAsync 同步抛出时 whenComplete
            // 不会挂上，必须显式关闭 binder 持有的 context，否则新建 context 在线程上泄漏
            binder.close();
            throw e;
        }
    }

    @Override
    public ApiResponse<?> intercept(
            IRpcServiceInvocation inv) {

        ApiRequest<?> request = inv.getRequest();
        ContextBinder binder = new ContextBinder(request);

        try {
            return inv.proceed();
        } finally {
            binder.close();
        }
    }
}
