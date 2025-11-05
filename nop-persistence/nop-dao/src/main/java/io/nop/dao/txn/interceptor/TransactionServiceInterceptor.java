/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.interceptor;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.ApiResponse;

import io.nop.dao.txn.ITransactionTemplate;
import io.nop.rpc.api.IRpcServiceInterceptor;
import io.nop.rpc.api.IRpcServiceInvocation;

import java.util.concurrent.CompletionStage;

public class TransactionServiceInterceptor implements IRpcServiceInterceptor {
    private final ITransactionTemplate transactionTemplate;
    private final String querySpace;
    private final TransactionPropagation propagation;

    public TransactionServiceInterceptor(ITransactionTemplate transactionTemplate, String querySpace,
                                         TransactionPropagation propagation) {
        this.transactionTemplate = transactionTemplate;
        this.querySpace = querySpace;
        this.propagation = propagation;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        return transactionTemplate.runInTransactionAsync(querySpace, propagation, txn -> {
            return inv.proceedAsync();
        });
    }

    @Override
    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        return transactionTemplate.runInTransaction(querySpace, propagation, txn -> {
            return inv.proceed();
        });
    }
}
