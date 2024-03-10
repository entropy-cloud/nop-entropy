/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.api.core.annotations.core.Internal;
import io.nop.dao.dialect.IDialect;

/**
 * 应用程序应该通过ITransactionTemplate接口来使用事务，而不应该直接调用ITransactionManager。
 */
@Internal
public interface ITransactionManager {
    /**
     * 每个querySpace可能属于一个group。每次创建事务的时候，如果发现group的事务是打开的，则挂接到group的事务上作为子事务
     */
    String getMainTxnGroup(String querySpace);

    ITransaction getRegisteredTransaction(String txnGroup);

    /**
     * 将事务对象注册到线程上下文中，返回当前注册的事务对象
     */
    ITransaction registerTransaction(ITransaction txn);

    boolean unregisterTransaction(ITransaction txn);

    ITransaction newTransaction(String txnGroup);

    boolean isQuerySpaceDefined(String querySpace);

    IDialect getDialectForQuerySpace(String querySpace);
}