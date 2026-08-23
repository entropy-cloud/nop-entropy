/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.util.FutureHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.txn.impl.AbstractTransaction;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证异步事务路径与同步路径在"已存在主事务 + 新建子事务"场景下的回滚行为一致：
 * 子任务失败时不应回滚整个主事务组，主事务由它的owner负责回滚
 */
public class TestTransactionTemplateAsync {

    private FakeTransactionManager transactionManager;
    private TransactionTemplateImpl template;
    private RecordingTxn mainTxn;

    @BeforeEach
    public void setUp() {
        transactionManager = new FakeTransactionManager();
        transactionManager.mainGroups.put("sec", "main");

        template = new TransactionTemplateImpl();
        template.setTransactionManager(transactionManager);

        mainTxn = new RecordingTxn("main");
        mainTxn.open();
        transactionManager.registerTransaction(mainTxn);
    }

    @AfterEach
    public void tearDown() {
        transactionManager.registered.clear();
    }

    @Test
    public void testAsyncSubTxnFailDoesNotRollbackMainGroup() {
        CompletionStage<Object> future = template.runInTransactionAsync("sec", TransactionPropagation.REQUIRED,
                txn -> {
                    throw new IllegalStateException("sub-task-fail");
                });

        assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(future));

        // 子任务失败不得回滚整个主事务组
        assertEquals(0, mainTxn.rollbackCount);
        assertTrue(mainTxn.opened, "main txn must remain open for its owner");

        // 子事务被挂接到主事务组
        assertEquals(1, mainTxn.getSubTransactions().size());
    }

    @Test
    public void testAsyncGroupNewlyCreatedRollsBackGroup() {
        // 没有已注册的主事务时，本层新建事务组，失败时必须回滚
        transactionManager.registered.clear();

        assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(
                template.runInTransactionAsync("main", TransactionPropagation.REQUIRED, t -> {
                    throw new IllegalStateException("task-fail");
                })));

        RecordingTxn txn = transactionManager.lastCreated;
        assertTrue(txn.rollbackCount >= 1, "newly created txn must be rolled back");
        assertFalse(transactionManager.registered.containsKey("main"), "txn must be unregistered after completion");
    }

    static class RecordingTxn extends AbstractTransaction {
        int rollbackCount;
        int commitCount;
        boolean opened;
        boolean closed;

        RecordingTxn(String txnGroup) {
            super(txnGroup);
        }

        @Override
        protected void doOpen() {
            opened = true;
        }

        @Override
        protected void doCommit() {
            commitCount++;
        }

        @Override
        protected void doRollback(Throwable error) {
            rollbackCount++;
        }

        @Override
        protected void doClose() {
            closed = true;
        }
    }

    static class FakeTransactionManager implements ITransactionManager {
        final Map<String, String> mainGroups = new HashMap<>();
        final Map<String, ITransaction> registered = new HashMap<>();
        RecordingTxn lastCreated;

        @Override
        public String getMainTxnGroup(String querySpace) {
            return mainGroups.get(querySpace);
        }

        @Override
        public ITransaction getRegisteredTransaction(String txnGroup) {
            return registered.get(txnGroup);
        }

        @Override
        public ITransaction registerTransaction(ITransaction txn) {
            return registered.put(txn.getTxnGroup(), txn);
        }

        @Override
        public boolean unregisterTransaction(ITransaction txn) {
            return registered.remove(txn.getTxnGroup(), txn);
        }

        @Override
        public ITransaction newTransaction(String txnGroup) {
            lastCreated = new RecordingTxn(txnGroup);
            return lastCreated;
        }

        @Override
        public boolean isQuerySpaceDefined(String querySpace) {
            return true;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return null;
        }

        @Override
        public ITransactionFactory getTransactionFactory(String querySpace) {
            return null;
        }

        @Override
        public Connection openConnection(String querySpace) {
            return null;
        }
    }
}
