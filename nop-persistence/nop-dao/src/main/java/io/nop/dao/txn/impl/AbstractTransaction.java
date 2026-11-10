/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.ITransactionListener.CompleteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.nop.api.core.context.ContextProvider.completeAsyncOnContext;
import static io.nop.api.core.context.ContextProvider.thenOnContext;
import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ARG_TXN_GROUP;
import static io.nop.dao.DaoErrors.ARG_TXN_ID;
import static io.nop.dao.DaoErrors.ERR_TXN_ALREADY_STARTED;
import static io.nop.dao.DaoErrors.ERR_TXN_COMMIT_FAIL;
import static io.nop.dao.DaoErrors.ERR_TXN_DUPLICATE_SUB_TRANSACTION;
import static io.nop.dao.DaoErrors.ERR_TXN_LATE_SUB_TRANSACTION;
import static io.nop.dao.DaoErrors.ERR_TXN_ROLLBACK_FAIL;
import static io.nop.dao.DaoErrors.ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT;

public abstract class AbstractTransaction implements ITransaction {
    static final Logger LOG = LoggerFactory.getLogger(AbstractTransaction.class);

    private static final AtomicLong s_seq = new AtomicLong();

    private final long seq = s_seq.incrementAndGet();

    private final String txnGroup;

    // 2.3-C1-025（plan 2552 裁定 M）：状态族 volatile——commitAsync/rollbackAsync 回调线程
    // 与注册线程跨线程读写（2517 裁定⑨已修 subTransactions/listeners 面；主状态字段本批收敛）
    private volatile boolean opened;

    /**
     * 2.3-C2-016（plan 2553 裁定 D，Fix+Decision）：事务状态机 CAS——open 双开 fail-fast、
     * commit×rollbackOnly 窗口闭合、二次 commit no-op-with-WARN（原 testMultipleCommit 现状
     * 容忍钉已改写为状态断言，且修复原 afterCommit 重置 rollbackOnly 导致的二次 commit
     * 监听器重复通知潜在缺陷）。
     * Fix+Decision 显式裁定（R1-Major-7）：①markRollbackOnly 后 commit 由抛
     * ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT **改为自动走 rollback 语义 + WARN**（载体断言覆盖）；
     * ②CAS 失败转移：doCommit/listener 异常时终态尽力置 ROLLED_BACK + 异常透传；
     * ③isTransactionOpened() 映射 OPEN/COMMITTING=true（TransactionTemplateImpl 门控与
     * commitSubTransactions 行为保持）。
     */
    private final AtomicReference<TxnState> state = new AtomicReference<>(TxnState.NEW);

    enum TxnState { NEW, OPEN, COMMITTING, COMMITTED, ROLLING_BACK, ROLLED_BACK, CLOSED }

    private void requireStateOpen() {
        TxnState st = state.get();
        if (st != TxnState.OPEN) {
            throw newError(ERR_TXN_ALREADY_STARTED).param("state", st.name());
        }
    }

    // listener数量少、写少读多；commitAsync/rollbackAsync路径的listener回调发生在executor线程，
    // 与注册线程并发时ArrayList扩容/遍历存在竞态，故用CopyOnWriteArrayList。
    // 2.3-C2-014（plan 2553 裁定 C，R1-Minor-8）：volatile——invokeListener/getListeners 锁外读
    // 首注册 stale-null 面消除（init 原子性由 addListener 的 synchronized(this) 域承载）。
    private volatile CopyOnWriteArrayList<ITransactionListener> listeners;
    private volatile boolean rollbackOnly;
    private volatile Throwable error;

    private Map<String, ITransaction> subTransactions;

    // plan 2517 R2-14 裁定⑨（C2-015）：提交/回滚快照开始后 addSubTransaction 显式拒绝（fail-fast，禁静默丢写）
    private boolean subTxnFinalized;

