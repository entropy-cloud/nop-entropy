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

import java.sql.Connection;

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
     * plan 2517 R2-14 裁定⑧（C2-013）：get+new+register 三步收敛为注册表级原子 create-if-absent。
     * compute 函数体内取 external synchronization ?? newTransaction（JTA 型外部同步器不被原子化遮蔽，
     * 且外部同步器不落注册表）；并发下败者不再覆盖胜者，自建事务由调用方按 newlyRegistered 判定收尾
     * （半个成功消除）。锁序约束：注册表锁只保护结构变更，不覆盖 task 执行与 commit。
     */
    default RegisteredTxn getOrCreateRegisteredTransaction(String txnGroup) {
        // 裁定⑧缺省退化实现：非原子两步（供既有 ITransactionManager 实现方渐进升级）；
        // DefaultTransactionManager 覆盖为注册表级原子形态
        ITransaction txn = getRegisteredTransaction(txnGroup);
        if (txn != null)
            return new RegisteredTxn(txn, false);
        txn = newTransaction(txnGroup);
        registerTransaction(txn);
        return new RegisteredTxn(txn, true);
    }

    /**
     * plan 2517 R2-14 裁定⑧：注销-执行-重挂窗口改挂起标记——registry entry 摘除并挂 suspended 位，
     * 查找侧跳过（runWithoutTransaction/NOT_SUPPORTED）。缺省实现退化为 unregister（既有实现方渐进升级）。
     */
    default void suspendTransaction(ITransaction txn) {
        unregisterTransaction(txn);
    }

    /**
     * plan 2517 R2-14 裁定⑧：恢复挂起的事务（摘除 suspended 位并重挂注册表）。缺省实现退化为 re-register。
     */
    default void resumeTransaction(ITransaction txn) {
        if (txn != null)
            registerTransaction(txn);
    }

    /**
     * 将事务对象注册到线程上下文中，返回当前注册的事务对象
     */
    ITransaction registerTransaction(ITransaction txn);

    boolean unregisterTransaction(ITransaction txn);

    ITransaction newTransaction(String txnGroup);

    boolean isQuerySpaceDefined(String querySpace);

    IDialect getDialectForQuerySpace(String querySpace);

    ITransactionFactory getTransactionFactory(String querySpace);

    /**
     * 打开一个不参与当前事务的独立连接。
     *
     * <p><b>易误用告警（plan 2306 项 34 设计裁定）</b>：本方法返回的是连接池中的全新
     * 连接（autocommit），<em>不会</em>挂接到 querySpace 已注册的活跃事务——在事务内
     * 经此连接执行的写入无法被事务 rollback 撤回。事务内的数据库访问必须通过
     * {@code IJdbcTransaction.getConnection()} 获取事务绑定连接。</p>
     */
    Connection openConnection(String querySpace);

    /**
     * getOrCreateRegisteredTransaction 的结果载体：txn 为当前生效事务；newlyRegistered 标记是否由本次调用
     * 新建并注册（true 时调用方负责 cleanup 的 unregister/close 收尾）
     */
    class RegisteredTxn {
        private final ITransaction txn;
        private final boolean newlyRegistered;

        public RegisteredTxn(ITransaction txn, boolean newlyRegistered) {
            this.txn = txn;
            this.newlyRegistered = newlyRegistered;
        }

        public ITransaction getTxn() {
            return txn;
        }

        public boolean isNewlyRegistered() {
            return newlyRegistered;
        }
    }
}