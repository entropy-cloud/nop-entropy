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
import static io.nop.dao.DaoErrors.ERR_TXN_ROLLBACK_FAIL;
import static io.nop.dao.DaoErrors.ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT;

public abstract class AbstractTransaction implements ITransaction {
    static final Logger LOG = LoggerFactory.getLogger(AbstractTransaction.class);

    private static final AtomicLong s_seq = new AtomicLong();

    private final long seq = s_seq.incrementAndGet();

    private final String txnGroup;

    private boolean opened;

    private List<ITransactionListener> listeners;
    private boolean rollbackOnly;
    private Throwable error;

    private Map<String, ITransaction> subTransactions;

    public AbstractTransaction(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public String toString() {
        return getClass().getSimpleName() + "[seq=" + seq + ",txnGroup=" + txnGroup + ",txnId=" + getTxnId() + "]";
    }

    public boolean isTransactionOpened() {
        return opened;
    }

    @Override
    public String getTxnGroup() {
        return txnGroup;
    }

    @Override
    public void addListener(ITransactionListener listener) {
        if (listeners == null)
            listeners = new ArrayList<>();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            if (listeners.size() > 1)
                listeners.sort(ITransactionListener::compareTo);
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
        if (subTransactions == null)
            subTransactions = new LinkedHashMap<>();
        ITransaction oldTxn = subTransactions.put(subTxn.getTxnGroup(), subTxn);
        if (oldTxn != null && oldTxn != subTxn)
            throw new NopException(ERR_TXN_DUPLICATE_SUB_TRANSACTION).param(ARG_QUERY_SPACE, subTxn.getTxnGroup());
    }

    @Override
    public boolean removeSubTransaction(ITransaction subTxn) {
        if (subTransactions == null)
            return false;
        return subTransactions.remove(subTxn.getTxnGroup(), subTxn);
    }

    @Override
    public ITransaction getSubTransaction(String querySpace) {
        if (subTransactions == null)
            return null;
        return subTransactions.get(querySpace);
    }

    @Override
    public Collection<ITransaction> getSubTransactions() {
        if (subTransactions == null)
            return Collections.emptySet();
        return subTransactions.values();
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
        if (subTransactions == null)
            return false;
        for (ITransaction txn : subTransactions.values()) {
            if (txn.isRollbackOnly())
                return true;
        }
        return false;
    }

    @Override
    public void rollback(Throwable error) {
        LOG.info("nop.dao.txn.rollback:txn={}", this);

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
        if (subTransactions != null) {
            for (ITransaction subTxn : subTransactions.values()) {
                try {
                    subTxn.rollback(error);
                } catch (RuntimeException err) {
                    LOG.error("nop.err.dao.txn.sub-txn-rollback-fail", err);
                    if (e == null)
                        e = err;

                    rollbackOnly = true;
                }
            }
        }
        return e;
    }

    protected CompletionStage<Void> rollbackSubTransactionsAsync() {
        if (subTransactions == null)
            return CompletableFuture.completedFuture(null);

        List<CompletionStage<?>> futures = new ArrayList<>(subTransactions.size());
        for (ITransaction subTxn : subTransactions.values()) {
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
        if (subTransactions == null) {
            future = thenOnContext(doRollbackAsync(error));
        } else {
            future = completeAsyncOnContext(rollbackSubTransactionsAsync(),
                    (v, err) -> thenOnContext(doRollbackAsync(error).thenRun(() -> {
                        if (err != null)
                            throw NopException.adapt(err);
                    })));
        }

        future.exceptionally(err -> {
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

        beforeCommit();
        beforeCompletion();
        try {
            doCommit();
        } catch (Exception e) {
            throw newError(ERR_TXN_COMMIT_FAIL).cause(e).forWrap();
        }

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
        if (subTransactions == null) {
            invokeListener(listener -> listener.onBeforeCompletion(this), true);
            future = thenOnContext(doCommitAsync());
        } else {
            future = thenOnContext(commitSubTransactionsAsync()).thenCompose(v -> {
                invokeListener(listener -> listener.onBeforeCompletion(this), true);
                return thenOnContext(doCommitAsync());
            });
        }

        future.exceptionally(err -> {
            throw newError(ERR_TXN_COMMIT_FAIL).cause(err).forWrap();
        });

        return future.thenAccept(v -> {
            afterCommit();
            afterCompletion(CompleteStatus.COMMIT);

            LOG.info("nop.dao.txn.after-commit-async:txn={}", this);
        });

    }

    protected CompletionStage<Void> commitSubTransactionsAsync() {
        if (subTransactions == null)
            return CompletableFuture.completedFuture(null);

        List<CompletionStage<?>> futures = new ArrayList<>(subTransactions.size());

        for (ITransaction subTxn : subTransactions.values()) {
            if (subTxn.isTransactionOpened()) {
                futures.add(subTxn.commitAsync());
            }
        }
        return FutureHelper.waitAll(futures);
    }

    protected void commitSubTransactions() {
        if (subTransactions != null) {
            for (ITransaction subTxn : subTransactions.values()) {
                if (subTxn.isTransactionOpened())
                    subTxn.commit();
            }
        }
    }

    protected void invokeListener(Consumer<ITransactionListener> action, boolean ignoreError) {
        if (listeners != null && !listeners.isEmpty()) {
            for (ITransactionListener listener : new ArrayList<>(listeners)) {
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

        if (opened)
            throw newError(ERR_TXN_ALREADY_STARTED);
        invokeOpenListener();
        doOpen();
        openSubTransactions();
        opened = true;
    }

    @Override
    public void close() {
        LOG.info("nop.dao.txn.close:txn={}", this);

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
        if (subTransactions != null) {
            for (ITransaction subTxn : subTransactions.values()) {
                IoHelper.safeClose(subTxn);
            }
        }
    }

    protected void openSubTransactions() {
        if (subTransactions != null) {
            for (ITransaction subTxn : subTransactions.values()) {
                subTxn.open();
            }
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