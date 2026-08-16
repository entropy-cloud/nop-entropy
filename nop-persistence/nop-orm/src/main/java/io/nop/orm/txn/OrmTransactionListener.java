/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.txn;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;

import jakarta.inject.Inject;

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
        // ormTemplate 为 ioc:lazy-property（规避事务管理器/监听器/OrmTemplate 循环依赖）。
        // 容器 bean 创建阶段（如 DataInitInitializer 执行 _init-data/*.sql 时）lazy 属性尚未赋值，
        // 此时事务为纯 JDBC 原始 SQL，无 ORM session 绑定，跳过 flush 与已装配后行为等价。
        if (ormTemplate != null)
            ormTemplate.flushSession();
    }

    @Override
    public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
        if (ormTemplate == null)
            return;
        if (status != CompleteStatus.COMMIT) {
            IOrmSession session = ormTemplate.currentSession();
            // 如果执行过程中出现异常，则清空session缓存。这个行为与Spring+Hibernate类似。
            // 如果不清空，则可能因为各种原因导致session中的数据与数据库中的数据不一致，难以处理
            if (session != null)
                session.clear();
        }
    }
}