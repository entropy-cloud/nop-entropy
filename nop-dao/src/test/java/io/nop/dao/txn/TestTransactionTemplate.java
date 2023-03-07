/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.txn;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.mutable.MutableInt;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static io.nop.dao.DaoErrors.ERR_TXN_NOT_ALLOW_TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTransactionTemplate extends JdbcTestCase {

    @Test
    public void testSupports() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn -> {
            assertTrue(!txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn2 -> {
                assertTrue(!txn2.isTransactionOpened());
                assertTrue(conn == jdbc().currentConnection(null));
                assertTrue(conn == ((IJdbcTransaction) txn2).getConnection());
                try {
                    assertTrue(conn.getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });

            return null;
        });
        checkNoActive();
    }

    @Test
    public void testSupportsInTxn() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn2 -> {
                assertTrue(txn2.isTransactionOpened());
                assertTrue(conn == jdbc().currentConnection(null));
                assertTrue(conn == ((IJdbcTransaction) txn2).getConnection());
                try {
                    assertTrue(!conn.getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });

            return null;
        });
        checkNoActive();
    }

    @Test
    public void testRequiresInTxn() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn2 -> {
                assertTrue(txn2.isTransactionOpened());
                assertTrue(txn == txn2);
                assertTrue(conn == jdbc().currentConnection(null));
                assertTrue(conn == ((IJdbcTransaction) txn2).getConnection());
                try {
                    assertTrue(!conn.getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });

            return null;
        });
        checkNoActive();
    }

    @Test
    public void testRequiresNew() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn -> {
            assertTrue(!txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn2 -> {
                assertTrue(txn != txn2);
                assertTrue(txn2.isTransactionOpened());
                assertTrue(conn != jdbc().currentConnection(null));
                assertTrue(conn != ((IJdbcTransaction) txn2).getConnection());

                try {
                    assertTrue(!jdbc().currentConnection(null).getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });

            return null;
        });
        checkNoActive();
    }

    @Test
    public void testRequiresNewInTxn() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn2 -> {
                assertTrue(txn != txn2);
                assertTrue(txn2.isTransactionOpened());
                assertTrue(conn != jdbc().currentConnection(null));
                assertTrue(conn != ((IJdbcTransaction) txn2).getConnection());

                try {
                    assertTrue(!jdbc().currentConnection(null).getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }

                assertEquals(2, getActiveConnections());
                return null;
            });

            assertEquals(1, getActiveConnections());
            return null;
        });
        checkNoActive();
    }

    @Test
    public void testMandatoryInTxn() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);

            txn().runInTransaction(null, TransactionPropagation.MANDATORY, txn2 -> {
                assertTrue(txn == txn2);
                assertTrue(txn2.isTransactionOpened());
                assertTrue(conn == jdbc().currentConnection(null));
                assertTrue(conn == ((IJdbcTransaction) txn2).getConnection());

                try {
                    assertTrue(!jdbc().currentConnection(null).getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }

                assertEquals(1, getActiveConnections());
                return null;
            });

            assertEquals(1, getActiveConnections());
            return null;
        });
        checkNoActive();
    }

    @Test
    public void testNever() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.NEVER, txn -> {
            assertTrue(txn == null);

            Connection conn = jdbc().currentConnection(null);
            assertTrue(conn == null);

            txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn2 -> {
                assertTrue(!txn2.isTransactionOpened());
                assertTrue(conn != jdbc().currentConnection(null));
                try {
                    assertTrue(jdbc().currentConnection(null).getAutoCommit());
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }

                assertEquals(1, getActiveConnections());
                return null;
            });

            assertEquals(0, getActiveConnections());
            return null;
        });
        checkNoActive();
    }

    @Test
    public void testNeverInTxn() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);
            assertTrue(conn != null);

            try {
                txn().runInTransaction(null, TransactionPropagation.NEVER, txn2 -> {
                    return null;
                });
                assertTrue(false);
            } catch (NopException e) {
                assertEquals(ERR_TXN_NOT_ALLOW_TRANSACTION.getErrorCode(), e.getErrorCode());
            }

            assertEquals(1, getActiveConnections());
            return null;
        });
        checkNoActive();
    }

    @Test
    public void testNotSupported() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            Connection conn = jdbc().currentConnection(null);
            assertTrue(conn != null);

            txn().runInTransaction(null, TransactionPropagation.NOT_SUPPORTED, txn2 -> {
                assertTrue(txn2 == null);
                assertTrue(null == jdbc().currentConnection(null));
                assertEquals(1, getActiveConnections());
                return null;
            });

            assertEquals(1, getActiveConnections());
            return null;
        });
        checkNoActive();
    }

    @Test
    public void testRollback() {
        checkNoActive();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            jdbc().executeUpdate(new SQL("update my_entity set a=44 where id = 1 "));

            // 抛出异常就会触发回滚
            try {
                txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn2 -> {
                    throw new RuntimeException("error");
                });
            } catch (Exception e) {

            }
            // 回滚完毕后rollbackOnly恢复为false
            assertTrue(!txn.isRollbackOnly());

            // 缺省情况下commit或者rollback都会释放连接
            assertEquals(0, getActiveConnections());

            jdbc().executeUpdate(new SQL("update my_entity set b=44 where id = 1 "));

            assertEquals(1, getActiveConnections());
            return null;
        });
        assertEquals(3L, jdbc().findLong(new SQL("select a from my_entity where id=1"), 0L));
        assertEquals(44L, jdbc().findLong(new SQL("select b from my_entity where id=1"), 0L));
        checkNoActive();
    }

    @Test
    public void testCommit() {
        checkNoActive();
        MutableInt count = new MutableInt();
        txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
            assertTrue(txn.isTransactionOpened());

            txn().getRegisteredTransaction(null).addListener(new ITransactionListener() {
                @Override
                public void onBeforeCommit(ITransaction txn) {
                    count.getAndIncrement();
                }
            });

            jdbc().executeUpdate(new SQL("update my_entity set a=44 where id = 1 "));

            // 缺省情况下commit或者rollback都会释放连接
            assertEquals(1, getActiveConnections());

            jdbc().executeUpdate(new SQL("update my_entity set b=44 where id = 1 "));

            assertEquals(1, getActiveConnections());
            return null;
        });
        assertEquals(1, count.get());
        assertEquals(44L, jdbc().findLong(new SQL("select a from my_entity where id=1"), 0L));
        assertEquals(44L, jdbc().findLong(new SQL("select b from my_entity where id=1"), 0L));
        checkNoActive();
    }
}
