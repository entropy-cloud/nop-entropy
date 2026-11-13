package io.nop.dao.txn;

import io.nop.dao.txn.impl.AbstractTransaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C2-014（plan 2553 裁定 C）钉：listener 惰性 init 收进 synchronized(this)（volatile
 * CopyOnWriteArrayList）——并发注册全部可见，open 后注册的监听器 commit 时恰好通知一次。
 * 变异红形态：check-then-act 惰性 init（并发注册互相覆盖丢监听器）后 pin-a 红。
 */
public class TestTransactionListenerAtomic {

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

    static class CountingListener implements ITransactionListener {
        final AtomicInteger commits = new AtomicInteger();

        @Override
        public int order() {
            return 0;
        }

        @Override
        public void onAfterCommit(ITransaction t) {
            commits.incrementAndGet();
        }
    }

    /**
     * pin-a：open 后 16 个线程并发 addListener，全部注册成功且 commit 时各通知恰好一次。
     * 修复前 check-then-act 惰性 init（if(listeners==null) listeners=new...; listeners.add）
     * 在并发注册下可互相覆盖丢监听器。
     */
    @Test
    public void pin_a_concurrentAddListenerAllRegistered() throws Exception {
        TestTxn txn = new TestTxn("listener-atomic-test");
        txn.open();

        int racers = 16;
        List<CountingListener> listeners = new ArrayList<>(racers);
        for (int i = 0; i < racers; i++)
            listeners.add(new CountingListener());

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        for (CountingListener listener : listeners) {
            pool.execute(() -> {
                try {
                    start.await();
                    txn.addListener(listener);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "all concurrent registrations must finish");
        pool.shutdownNow();

        txn.commit();
        for (int i = 0; i < racers; i++) {
            assertEquals(1, listeners.get(i).commits.get(),
                    "listener #" + i + " must be registered exactly once and notified exactly once (C2-014)");
        }
        txn.close();
    }

    /**
     * pin-b：open 之后单线程注册的监听器在 commit 时恰好通知一次（惰性 init 延迟到首次
     * addListener，不依赖 open 时点初始化）。
     */
    @Test
    public void pin_b_listenerRegisteredAfterOpenVisibleAtCommit() {
        TestTxn txn = new TestTxn("listener-atomic-test");
        txn.open();

        CountingListener listener = new CountingListener();
        txn.addListener(listener);

        txn.commit();
        assertEquals(1, listener.commits.get(),
                "listener registered after open must fire exactly once at commit");
        txn.close();
    }
}
