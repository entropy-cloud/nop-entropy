/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.concurrent.ContextualizedRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.txn.ITransaction;

@GlobalInstance
public class TransactionRegistry extends ContextualizedRegistry<String, ITransaction> {
    static final String CONTEXT_KEY = TransactionRegistry.class.getSimpleName();

    public static TransactionRegistry instance() {
        IContext context = ContextProvider.getOrCreateContext();
        TransactionRegistry registry = (TransactionRegistry) context.getAttribute(CONTEXT_KEY);
        if (registry == null) {
            registry = new TransactionRegistry();
            context.setAttribute(CONTEXT_KEY, registry);
        }
        return registry;
    }

    @Override
    protected String normalizeKey(String txnGroup) {
        if (StringHelper.isEmpty(txnGroup))
            txnGroup = DaoConstants.DEFAULT_TXN_GROUP;
        return txnGroup;
    }
}