    public AbstractTransaction(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public String toString() {
        return getClass().getSimpleName() + "[seq=" + seq + ",txnGroup=" + txnGroup + ",txnId=" + getTxnId() + "]";
    }

    public boolean isTransactionOpened() {
        // 2.3-C2-016（R1-Major-7 ③）：纯状态映射——OPEN/COMMITTING 视为打开（TransactionTemplateImpl
        // 门控与 commitSubTransactions :388 行为保持）；终态（COMMITTED/ROLLED_BACK/CLOSED）为 false
        //（rollback-only commit 终态语义由 TestTransactionStateCas pin_b 承载）
        TxnState st = state.get();
        return st == TxnState.OPEN || st == TxnState.COMMITTING;
    }

    @Override
    public String getTxnGroup() {
        return txnGroup;
    }

    @Override
    public void addListener(ITransactionListener listener) {
        // 2.3-C2-014（plan 2553 裁定 C）：惰性 init 收进与快照（:112-126 addSubTransaction）同一
        // 监视器——原 check-then-act 两步创建在并发注册下可丢监听器。
        synchronized (this) {
            if (listeners == null)
                listeners = new CopyOnWriteArrayList<>();
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                if (listeners.size() > 1)
                    listeners.sort(ITransactionListener::compareTo);
            }
        }
    }

    @Override
    public void removeListener(ITransactionListener listener) {
        if (listeners != null)
            listeners.remove(listener);
    }

    public Collection<ITransactionListener> getListeners() {
        return listeners;
    }

    public void clearListeners() {
        this.listeners = null;
    }

    @Override
    public void addSubTransaction(ITransaction subTxn) {
        // plan 2517 R2-14 裁定⑨：惰性 init synchronized(this)，与快照迭代点同一监视器（同状态机集合部件同步纪律归一）
        synchronized (this) {
            if (subTxnFinalized)
                // 快照外迟到子事务显式拒绝：提交期静默注册的子事务不会随组提交落库（丢写最重后果），fail-fast
                throw new NopException(ERR_TXN_LATE_SUB_TRANSACTION).param(ARG_TXN_GROUP, subTxn.getTxnGroup());
            if (subTransactions == null)
                subTransactions = new LinkedHashMap<>();
            ITransaction oldTxn = subTransactions.put(subTxn.getTxnGroup(), subTxn);
            if (oldTxn != null && oldTxn != subTxn)
                throw new NopException(ERR_TXN_DUPLICATE_SUB_TRANSACTION).param(ARG_QUERY_SPACE, subTxn.getTxnGroup());
        }
    }

    @Override
    public boolean removeSubTransaction(ITransaction subTxn) {
        synchronized (this) {
            if (subTransactions == null)
                return false;
            return subTransactions.remove(subTxn.getTxnGroup(), subTxn);
        }
    }

    @Override
    public ITransaction getSubTransaction(String querySpace) {
        synchronized (this) {
            if (subTransactions == null)
                return null;
            return subTransactions.get(querySpace);
        }
    }

    @Override
    public Collection<ITransaction> getSubTransactions() {
        // 裁定⑨：返回持锁快照，迭代安全
        return snapshotSubTransactions(false);
    }

    /**
     * plan 2517 R2-14 裁定⑨：全部迭代消费点统一改持锁快照后锁外迭代。
     * finalize=true（commit/rollback 路径）时置 subTxnFinalized 位——其后 addSubTransaction 显式拒绝。
     * 外部回调（子事务 commit/rollback）在快照迭代中锁外执行（锁序总表 §4.6）。
     */
    private List<ITransaction> snapshotSubTransactions(boolean finalize) {
        synchronized (this) {
            if (subTransactions == null)
                return Collections.emptyList();
            if (finalize)
                subTxnFinalized = true;
            return new ArrayList<>(subTransactions.values());
        }
    }

    @Override
    public void markRollbackOnly(Throwable error) {
        this.rollbackOnly = true;
        this.error = error;
        LOG.info("nop.dao.txn.mark-rollback-only:txn={}", this, error);
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly || isSubTxnRollbackOnly();
    }

