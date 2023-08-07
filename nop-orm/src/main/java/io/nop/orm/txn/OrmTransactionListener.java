/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.txn;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;

import javax.inject.Inject;

/**
 * 与Transaction集成
 */
public class OrmTransactionListener implements ITransactionListener {
    private IOrmTemplate ormTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public void onBeforeCommit(ITransaction txn) {
        ormTemplate.flushSession();
    }

    @Override
    public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
        if (status != CompleteStatus.COMMIT) {
            IOrmSession session = ormTemplate.currentSession();
            // 如果执行过程中出现异常，则清空session缓存。这个行为与Spring+Hibernate类似。
            // 如果不清空，则可能因为各种原因导致session中的数据与数据库中的数据不一致，难以处理
            session.clear();
        }
    }
}