/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.interceptor;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.orm.IOrmTemplate;

import java.util.concurrent.CompletionStage;

public class SingleSessionServiceInterceptor implements IRpcServiceInterceptor {
    private final IOrmTemplate ormTemplate;

    public SingleSessionServiceInterceptor(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        return ormTemplate.runInSessionAsync(session -> {
            return inv.proceedAsync();
        });
    }

    @Override
    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        return ormTemplate.runInSession(session -> {
            return inv.proceed();
        });
    }
}