    boolean isSubTxnRollbackOnly() {
        for (ITransaction txn : snapshotSubTransactions(false)) {
            if (txn.isRollbackOnly())
                return true;
        }
        return false;
    }

    @Override
    public void rollback(Throwable error) {
        LOG.info("nop.dao.txn.rollback:txn={}", this);

        // 2.3-C2-016：状态门控——仅 OPEN 可进入 rollback
        if (!state.compareAndSet(TxnState.OPEN, TxnState.ROLLING_BACK))
            throw newError(ERR_TXN_ALREADY_STARTED).param("state", state.get().name());

        beforeRollback(error);

        Throwable ex = null;
        try {
            doRollback(error);
        } catch (RuntimeException e) {
            ex = e;
        }

        CompleteStatus status = ex != null ? CompleteStatus.UNKNOWN : CompleteStatus.ROLLBACK;
        invokeListener(listener -> listener.onAfterCompletion(this, status, error), true);

        this.error = null;
        this.rollbackOnly = false;
        state.set(TxnState.ROLLED_BACK);

        // ex为执行rollback过程中又发生的异常
        if (ex != null)
            throw NopException.adapt(ex);
    }

    public void beforeRollback(Throwable error) {
        invokeListener(listener -> listener.onBeforeCompletion(this), true);

        Throwable ex;
        try {
            ex = rollbackSubTransactions();
        } catch (RuntimeException e) {
            ex = e;
        }
        if (this.error == null) {
            this.error = ex;
        }
    }

    protected RuntimeException rollbackSubTransactions() {
        RuntimeException e = null;
        // 裁定⑨：持锁快照 + 锁外迭代（快照开始后迟到子事务显式拒绝）
        for (ITransaction subTxn : snapshotSubTransactions(true)) {
            try {
                subTxn.rollback(error);
            } catch (RuntimeException err) {
                LOG.error("nop.err.dao.txn.sub-txn-rollback-fail", err);
                if (e == null)
                    e = err;

                rollbackOnly = true;
            }
        }
        return e;
    }

    protected CompletionStage<Void> rollbackSubTransactionsAsync() {
        List<ITransaction> snapshot = snapshotSubTransactions(true);
        if (snapshot.isEmpty())
            return CompletableFuture.completedFuture(null);

        List<CompletionStage<?>> futures = new ArrayList<>(snapshot.size());
        for (ITransaction subTxn : snapshot) {
            futures.add(subTxn.rollbackAsync(error));
        }
        return FutureHelper.waitAll(futures);
    }

    @Override
    public CompletionStage<Void> rollbackAsync(Throwable ex) {
        LOG.info("nop.dao.txn.rollback-async:txn={}", this);

        if (listeners != null) {
            invokeListener(listener -> listener.onBeforeCompletion(this), true);
        }

        CompletionStage<Void> future;
        if (snapshotSubTransactions(false).isEmpty()) {
            future = thenOnContext(doRollbackAsync(error));
        } else {
            future = completeAsyncOnContext(rollbackSubTransactionsAsync(),
                    (v, err) -> thenOnContext(doRollbackAsync(error).thenRun(() -> {
                        if (err != null)
                            throw NopException.adapt(err);
                    })));
        }

        future = future.exceptionally(err -> {
            invokeListener(
                    listener -> listener.onAfterCompletion(this, ITransactionListener.CompleteStatus.UNKNOWN, err),
                    true);
            error = null;
            rollbackOnly = false;
            throw newError(ERR_TXN_ROLLBACK_FAIL).cause(err);
        });

        return future.thenAccept(v -> {
            if (listeners != null) {
                invokeListener(listener -> listener.onAfterCompletion(this, CompleteStatus.ROLLBACK, null), true);
            }
            error = null;
            rollbackOnly = false;
        });
    }

    protected String getTxnId() {
        return null;
    }

    protected NopException newError(ErrorCode errorCode) {
        return new NopException(errorCode).param(ARG_TXN_ID, getTxnId()).param(ARG_TXN_GROUP, txnGroup);
    }

