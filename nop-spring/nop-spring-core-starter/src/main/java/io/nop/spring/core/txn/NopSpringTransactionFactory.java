/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.core.txn;

import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.impl.AbstractTransaction;
import io.nop.dao.txn.impl.TransactionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 与spring事务机制集成。底层事务直接使用PlatformTransactionManager提供。
 */
@Component("nopTransactionFactory")
@ConditionalOnClass({PlatformTransactionManager.class, ITransaction.class})
@ConditionalOnProperty("nop.dao.use-parent-transaction-factory")
public class NopSpringTransactionFactory implements ITransactionFactory {
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private IDialect dialect;

    public NopSpringTransactionFactory(PlatformTransactionManager transactionManager, DataSource dataSource) {
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        if (dialect == null) {
            this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        }
        return dialect;
    }

    @Override
    public Connection openConnection(String txnGroup) {
        // 使用Spring的工具类获取连接，确保连接与当前事务关联
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public ITransaction newTransaction(String txnGroup) {
        // 如果已经有外部事务，则返回同步事务
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return getSynchronization(txnGroup);
        }
        return new SpringTransaction(txnGroup, null);
    }

    @Override
    public ITransaction getSynchronization(String txnGroup) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 检查是否已经注册过同步事务
            SpringTransaction existingTxn = (SpringTransaction) TransactionRegistry.instance().get(txnGroup);
            if (existingTxn != null) {
                return existingTxn;
            }

            // 创建新的事务同步
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName(txnGroup);
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

            TransactionStatus txnStatus = transactionManager.getTransaction(def);
            SpringTransaction txn = new SpringTransaction(txnGroup, txnStatus);

            TransactionRegistry.instance().put(txnGroup, txn);

            // TransactionTemplateImpl中认为txn为已注册的transaction，不会主动释放，因此需要利用spring的事务同步机制来释放资源
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    txn.beforeCommit();
                }

                @Override
                public void afterCommit() {
                    txn.afterCommit();
                }

                @Override
                public void beforeCompletion() {
                    txn.beforeCompletion();
                }

                @Override
                public void afterCompletion(int status) {
                    try {
                        TransactionRegistry.instance().remove(txn.getTxnGroup(), txn);
                        ITransactionListener.CompleteStatus completeStatus = toCompleteStatus(status);
                        txn.afterCompletion(completeStatus);
                    } finally {
                        txn.close();
                    }
                }
            });

            return txn;
        }
        return null;
    }

    static ITransactionListener.CompleteStatus toCompleteStatus(int status) {
        switch (status) {
            case TransactionSynchronization.STATUS_COMMITTED:
                return ITransactionListener.CompleteStatus.COMMIT;
            case TransactionSynchronization.STATUS_ROLLED_BACK:
                return ITransactionListener.CompleteStatus.ROLLBACK;
            default:
                return ITransactionListener.CompleteStatus.UNKNOWN;
        }
    }

    class SpringTransaction extends AbstractTransaction implements IJdbcTransaction {
        private TransactionStatus txn;

        public SpringTransaction(String txnGroup, TransactionStatus txn) {
            super(txnGroup);
            this.txn = txn;
        }

        @Override
        public Connection getConnection() {
            if (txn == null) {
                open();
            }
            // 使用Spring的工具类获取连接，确保连接与当前事务关联
            return DataSourceUtils.getConnection(dataSource);
        }

        @Override
        public boolean isTransactionOpened() {
            return txn != null;
        }

        @Override
        protected void doOpen() {
            if (txn != null)
                return;

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName(getTxnGroup());

            // 如果有外部事务，则参与外部事务
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            } else {
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            }

            txn = transactionManager.getTransaction(def);
        }

        @Override
        protected void doCommit() {
            if (txn != null)
                transactionManager.commit(txn);
        }

        @Override
        protected void doRollback(Throwable error) {
            if (txn != null && !txn.isCompleted()) {
                transactionManager.rollback(txn);
            }
        }

        @Override
        protected void doClose() {
            if (txn != null) {
                if (!txn.isCompleted()) {
                    if (txn.isRollbackOnly()) {
                        transactionManager.rollback(txn);
                    } else {
                        transactionManager.commit(txn);
                    }
                }
                txn = null;
            }
        }

        @Override
        public void markRollbackOnly(Throwable error) {
            if (txn != null) {
                super.markRollbackOnly(error);
                txn.setRollbackOnly();
            }
        }

        @Override
        public boolean isRollbackOnly() {
            if (txn == null)
                return false;
            return txn.isRollbackOnly();
        }
    }
}