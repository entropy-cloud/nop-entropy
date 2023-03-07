/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.tcc.api.ITccTransaction;

public class TccTransactionRegistry extends ContextualizedRegistry<String, ITccTransaction> {
    static final String CONTEXT_KEY = TccTransactionRegistry.class.getSimpleName();

    public static TccTransactionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        TccTransactionRegistry registry = (TccTransactionRegistry) context.getAttribute(CONTEXT_KEY);
        if (registry == null) {
            registry = new TccTransactionRegistry();
            context.setAttribute(CONTEXT_KEY, registry);
        }
        return registry;
    }

    @Override
    protected String normalizeKey(String txnGroup) {
        return TccHelper.normalizeTxnGroup(txnGroup);
    }
}