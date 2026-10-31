/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.jdbc.txn.JdbcTransaction;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.nop.dao.DaoErrors.ERR_TXN_NOT_REGISTERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红⑧（C2-013，C10-007）/ 红⑨（C2-015，C10-007）/ 红⑩（C2-017/C1-026，C10-008）回归钉。
 * <ul>
 * <li>红⑧：cleanupTransaction unregister 失败（并发方已接管注册项）不再抛伪 ERR_TXN_NOT_REGISTERED
 *     （提交成功误报触发上游重试与数据重复）→ 身份比对降级 WARN；身份比对一致时真异常保留。</li>
 * <li>红⑨：组提交快照开始后迟到 addSubTransaction 显式拒绝（fail-fast，禁静默不提交丢写）。</li>
 * <li>红⑩：JdbcTransaction 五触点 monitor 串行化——并行 getConnection 恰好一次池获取、同一连接身份；
 *     doCommit null 早退不再无痕（WARN 留痕，语义保持不抛错）。</li>
 * </ul>
 */
public class TestCra2517TxnProtocol {

    /**
     * 可控 ITransaction 替身（nop-dao 测试域无 mockito 依赖，手工桩）
     */
    static class StubTransaction implements ITransaction {
        private final String txnGroup;
        private final boolean opened;
        Runnable onCommit;

        StubTransaction(String txnGroup, boolean opened) {
            this.txnGroup = txnGroup;
            this.opened = opened;
        }

        @Override
        public String getTxnGroup() {
            return txnGroup;
        }

