/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.decorator;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.dao.txn.ITransactionTemplate;

import static io.nop.biz.BizConstants.TRANSACTION_DECORATOR_PRIORITY;

public class TransactionActionDecorator implements IServiceActionDecorator {
    private final ITransactionTemplate transactionTemplate;
    private final String txnGroup;
    private final TransactionPropagation propagation;
    private final boolean async;

    public TransactionActionDecorator(ITransactionTemplate transactionTemplate, String txnGroup,
                                      TransactionPropagation propagation, boolean async) {
        this.transactionTemplate = transactionTemplate;
        this.txnGroup = txnGroup;
        this.propagation = propagation;
        this.async = async;
    }

    @Override
    public int order() {
        return TRANSACTION_DECORATOR_PRIORITY;
    }

    @Override
    public IServiceAction decorate(IServiceAction action) {
        return (request, selection, context) -> {
            if (async) {
                return transactionTemplate.runInTransactionAsync(txnGroup, propagation, txn -> {
                    return FutureHelper.futureCall(() -> action.invoke(request, selection, context));
                });
            } else {
                return transactionTemplate.runInTransaction(txnGroup, propagation, txn -> {
                    return action.invoke(request, selection, context);
                });
            }
        };
    }
}
