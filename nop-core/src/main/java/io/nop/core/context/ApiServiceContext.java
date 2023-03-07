/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.context;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;

import static io.nop.core.CoreErrors.ERR_CORE_NO_API_SERVICE_CONTEXT;

public class ApiServiceContext {
    private static final String CONTEXT_KEY = ApiServiceContext.class.getSimpleName();

    public static void set(IServiceContext context) {
        ContextProvider.setContextAttr(CONTEXT_KEY, context);
    }

    public static IServiceContext get() {
        return (IServiceContext) ContextProvider.getContextAttr(CONTEXT_KEY);
    }

    public static IServiceContext require() {
        IServiceContext context = get();
        if (context == null)
            throw new NopException(ERR_CORE_NO_API_SERVICE_CONTEXT);
        return context;
    }
}