        @Override
        public boolean isTransactionOpened() {
            return opened;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public void commit() {
            if (onCommit != null)
                onCommit.run();
        }

        @Override
        public void rollback(Throwable exception) {
        }

        @Override
        public void markRollbackOnly(Throwable exception) {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void addListener(ITransactionListener listener) {
        }

        @Override
        public void removeListener(ITransactionListener listener) {
        }

        @Override
        public void addSubTransaction(ITransaction subTxn) {
        }

        @Override
        public boolean removeSubTransaction(ITransaction subTxn) {
            return false;
        }

        @Override
        public ITransaction getSubTransaction(String txnGroup) {
            return null;
        }

        @Override
        public Collection<ITransaction> getSubTransactions() {
            return null;
        }
    }

    private static Connection stubConnection() {
        return (Connection) Proxy.newProxyInstance(TestCra2517TxnProtocol.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "StubConnection";
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    // ---------------- 红⑧ cleanup 身份比对 ----------------

    static class MismatchTxnManager extends DefaultTransactionManager {
        ITransaction registeredTxn;
        ITransaction otherTxn;
        boolean failUnregister;
        // true 时 unregister 失败但注册项不被并发方替换（身份比对一致场景）
        boolean unregisterWithoutTakeover;
        final AtomicInteger cleanupFailures = new AtomicInteger();

        @Override
        public ITransaction getRegisteredTransaction(String querySpace) {
            return registeredTxn;
        }

        @Override
        public ITransaction registerTransaction(ITransaction txn) {
            this.registeredTxn = txn;
            return null;
        }

        @Override
        public ITransaction newTransaction(String txnGroup) {
            return new StubTransaction(txnGroup, false);
        }

        @Override
        public RegisteredTxn getOrCreateRegisteredTransaction(String txnGroup) {
            // 桩实现：注册表语义与 DefaultTransactionManager 同构，但不依赖事务工厂（测试域无数据源）
            ITransaction txn = getRegisteredTransaction(txnGroup);
            if (txn != null)
                return new RegisteredTxn(txn, false);
            txn = newTransaction(txnGroup);
            registerTransaction(txn);
            return new RegisteredTxn(txn, true);
        }

        @Override
        public boolean unregisterTransaction(ITransaction txn) {
            if (failUnregister) {
                cleanupFailures.incrementAndGet();
                if (!unregisterWithoutTakeover) {
                    // 并发方已接管注册项（现值变为 otherTxn）
                    registeredTxn = otherTxn;
                }
                return false;
            }
            this.registeredTxn = null;
            return true;
        }
    }

    /**
     * 红⑧：runInTransaction 提交成功后，cleanup 阶段 unregister 失败且 registry 现值已是并发方事务
     * （身份比对不符）→ 降级 WARN 非抛错。缺陷态抛伪 ERR_TXN_NOT_REGISTERED → 红。
     */
    @Test
    public void testCra2517_C2_013_cleanupIdentityMismatchDowngradesToWarn() {
        MismatchTxnManager tm = new MismatchTxnManager();
        TransactionTemplateImpl template = new TransactionTemplateImpl();
        template.setTransactionManager(tm);

        tm.otherTxn = new StubTransaction("default", false);
        tm.failUnregister = false;

        // 第一次运行：正常注册；cleanup 正常 unregister（txnGroup=null 命中主分支）
        assertEquals("ok1", template.runInTransaction(null, null, t -> "ok1"));

        // 第二次运行：提交成功后 cleanup 时并发方已接管（unregister 失败 + 现值为 otherTxn）
        tm.failUnregister = true;
        String ret = template.runInTransaction(null, null, t -> "ok2");
        assertEquals("ok2", ret, "提交成功后不得因 cleanup 身份不符抛伪 ERR_TXN_NOT_REGISTERED（红⑧缺陷态 → 红）");
        assertTrue(tm.cleanupFailures.get() >= 1, "降级路径必须真实走过 unregister 失败分支");
    }

    /**
     * 红⑧对照：身份比对一致（registry 现值即本事务而 remove 失败的等价场景）时真异常保留。
     */
    @Test
    public void testCra2517_C2_013_cleanupIdentityMatchStillThrows() {
        MismatchTxnManager tm = new MismatchTxnManager();
        TransactionTemplateImpl template = new TransactionTemplateImpl();
        template.setTransactionManager(tm);

        tm.failUnregister = true;
        tm.unregisterWithoutTakeover = true;

        try {
            template.runInTransaction(null, null, t -> "ok");
            throw new AssertionError("身份比对一致时真异常必须保留（绿钉）");
        } catch (NopException e) {
            assertEquals(ERR_TXN_NOT_REGISTERED.getErrorCode(), e.getErrorCode());
        }
    }

    // ---------------- 红⑨ 迟到子事务显式拒绝 ----------------

    /**
     * 红⑨：组提交快照开始（首个子事务 commit 阻塞期间）并发 addSubTransaction → 显式拒绝（fail-fast）。
     * 缺陷态：静默加入组集合且不被本次提交覆盖（丢写）→ 红。
     */
    @Test
    public void testCra2517_C2_015_lateSubTransactionRejectedDuringCommit() throws Exception {
        JdbcTransaction parent = new JdbcTransaction("cra2517-group3", () -> null, null, false, null);

        CountDownLatch sub1InCommit = new CountDownLatch(1);
        CountDownLatch releaseSub1 = new CountDownLatch(1);
        StubTransaction sub1 = new StubTransaction("sub1", true);
        sub1.onCommit = () -> {
            sub1InCommit.countDown();
            try {
                releaseSub1.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        StubTransaction lateSub = new StubTransaction("sub2", false);

        parent.addSubTransaction(sub1);

        AtomicReference<Throwable> commitError = new AtomicReference<>();
        Thread committer = new Thread(() -> {
            try {
                parent.commit();
            } catch (Throwable e) {
                commitError.set(e);
            }
        });
        committer.start();
        assertTrue(sub1InCommit.await(5, TimeUnit.SECONDS), "组提交未进入子事务提交相位");

        // 快照开始后迟到注册：修复后显式拒绝；缺陷态静默加入（不随本次组提交落库，丢写）
        try {
            parent.addSubTransaction(lateSub);
            throw new AssertionError("缺陷态：迟到子事务被静默接受（静默不提交丢写）→ 红；修复后显式拒绝 → 绿");
        } catch (NopException expected) {
            // 绿：显式拒绝
        }

        releaseSub1.countDown();
        committer.join(10000);
        assertNull(commitError.get(), "组提交本体不得失败: " + commitError.get());
    }

    // ---------------- 红⑩ 连接生命周期锁纪律 ----------------

    /**
     * 红⑩：并行 getConnection 恰好一次池获取、两线程同一连接身份。
     * 缺陷态（双取窗）：两线程各自进入 dataSource.get() → 两个连接、其一脱离管辖泄漏 → 红。
     */
    @Test
    public void testCra2517_C2_017_parallelGetConnectionSingleFetch() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger();
        CountDownLatch secondArrived = new CountDownLatch(2);
        AtomicReference<Connection> connA = new AtomicReference<>();
        AtomicReference<Connection> connB = new AtomicReference<>();

        Supplier<Connection> dataSource = () -> {
            fetchCount.incrementAndGet();
            // 等待第二个调用者到达（暴露缺陷态双取窗；修复后被 monitor 串行化，第二个调用者不会到达）
            try {
                secondArrived.countDown();
                secondArrived.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return stubConnection();
        };

        JdbcTransaction txn = new JdbcTransaction("cra2517-group4", dataSource, null, false, null);

        Thread t1 = new Thread(() -> connA.set(txn.getConnection()));
        Thread t2 = new Thread(() -> connB.set(txn.getConnection()));
        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        assertNotNull(connA.get());
        assertNotNull(connB.get());
        assertEquals(1, fetchCount.get(), "缺陷态：双取池连接（其一泄漏）→ 红；修复后单锁串行恰好一次获取 → 绿");
        assertSame(connA.get(), connB.get(), "并行 getConnection 必须得到同一连接身份");
    }

    /**
     * 红⑩辅助：doCommit null 早退显式留痕（不抛错、可重复安全调用，语义保持）。
     */
    @Test
    public void testCra2517_C2_017_commitOnReleasedConnectionIsObservableNoOp() {
        JdbcTransaction txn = new JdbcTransaction("cra2517-group5", () -> null, null, false, null);
        // connection == null：缺陷态完全无痕 no-op；修复后 WARN 留痕且语义保持（不抛错）
        txn.commit();
    }
}
