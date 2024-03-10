/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.api;

import io.nop.ioc.impl.BeanScopeContextImpl;

import java.util.function.Supplier;

public class BeanScopeContext {
    static IBeanScopeContext _instance = new BeanScopeContextImpl();

    public static IBeanScopeContext instance() {
        return _instance;
    }

    public static <T> T withScope(IBeanScope scope, Supplier<T> task) {
        IBeanScopeContext context = instance();
        context.bind(scope);
        try {
            return task.get();
        } finally {
            context.closeScope(scope);
        }
    }
}