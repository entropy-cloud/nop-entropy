package io.nop.dao.txn;

import io.nop.dao.txn.impl.AbstractTransaction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C2-016（plan 2553 裁定 D）钉：事务状态机 CAS。
 * pin-a：open 双开 fail-fast；pin-b：markRollbackOnly 后 commit → 自动 rollback 语义 + WARN
 * （语义变更显式断言，Fix+Decision ①）；pin-c：二次 commit no-op（listener 不重复通知——
 * testMultipleCommit 改写钉的机制面佐证）。
 * 变异红形态：状态机擦除（open 无 CAS）后 pin-a 红。
 */
public class TestTransactionStateCas {

    static class TestTxn extends AbstractTransaction {
        TestTxn(String txnGroup) {
            super(txnGroup);
        }

        @Override
        protected void doOpen() {
        }

        @Override
        protected void doCommit() {
        }

        @Override
        protected void doRollback(Throwable error) {
        }

        @Override
        protected void doClose() {
        }
    }

    @Test
    public void pin_a_doubleOpenFailsFast() {
        TestTxn txn = new TestTxn("cas-test");
        txn.open();
        assertThrows(Exception.class, txn::open, "double open must fail fast (CAS)");
        txn.close();
    }

    @Test
    public void pin_b_rollbackOnlyCommitRunsRollbackSemantics() {
        TestTxn txn = new TestTxn("cas-test");
        txn.open();
        txn.markRollbackOnly(null);
        // 裁定 D 语义变更：不再抛 ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT——自动走 rollback 语义
        txn.commit();
        assertEquals(false, txn.isTransactionOpened(), "transaction must be terminal after rollback-semantics commit");
    }

    @Test
    public void pin_c_doubleCommitNoopNoDoubleNotify() {
        TestTxn txn = new TestTxn("cas-test");
        txn.open();
        AtomicInteger notifyCount = new AtomicInteger();
        txn.addListener(new ITransactionListener() {
            @Override
            public int order() {
                return 0;
            }

            @Override
            public void onAfterCommit(ITransaction t) {
                notifyCount.incrementAndGet();
            }
        });
        txn.commit();
        txn.commit(); // 二次：no-op-with-WARN
        assertEquals(1, notifyCount.get(), "second commit must not re-fire listeners");
        txn.close();
    }

    @Test
    public void pin_d_listenerRegistrationAtomic() throws Exception {
        // 2.3-C2-014：并发注册不丢监听器（check-then-act 变异红）
        TestTxn txn = new TestTxn("cas-test");
        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        for (int i = 0; i < racers; i++) {
            final int idx = i;
            pool.execute(() -> {
                try {
                    start.await();
                    txn.addListener(new ITransactionListener() {
                        @Override
                        public int order() {
                            return idx;
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        Field f = AbstractTransaction.class.getDeclaredField("listeners");
        f.setAccessible(true);
        Object listeners = f.get(txn);
        assertEquals(racers, ((java.util.List<?>) listeners).size(),
                "concurrent registrations must not lose listeners (C2-014)");
    }
}