    @Override
    public void commit() {
        LOG.debug("nop.dao.txn.begin-commit:txn={}", this);

        // 2.3-C2-016（裁定 D）：二次 commit = no-op-with-WARN（原形态重复触发全部监听器——
        // afterCommit 重置 rollbackOnly 的潜在缺陷一并消除）；非 OPEN/NEW/COMMITTED 状态 fail-fast。
        // NEW 允许进入提交相位：2517 红⑩辅助钉（commitOnReleasedConnectionIsObservableNoOp）契约——
        // 未 open 事务的 commit 走既有可观测路径（JdbcTransaction.doCommit null 早退 WARN 留痕），
        // 不由状态机拒绝。
        TxnState st = state.get();
        if (st == TxnState.COMMITTED) {
            LOG.warn("nop.warn.dao.txn-commit-after-committed:txn={},ignored=true", this);
            return;
        }
        if (!state.compareAndSet(TxnState.OPEN, TxnState.COMMITTING)
                && !state.compareAndSet(TxnState.NEW, TxnState.COMMITTING))
            throw newError(ERR_TXN_ALREADY_STARTED).param("state", st.name());

        // 2.3-C2-016（Fix+Decision ①）：markRollbackOnly 后 commit 由抛错改为自动 rollback 语义
        //（用户可见语义变更——载体断言显式覆盖）
        if (isRollbackOnly()) {
            LOG.warn("nop.warn.dao.txn-commit-on-rollback-only:txn={},rolling-back=true", this);
            state.set(TxnState.OPEN); // 回到 OPEN 供 rollback CAS（源自 NEW 的提交同此路径）
            rollback(null);
            return;
        }

        beforeCommit();
        beforeCompletion();
        try {
            doCommit();
        } catch (Exception e) {
            // 提交失败也必须给监听器发送终态通知，否则metrics少计失败且监听器状态泄漏
            state.set(TxnState.ROLLED_BACK); // 尽力终态（R1-Major-7 ②失败转移）
            afterCompletion(CompleteStatus.UNKNOWN);
            throw newError(ERR_TXN_COMMIT_FAIL).cause(e).forWrap();
        }

        state.set(TxnState.COMMITTED);
        afterCommit();
        afterCompletion(CompleteStatus.COMMIT);
        LOG.debug("nop.dao.txn.after-commit:txn={}", this);
    }

    public void beforeCommit() {
        if (isRollbackOnly())
            throw newError(ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT);

        invokeListener(listener -> listener.onBeforeCommit(this), false);

        commitSubTransactions();
    }

    public void afterCommit() {
        invokeListener(listeners -> listeners.onAfterCommit(this), true);
        error = null;
        rollbackOnly = false;
    }

    public void beforeCompletion() {
        invokeListener(listener -> listener.onBeforeCompletion(this), true);
    }

    public void afterCompletion(CompleteStatus status) {
        invokeListener(listener -> listener.onAfterCompletion(this, status, this.error), true);
    }

    @Override
    public CompletionStage<Void> commitAsync() {
        LOG.info("nop.dao.txn.begin-commit-async:txn={}", this);

        if (isRollbackOnly())
            return FutureHelper.reject(newError(ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT));

        try {
            invokeListener(listener -> listener.onBeforeCommit(this), false);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }

        CompletionStage<Void> future;
        if (snapshotSubTransactions(false).isEmpty()) {
            invokeListener(listener -> listener.onBeforeCompletion(this), true);
            future = thenOnContext(doCommitAsync());
        } else {
            future = thenOnContext(commitSubTransactionsAsync()).thenCompose(v -> {
                invokeListener(listener -> listener.onBeforeCompletion(this), true);
                return thenOnContext(doCommitAsync());
            });
        }

        future = future.exceptionally(err -> {
            // 与同步commit对齐：提交失败发送UNKNOWN终态通知
            invokeListener(
                    listener -> listener.onAfterCompletion(this, ITransactionListener.CompleteStatus.UNKNOWN, err),
                    true);
            throw newError(ERR_TXN_COMMIT_FAIL).cause(err).forWrap();
        });

        return future.thenAccept(v -> {
            afterCommit();
            afterCompletion(CompleteStatus.COMMIT);

            LOG.info("nop.dao.txn.after-commit-async:txn={}", this);
        });

    }

