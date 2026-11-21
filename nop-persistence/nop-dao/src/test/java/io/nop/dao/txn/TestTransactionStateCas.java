package io.nop.dao.txn;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.DaoErrors;
import io.nop.dao.txn.impl.AbstractTransaction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ---------------- plan 2587 钉 ----------------

    static class CountingTxn extends TestTxn {
        final AtomicInteger doCloseCount = new AtomicInteger();

        CountingTxn() {
            super("cas-test-2587");
        }

        @Override
        protected void doClose() {
            doCloseCount.incrementAndGet();
        }
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        assertTrue(e instanceof NopException, "expected NopException but got: " + e);
        assertEquals(expected.getErrorCode(), ((NopException) e).getErrorCode(),
                "error code must be " + expected.getErrorCode() + " but was " + ((NopException) e).getErrorCode());
    }

    /**
     * pin-2587-a：终态后再 rollback → ERR_TXN_ALREADY_FINISHED（不再误报「已启动」，2587 错误码裁定）。
     */
    @Test
    public void pin_2587_a_rollbackAfterRollbackUsesAlreadyFinishedCode() {
        TestTxn txn = new TestTxn("cas-test-2587");
        txn.open();
        txn.rollback(null);
        NopException e = assertThrows(NopException.class, () -> txn.rollback(null));
        assertErrorCode(e, DaoErrors.ERR_TXN_ALREADY_FINISHED);
    }

    /**
     * pin-2587-b：终态（ROLLED_BACK，非 COMMITTED）后再 commit → ERR_TXN_ALREADY_FINISHED。
     */
    @Test
    public void pin_2587_b_commitAfterRollbackUsesAlreadyFinishedCode() {
        TestTxn txn = new TestTxn("cas-test-2587");
        txn.open();
        txn.rollback(null);
        NopException e = assertThrows(NopException.class, txn::commit);
        assertErrorCode(e, DaoErrors.ERR_TXN_ALREADY_FINISHED);
    }

    /**
     * pin-2587-c：双 close no-op——onClose 监听器与 doClose 仅执行一次，不抛异常。
     */
    @Test
    public void pin_2587_c_doubleCloseIsNoop() {
        CountingTxn txn = new CountingTxn();
        AtomicInteger closeNotify = new AtomicInteger();
        txn.addListener(new ITransactionListener() {
            @Override
            public int order() {
                return 0;
            }

            @Override
            public void onClose(ITransaction t) {
                closeNotify.incrementAndGet();
            }
        });
        txn.open();
        txn.close();
        txn.close(); // 双 close：no-op，不得重复通知或抛错
        assertEquals(1, closeNotify.get(), "second close must not re-fire onClose listeners");
        assertEquals(1, txn.doCloseCount.get(), "second close must not re-run doClose");
    }

    /**
     * pin-2587-d：并发 close 竞态——CAS 门保证 doClose 恰好一次，双 close 均不抛异常。
     */
    @Test
    public void pin_2587_d_concurrentCloseSingleDoClose() throws Exception {
        CountingTxn txn = new CountingTxn();
        txn.open();
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicInteger closeErrors = new AtomicInteger();
        for (int i = 0; i < racers; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    txn.close(); // close 经 safeClose 在 finally 调用，任何线程都不得抛
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    closeErrors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(0, closeErrors.get(), "close must never throw (safeClose contract)");
        assertEquals(1, txn.doCloseCount.get(), "CAS gate must make doClose run exactly once");
    }

    /**
     * pin-2587-e：rollback-only 双路径钉——同步 commit() 自动 rollback 语义（不抛）；
     * commitAsync() 与公开 beforeCommit() 仍以 ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT 拒绝
     * （分叉为已登记的已知行为，owner doc transaction-boundaries.md 迁移限定条目）。
     */
    @Test
    public void pin_2587_e_rollbackOnlySyncVsAsyncDivergencePinned() throws Exception {
        // 同步路径：自动 rollback（pin_b 已覆盖终态，这里补错误码面：不抛任何 NopException）
        TestTxn syncTxn = new TestTxn("cas-test-2587");
        syncTxn.open();
        syncTxn.markRollbackOnly(null);
        syncTxn.commit();
        assertFalse(syncTxn.isTransactionOpened(), "sync commit on rollback-only must terminate via rollback");

        // async 路径：rejected future，错误码 ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT
        TestTxn asyncTxn = new TestTxn("cas-test-2587");
        asyncTxn.open();
        asyncTxn.markRollbackOnly(null);
        try {
            asyncTxn.commitAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            throw new AssertionError("commitAsync on rollback-only must reject");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertErrorCode(expected.getCause(), DaoErrors.ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT);
        }

        // beforeCommit 公开路径：同样抛 ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT
        TestTxn bcTxn = new TestTxn("cas-test-2587");
        bcTxn.open();
        bcTxn.markRollbackOnly(null);
        NopException e = assertThrows(NopException.class, bcTxn::beforeCommit);
        assertErrorCode(e, DaoErrors.ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT);
    }
}
