/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.dao;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.impl.AbstractTransaction;
import io.nop.dao.txn.impl.TransactionRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 与spring事务机制集成。底层事务直接使用PlatformTransactionManager提供。
 */
public class NopSpringTransactionFactory implements ITransactionFactory {
    private final PlatformTransactionManager transactionManager;

    public NopSpringTransactionFactory(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public ITransaction newTransaction(String txnGroup) {
        return new SpringTransaction(txnGroup, null);
    }

    @Override
    public ITransaction getSynchronization(String txnGroup) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName(txnGroup);

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
                    ITransactionListener.CompleteStatus completeStatus = toCompleteStatus(status);
                    txn.afterCompletion(completeStatus);
                    TransactionRegistry.instance().remove(txn.getTxnGroup(), txn);
                    txn.close();
                }
            });
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

    class SpringTransaction extends AbstractTransaction {
        private TransactionStatus txn;

        public SpringTransaction(String txnGroup, TransactionStatus txn) {
            super(txnGroup);
            this.txn = txn;
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
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            txn = transactionManager.getTransaction(def);
        }

        @Override
        protected void doCommit() {
            if (txn != null)
                transactionManager.commit(txn);
        }

        @Override
        protected void doRollback(Throwable error) {
            if (txn != null) {
                transactionManager.rollback(txn);
            }
        }

        @Override
        protected void doClose() {
            if (txn != null) {
                if (!txn.isCompleted()) {
                    transactionManager.rollback(txn);
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