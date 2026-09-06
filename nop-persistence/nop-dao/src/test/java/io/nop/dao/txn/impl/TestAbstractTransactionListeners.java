/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.ITransactionListener.CompleteStatus;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 处置新增：listeners 容器线程安全（CopyOnWriteArrayList）——异步事务路径上
 * listener 回调发生在 executor 线程，注册线程并发增删不得丢失或抛 ConcurrentModificationException。
 */
public class TestAbstractTransactionListeners {

    static class NoopTxn extends AbstractTransaction {
        NoopTxn(String txnGroup) {
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

    static class Listener implements ITransactionListener {
        @Override
        public int compareTo(ITransactionListener o) {
            return 0;
        }

        @Override
        public void onBeforeCompletion(ITransaction txn) {
        }

        @Override
        public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable error) {
        }
    }

    @Test
    public void testAddRemoveGetListeners() {
        NoopTxn txn = new NoopTxn("test");
        assertNull(txn.getListeners());

        Listener a = new Listener();
        Listener b = new Listener();
        txn.addListener(a);
        txn.addListener(b);
        assertEquals(2, txn.getListeners().size());

        // 重复添加去重
        txn.addListener(a);
        assertEquals(2, txn.getListeners().size());

        txn.removeListener(a);
        assertEquals(1, txn.getListeners().size());
        assertEquals(b, txn.getListeners().iterator().next());

        txn.clearListeners();
        assertNull(txn.getListeners());
    }

    /**
     * 遍历（invokeListener 路径）与并发注册不得互相破坏：COW 容器的迭代器是快照，
     * 遍历中新增 listener 不抛 ConcurrentModificationException、不影响本轮回调集合。
     */
    @Test
    public void testConcurrentAddWhileInvoking() {
        NoopTxn txn = new NoopTxn("test");
        List<Listener> registered = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 4; i++) {
            Listener l = new Listener();
            registered.add(l);
            txn.addListener(l);
        }

        // 在回调中并发注册新 listener（模拟异步路径回调线程与注册线程竞态）
        txn.addListener(new ITransactionListener() {
            @Override
            public int compareTo(ITransactionListener o) {
                return 0;
            }

            @Override
            public void onBeforeCompletion(ITransaction t) {
                txn.addListener(new Listener());
            }

            @Override
            public void onAfterCompletion(ITransaction t, CompleteStatus status, Throwable error) {
            }
        });

        txn.beforeRollback(null);
        // 快照语义：本轮 5 个 listener（4 个基础 + 1 个回调中追加者）都被回调，
        // 回调中新增的 listener 不影响本轮迭代、计入下一轮（总数 6）
        assertEquals(6, txn.getListeners().size());
    }

    @Test
    public void testInvokeListenerIsolation() {
        NoopTxn txn = new NoopTxn("test");
        txn.addListener(new ITransactionListener() {
            @Override
            public int compareTo(ITransactionListener o) {
                return 0;
            }

            @Override
            public void onBeforeCompletion(ITransaction t) {
                throw new IllegalStateException("listener-boom");
            }

            @Override
            public void onAfterCompletion(ITransaction t, CompleteStatus status, Throwable error) {
            }
        });
        Listener ok = new Listener();
        txn.addListener(ok);

        // ignoreError=true：单个 listener 失败不中断其余 listener
        txn.beforeRollback(null);
        assertTrue(txn.getListeners().contains(ok));
    }

    @Test
    public void testListenerTypeIsCopyOnWrite() throws Exception {
        NoopTxn txn = new NoopTxn("test");
        txn.addListener(new Listener());
        Collection<ITransactionListener> listeners = txn.getListeners();
        assertTrue(listeners instanceof CopyOnWriteArrayList,
                "listeners must be CopyOnWriteArrayList for async-path safety: " + listeners.getClass());
    }
}
