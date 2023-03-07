/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.decorator;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.biz.BizConstants;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizTxnModel;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.reflect.IFunctionModel;
import io.nop.dao.txn.ITransactionTemplate;

import java.util.List;

public class TransactionActionDecoratorCollector implements IActionDecoratorCollector {
    private final ITransactionTemplate transactionTemplate;

    public TransactionActionDecoratorCollector(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void collectDecorator(IFunctionModel funcModel, List<IServiceActionDecorator> decorators) {
        Transactional transactional = funcModel.getAnnotation(Transactional.class);
        if (transactional != null) {
            String txnGroup = transactional.txnGroup();
            TransactionPropagation propagation = transactional.propagation();
            decorators.add(
                    new TransactionActionDecorator(transactionTemplate, txnGroup, propagation, funcModel.isAsync()));
        }
    }

    @Override
    public void collectDecorator(BizActionModel actionModel, List<IServiceActionDecorator> decorators) {
        BizTxnModel txnModel = actionModel.getTxn();
        if (txnModel != null) {
            boolean defaultTransactional = BizConstants.BIZ_ACTION_TYPE_MUTATION.equals(actionModel.getType());
            boolean transactional = txnModel.getTransactional() != null ? txnModel.getTransactional()
                    : defaultTransactional;

            if (transactional) {
                String txnGroup = txnModel.getTxnGroup();
                TransactionPropagation propagation = txnModel.getPropagation();
                decorators.add(new TransactionActionDecorator(transactionTemplate, txnGroup, propagation,
                        actionModel.isAsync()));
            }
        }
    }
}
