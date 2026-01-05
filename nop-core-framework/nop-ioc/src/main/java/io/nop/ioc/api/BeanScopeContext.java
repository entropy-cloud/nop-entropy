/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.api;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.ioc.impl.BeanScopeContextImpl;
import io.nop.ioc.impl.BeanScopeImpl;
import io.nop.xlang.api.XLang;

import java.util.function.Supplier;

public class BeanScopeContext {
    static IBeanScopeContext _INSTANCE = new BeanScopeContextImpl();

    public static IBeanScopeContext instance() {
        return _INSTANCE;
    }

    public static <T> T runWithScope(IBeanScope scope, Supplier<T> task) {
        IBeanScopeContext context = instance();
        context.bind(scope);
        try {
            return task.get();
        } finally {
            context.closeScope(scope);
        }
    }

    public static <T> T runWithNewScope(String scopeName, IEvalScope scope, Supplier<T> task) {
        IBeanScope beanScope = new BeanScopeImpl(scopeName, scope, (IBeanContainerImplementor) BeanContainer.instance());
        return runWithScope(beanScope, task);
    }

    public static <T> T runWithNewScope(String scopeName, Supplier<T> task) {
        return runWithNewScope(scopeName, XLang.newEvalScope(), task);
    }
}