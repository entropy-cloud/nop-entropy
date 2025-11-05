/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.interceptor;

import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.core.reflect.aop.IMethodInvocation;
import io.nop.orm.IOrmTemplate;

import java.util.concurrent.CompletionStage;

public class SingleSessionMethodInterceptor implements IMethodInterceptor {
    private final IOrmTemplate ormTemplate;

    public SingleSessionMethodInterceptor(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public Object invoke(IMethodInvocation inv) throws Exception {
        IFunctionModel method = inv.getMethod();
        SingleSession singleSession = method.getAnnotation(SingleSession.class);
        if (singleSession == null)
            return inv.proceed();

        if (method.isAsync()) {
            return ormTemplate.runInSessionAsync(session -> {
                try {
                    return (CompletionStage<?>) inv.proceed();
                } catch (Exception e) {
                    throw NopException.adapt(e);
                }
            });
        }

        return ormTemplate.runInSession(session -> {
            try {
                return inv.proceed();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        });
    }
}