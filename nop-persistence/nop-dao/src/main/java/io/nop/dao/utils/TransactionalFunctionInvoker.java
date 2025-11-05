/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.utils;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.util.Guard;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.functional.IFunctionInvoker;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class TransactionalFunctionInvoker implements IFunctionInvoker, IAsyncFunctionInvoker {
    private ITransactionTemplate transactionTemplate;
    private String txnGroup;
    private TransactionPropagation propagation = TransactionPropagation.REQUIRED;

    public TransactionalFunctionInvoker() {
    }

    public TransactionalFunctionInvoker(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = Guard.notNull(transactionTemplate, "transactionTemplate");
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setTxnGroup(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public void setPropagation(TransactionPropagation propagation) {
        this.propagation = propagation;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        return transactionTemplate.runInTransactionAsync(txnGroup, propagation, txn -> {
            return task.apply(request);
        });
    }

    @Override
    public <R, T> T invoke(Function<R, T> fn, R request) {
        return transactionTemplate.runInTransaction(txnGroup, propagation, txn -> {
            return fn.apply(request);
        });
    }
}
