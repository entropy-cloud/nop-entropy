/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.txn.impl;

import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;

import static io.nop.dao.DaoConstants.SEQ_TXN_ID;

public class GroupTransactionFactory implements ITransactionFactory {
    private final ISequenceGenerator txnIdGenerator;

    public GroupTransactionFactory(ISequenceGenerator txnIdGenerator) {
        this.txnIdGenerator = txnIdGenerator;
    }

    String genTxnId() {
        return txnIdGenerator.generateString(SEQ_TXN_ID, true);
    }

    @Override
    public ITransaction newTransaction(String querySpace) {
        return new GroupTransaction(querySpace, genTxnId());
    }
}