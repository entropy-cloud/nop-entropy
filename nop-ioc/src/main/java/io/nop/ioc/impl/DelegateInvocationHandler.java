/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class DelegateInvocationHandler implements InvocationHandler {
    private InvocationHandler handler;

    public DelegateInvocationHandler() {
    }

    public DelegateInvocationHandler(InvocationHandler handler) {
        this.handler = handler;
    }

    public InvocationHandler getHandler() {
        return handler;
    }

    public void setHandler(InvocationHandler handler) {
        this.handler = handler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (handler == null)
            throw new IllegalStateException("nop.err.ioc.bean-proxy-not-initialized:" + method);

        return handler.invoke(proxy, method, args);
    }
}