    protected CompletionStage<Void> commitSubTransactionsAsync() {
        List<ITransaction> snapshot = snapshotSubTransactions(true);
        if (snapshot.isEmpty())
            return CompletableFuture.completedFuture(null);

        List<CompletionStage<?>> futures = new ArrayList<>(snapshot.size());

        for (ITransaction subTxn : snapshot) {
            if (subTxn.isTransactionOpened()) {
                futures.add(subTxn.commitAsync());
            }
        }
        return FutureHelper.waitAll(futures);
    }

    protected void commitSubTransactions() {
        // 裁定⑨：持锁快照 + 锁外迭代（快照开始后迟到子事务显式拒绝）
        for (ITransaction subTxn : snapshotSubTransactions(true)) {
            if (subTxn.isTransactionOpened())
                subTxn.commit();
        }
    }

    protected void invokeListener(Consumer<ITransactionListener> action, boolean ignoreError) {
        if (listeners != null && !listeners.isEmpty()) {
            // CopyOnWriteArrayList的迭代器本身是快照，遍历中并发增删listener是安全的
            for (ITransactionListener listener : listeners) {
                try {
                    action.accept(listener);
                } catch (Exception e) {
                    LOG.error("nop.err.txn.invoke-listener-fail", e);
                    if (!ignoreError)
                        throw NopException.adapt(e);
                }
            }
        }
    }

    @Override
    public void open() {
        LOG.info("nop.dao.txn.open:txn={}", this);

        if (opened || !state.compareAndSet(TxnState.NEW, TxnState.OPEN))
            throw newError(ERR_TXN_ALREADY_STARTED);
        try {
            invokeOpenListener();
            doOpen();
            openSubTransactions();
            opened = true;
        } catch (Throwable e) {
            state.set(TxnState.NEW); // 回滚启动失败：允许重试 open
            throw e;
        }
    }

    @Override
    public void close() {
        LOG.info("nop.dao.txn.close:txn={}", this);

        state.set(TxnState.CLOSED);
        invokeCloseListener();
        closeSubTransactions();
        doClose();
        this.opened = false;
    }

    protected void invokeOpenListener() {
        if (listeners != null && !listeners.isEmpty()) {
            for (ITransactionListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onOpen(this);
                } catch (Exception e) {
                    LOG.error("nop.err.dao.txn.listener.onOpen.fail:txnId={},txnGroup={}", getTxnId(), txnGroup, e);
                }
            }
        }
    }

    protected void invokeCloseListener() {
        if (listeners != null && !listeners.isEmpty()) {
            for (ITransactionListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onClose(this);
                } catch (Exception e) {
                    LOG.error("nop.err.dao.txn.listener.onClose.fail:txnId={},txnGroup={}", getTxnId(), txnGroup, e);
                }
            }

            clearListeners();
        }
    }

    protected void closeSubTransactions() {
        for (ITransaction subTxn : snapshotSubTransactions(false)) {
            IoHelper.safeClose(subTxn);
        }
    }

    protected void openSubTransactions() {
        for (ITransaction subTxn : snapshotSubTransactions(false)) {
            subTxn.open();
        }
    }


    protected abstract void doOpen();

    protected abstract void doCommit();

    protected abstract void doRollback(Throwable error);

    protected abstract void doClose();

    protected CompletionStage<Void> doCommitAsync() {
        return FutureHelper.futureRun(this::doCommit);
    }

    protected CompletionStage<Void> doRollbackAsync(Throwable error) {
        return FutureHelper.futureRun(() -> doRollback(error));
    }
}