/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;

import java.sql.Connection;

public interface ITransactionFactory extends IDialectProvider {
    ITransaction newTransaction(String txnGroup);

    IDialect getDialectForQuerySpace(String txnGroup);

    default ITransaction getSynchronization(String txnGroup) {
        return null;
    }

    Connection openConnection(String txnGroup);
}
