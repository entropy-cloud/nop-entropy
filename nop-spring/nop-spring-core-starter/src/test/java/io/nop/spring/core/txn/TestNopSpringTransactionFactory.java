package io.nop.spring.core.txn;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: NopSpringTransactionFactory is the Spring
 * transaction bridge — outside a Spring-managed transaction it must hand out
 * fresh unopened transactions (no eager connection/transaction acquisition),
 * and the Spring synchronization status ints must translate losslessly onto
 * the platform's CompleteStatus enum.
 */
public class TestNopSpringTransactionFactory {

    /** Minimal DataSource stub: connections are never acquired in these paths. */
    static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("no connection expected in this test path");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("no connection expected in this test path");
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("no delegation expected in this test path");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /** Recording PlatformTransactionManager stub: any unexpected call flips a flag. */
    static final class RecordingTxnManager implements PlatformTransactionManager {
        boolean anyTransactionCall;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            anyTransactionCall = true;
            return null;
        }

        @Override
        public void commit(TransactionStatus status) {
            anyTransactionCall = true;
        }

        @Override
        public void rollback(TransactionStatus status) {
            anyTransactionCall = true;
        }
    }

    @Test
    public void testNewTransactionOutsideSpringTxnIsUnopenedAndLazy() {
        RecordingTxnManager txnManager = new RecordingTxnManager();
        NopSpringTransactionFactory factory = new NopSpringTransactionFactory(
                txnManager, new StubDataSource());

        ITransaction txn = factory.newTransaction("txnGroup-1");
        assertNotNull(txn, "a fresh transaction object is always handed out");
        assertEquals("txnGroup-1", txn.getTxnGroup());
        assertFalse(txn.isTransactionOpened(),
                "outside a Spring transaction the returned txn starts unopened");
        assertFalse(txn.isRollbackOnly());
        assertFalse(txnManager.anyTransactionCall,
                "creating the transaction must not touch the Spring txn manager");
    }

    @Test
    public void testGetSynchronizationOutsideSpringTxnReturnsNull() {
        NopSpringTransactionFactory factory = new NopSpringTransactionFactory(
                new RecordingTxnManager(), new StubDataSource());
        assertNull(factory.getSynchronization("txnGroup-2"),
                "no Spring-managed transaction active means no synchronization to join");
    }

    @Test
    public void testToCompleteStatusTranslation() {
        assertEquals(ITransactionListener.CompleteStatus.COMMIT,
                NopSpringTransactionFactory.toCompleteStatus(
                        TransactionSynchronization.STATUS_COMMITTED));
        assertEquals(ITransactionListener.CompleteStatus.ROLLBACK,
                NopSpringTransactionFactory.toCompleteStatus(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(ITransactionListener.CompleteStatus.UNKNOWN,
                NopSpringTransactionFactory.toCompleteStatus(999),
                "unrecognized statuses translate to UNKNOWN, never thrown away");
    }

    @Test
    public void testDialectLookupIsLazyPerFactory() {
        RecordingTxnManager txnManager = new RecordingTxnManager();
        StubDataSource dataSource = new StubDataSource();
        NopSpringTransactionFactory factory = new NopSpringTransactionFactory(txnManager, dataSource);

        assertNotNull(factory);
        assertFalse(txnManager.anyTransactionCall,
                "factory construction must not touch the Spring txn manager (lazy dialect wiring)");
    }
}
