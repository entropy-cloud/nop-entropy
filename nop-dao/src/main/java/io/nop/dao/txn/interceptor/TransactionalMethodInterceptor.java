/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.interceptor;

import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.core.reflect.aop.IMethodInvocation;
import io.nop.dao.txn.ITransactionTemplate;

import java.util.concurrent.CompletionStage;

public class TransactionalMethodInterceptor implements IMethodInterceptor {
    private final ITransactionTemplate txnTemplate;

    public TransactionalMethodInterceptor(ITransactionTemplate txnTemplate) {
        this.txnTemplate = txnTemplate;
    }

    @Override
    public Object invoke(IMethodInvocation inv) throws Exception {
        IFunctionModel method = inv.getMethod();
        Transactional transactional = method.getAnnotation(Transactional.class);
        if (transactional == null)
            return inv.proceed();

        if (method.isAsync()) {
            return txnTemplate.runInTransactionAsync(transactional.txnGroup(), transactional.propagation(), txn -> {
                try {
                    return (CompletionStage<?>) inv.proceed();
                } catch (Exception e) {
                    throw NopException.adapt(e);
                }
            });
        }

        return txnTemplate.runInTransaction(transactional.txnGroup(), transactional.propagation(), txn -> {
            try {
                return inv.proceed();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        });
    }
}