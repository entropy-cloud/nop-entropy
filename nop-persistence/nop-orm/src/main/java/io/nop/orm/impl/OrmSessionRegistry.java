/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;

public class OrmSessionRegistry extends ContextualizedRegistry<IOrmSessionFactory, IOrmSession> {
    static final String CONTEXT_KEY = OrmSessionRegistry.class.getSimpleName();

    public static OrmSessionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        OrmSessionRegistry registry = (OrmSessionRegistry) context.getAttribute(CONTEXT_KEY);
        if (registry == null) {
            registry = new OrmSessionRegistry();
            context.setAttribute(CONTEXT_KEY, registry);
        }
        return registry;
    }

}