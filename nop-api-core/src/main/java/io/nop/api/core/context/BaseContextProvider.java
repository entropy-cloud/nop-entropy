/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.context;

public class BaseContextProvider implements IContextProvider {
    private final static ThreadLocal<IContext> t_context = new ThreadLocal<>();

    public static ThreadLocal<IContext> contextHolder() {
        return t_context;
    }

    @Override
    public IContext currentContext() {
        return t_context.get();
    }

    public static void clear() {
        t_context.remove();
    }

    @Override
    public IContext getOrCreateContext() {
        IContext context = t_context.get();
        if (context == null || context.isClosed()) {
            context = new BaseContext();
            t_context.set(context);
        }
        return context;
    }

    @Override
    public IContext newContext() {
        IContext context = new BaseContext();
        t_context.set(context);
        return context;
    }
}
