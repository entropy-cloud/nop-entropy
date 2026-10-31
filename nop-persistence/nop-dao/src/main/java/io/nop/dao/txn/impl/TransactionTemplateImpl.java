/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionManager;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.api.core.context.ContextProvider.completeAsyncOnContext;
import static io.nop.dao.DaoErrors.ARG_TXN;
import static io.nop.dao.DaoErrors.ARG_TXN_GROUP;
import static io.nop.dao.DaoErrors.ERR_TXN_NOT_ALLOW_TRANSACTION;
import static io.nop.dao.DaoErrors.ERR_TXN_NOT_IN_TRANSACTION;
import static io.nop.dao.DaoErrors.ERR_TXN_NOT_REGISTERED;
import static io.nop.dao.utils.DaoHelper.isDefaultQuerySpace;

public class TransactionTemplateImpl implements ITransactionTemplate {
    static final Logger LOG = LoggerFactory.getLogger(TransactionTemplateImpl.class);

    private ITransactionManager transactionManager;

    public ITransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Inject
    public void setTransactionManager(ITransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        return transactionManager.getDialectForQuerySpace(querySpace);
    }

    @Override
    public Connection openConnection(String querySpace) {
        return transactionManager.openConnection(querySpace);
    }

    @Override
    public boolean isQuerySpaceDefined(String querySpace) {
        return transactionManager.isQuerySpaceDefined(querySpace);
    }

    @Override
    public String getMainTxnGroup(String txnGroup) {
        if (isDefaultQuerySpace(txnGroup))
            return null;
        return transactionManager.getMainTxnGroup(txnGroup);
    }

    @Override
    public ITransaction getRegisteredTransaction(String txnGroup) {
        ITransaction txn = transactionManager.getRegisteredTransaction(txnGroup);
        if (txn == null) {
            ITransaction groupTxn = getMainGroupTransaction(txnGroup);
            if (groupTxn != null)
                return groupTxn.getSubTransaction(txnGroup);

        }
        return txn;
    }

    private ITransaction getMainGroupTransaction(String txnGroup) {
        String mainGroup = getMainTxnGroup(txnGroup);
        if (mainGroup != null) {
            ITransaction groupTxn = transactionManager.getRegisteredTransaction(mainGroup);
            return groupTxn;
        }
        return null;
    }

    static class TxnState {
        String txnGroup;
        ITransaction txn;

        boolean newlyOpen;
        boolean newlyCreated;
        boolean groupNewlyCreated;

        String mainGroup;
        ITransaction groupTxn;

        ITransaction prevTxn;

        // plan 2517 R2-14 裁定⑧：prevTxn 经挂起标记摘除（NOT_SUPPORTED）时为 true，cleanup 走 resume 而非 re-register
        boolean prevSuspended;

        TxnState newState() {
            TxnState state = new TxnState();
            state.txnGroup = txnGroup;
            state.mainGroup = mainGroup;
            state.prevTxn = getRealTxn();
            return state;
        }

        ITransaction getRealTxn() {
            if (mainGroup != null)
                return groupTxn;
            return txn;
        }

        boolean isOpened() {
            if (mainGroup != null) {
                return groupTxn != null && groupTxn.isTransactionOpened();
            } else {
                return txn != null && txn.isTransactionOpened();
            }
        }
    }

    private TxnState getTxnState(String txnGroup, boolean allowCreate) {
        TxnState state = new TxnState();
        state.txnGroup = txnGroup;
        String mainGroup = getMainTxnGroup(txnGroup);
        if (mainGroup != null && !mainGroup.equals(txnGroup)) {
            state.mainGroup = mainGroup;
            ITransaction groupTxn = transactionManager.getRegisteredTransaction(mainGroup);
            ITransaction txn;
            if (groupTxn != null) {
                txn = groupTxn.getSubTransaction(txnGroup);
                if (txn == null && allowCreate) {
                    // 如果主事务已经打开，则需要自动打开子事务
                    txn = transactionManager.newTransaction(txnGroup);
                    groupTxn.addSubTransaction(txn);
                    if (groupTxn.isTransactionOpened()) {
                        txn.open();
                    }
                    // 这里事务的状态由主事务决定，所以不用标记newlyOpen
                    state.newlyCreated = true;
                }
            } else {
                txn = transactionManager.getRegisteredTransaction(txnGroup);
                if (txn == null && allowCreate) {
                    // 只是新建事务对象，但是并没有调用open打开事务
                    txn = transactionManager.newTransaction(txnGroup);
                    state.newlyCreated = true;
                    state.prevTxn = transactionManager.registerTransaction(txn);
                }
            }
            state.txn = txn;
            state.groupTxn = groupTxn;
        } else {
            if (allowCreate) {
                // plan 2517 R2-14 裁定⑧（C2-013）：get+new+register 三步收敛为注册表级原子 create-if-absent。
                // 并发下败者采用胜者（自建事务不落注册表，消除 put 覆盖 prevTxn 的注册表覆盖面）——
                // REQUIRED 组语义不再静默退化为独立事务。锁只保护注册表结构变更（不覆盖 task 执行与 commit）
                ITransactionManager.RegisteredTxn registered = transactionManager
                        .getOrCreateRegisteredTransaction(txnGroup);
                ITransaction txn = registered.getTxn();
                if (registered.isNewlyRegistered())
                    state.newlyCreated = true;
                state.txn = txn;
            } else {
                // allowCreate=false（NEVER/SUPPORTS 探测等）：只读不建，保持既有 get+同步器回退语义
                state.txn = transactionManager.getRegisteredTransaction(txnGroup);
            }
        }
        return state;
    }

    @Override
    public <T> T runWithoutTransaction(String txnGroup, Supplier<T> task) {
        ITransaction txn = getRegisteredTransaction(txnGroup);
        if (txn == null) {
            return task.get();
        } else {
            // plan 2517 R2-14 裁定⑧：注销-执行-重挂窗口改挂起标记（registry entry 挂 suspended 位，
            // 查找侧跳过）——并行分支在窗口内 get-or-create 不再误建新事务，REQUIRED 组语义保持
            transactionManager.suspendTransaction(txn);
            try {
                return task.get();
            } finally {
                transactionManager.resumeTransaction(txn);
            }
        }
    }

    @Override
    public <T> CompletionStage<T> runInTransactionAsync(String txnGroup, TransactionPropagation propagation,
                                                        Function<ITransaction, CompletionStage<T>> task) {
        TxnState state = openTransaction(txnGroup, propagation);

        CompletionStage<T> future;
        try {
            future = task.apply(state.txn);
        } catch (Throwable e) {
            future = FutureHelper.reject(e);
        }
        return completeAsyncOnContext(future, (ret, err) -> {
            if (NopException.shouldRollback(err)) {
                // 出错时总是执行rollback，确保数据库资源得到释放
                CompletionStage<Void> rollbackStage;
                try {
                    rollbackStage = rollbackTransactionAsync(state, err);
                } catch (Exception rbError) {
                    // rollback失败可能同步抛出（ResolvedPromise内联执行路径）：同样只作suppressed
                    // 附加到原始异常，不覆盖原始异常
                    err.addSuppressed(rbError);
                    return FutureHelper.reject(NopException.adapt(err));
                }
                return rollbackStage.handle((v, b) -> {
                    // rollback自身的失败不能被吞掉：作为suppressed附加到原始异常上，
                    // 与同步版runInTransaction的行为保持一致。经handle替换结果——成功/失败
                    // 两条路径都必须重抛原始业务异常（ResolvedPromise.whenComplete 对已失败
                    // stage的action异常只记日志不替换，exceptionally 只覆盖失败路径）
                    if (b != null)
                        err.addSuppressed(b);
                    throw NopException.adapt(err);
                }).thenApply(x -> null);
            } else {
                return commitTransactionAsync(state).exceptionally(err2 -> {
                    rollbackTransaction(state, err2);
                    throw NopException.adapt(err2);
                }).thenApply(v -> FutureHelper.returnResult(ret, err));
            }
        }).whenComplete((ret, err) -> {
            cleanupTransaction(state);
        });
    }

    @Override
    public <T> T runInTransaction(String txnGroup, TransactionPropagation propagation, Function<ITransaction, T> task) {
        TxnState state = openTransaction(txnGroup, propagation);

        boolean executed = false;
        try {
            T result = task.apply(state.txn);
            executed = true;
            commitTransaction(state);
            return result;
        } catch (Exception e) {
            if (executed || NopException.shouldRollback(e)) {
                try {
                    rollbackTransaction(state, e);
                } catch (Exception rbError) {
                    // rollback自身的失败只作为suppressed附加，不覆盖原始异常
                    e.addSuppressed(rbError);
                }
            } else {
                try {
                    commitTransaction(state);
                } catch (Exception e2) {
                    // commit失败需要补rollback。commit/rollback的失败都作为suppressed附加，
                    // 不吞掉也不覆盖原始异常
                    e.addSuppressed(e2);
                    try {
                        rollbackTransaction(state, e2);
                    } catch (Exception rbError) {
                        e.addSuppressed(rbError);
                    }
                }
            }
            throw NopException.adapt(e);
        } finally {
            cleanupTransaction(state);
        }
    }

    private TxnState openTransaction(String txnGroup, TransactionPropagation propagation) {
        if (propagation == null)
            propagation = TransactionPropagation.REQUIRED;
        TxnState state;
        switch (propagation) {
            case REQUIRED: {
                state = getTxnState(txnGroup, true);
                openExistingTransaction(state);
                break;
            }
            case SUPPORTS: {
                state = getTxnState(txnGroup, true);
                break;
            }
            case MANDATORY: {
                state = getTxnState(txnGroup, true);
                if (!state.isOpened())
                    throw new NopException(ERR_TXN_NOT_IN_TRANSACTION).param(ARG_TXN_GROUP, txnGroup);
                break;
            }
            case REQUIRES_NEW: {
                state = getTxnState(txnGroup, false);
                state = state.newState();
                createNewTransaction(state);
                break;
            }
            case NOT_SUPPORTED: {
                state = getTxnState(txnGroup, false);
                state = state.newState();
                if (state.prevTxn != null) {
                    // plan 2517 R2-14 裁定⑧：注销窗口改挂起标记，cleanup 阶段 resume 恢复
                    transactionManager.suspendTransaction(state.prevTxn);
                    state.prevSuspended = true;
                }
                break;
            }
            case NEVER: {
                state = getTxnState(txnGroup, false);
                if (state.isOpened())
                    throw new NopException(ERR_TXN_NOT_ALLOW_TRANSACTION).param(ARG_TXN_GROUP, txnGroup);
                break;
            }
            default:
                throw new UnsupportedOperationException();
        }
        return state;
    }

    private void openExistingTransaction(TxnState state) {
        if (state.groupTxn != null) {
            // 如果存在主事务，则事务的打开关闭完全交由主事务负责
            if (!state.groupTxn.isTransactionOpened()) {
                state.groupTxn.open();
                state.newlyOpen = true;
            }
        } else if (!state.txn.isTransactionOpened()) {
            state.txn.open();
            state.newlyOpen = true;
        }
    }

    private void createNewTransaction(TxnState state) {
        if (state.mainGroup != null) {
            ITransaction groupTxn = transactionManager.newTransaction(state.mainGroup);
            ITransaction txn = null;
            try {
                txn = transactionManager.newTransaction(state.txnGroup);
                groupTxn.addSubTransaction(txn);
                groupTxn.open();
            } catch (Exception e) {
                groupTxn.close();
                IoHelper.safeCloseObject(txn);
                throw NopException.adapt(e);
            }
            transactionManager.registerTransaction(groupTxn);
            state.groupTxn = groupTxn;
            state.txn = txn;
            state.groupNewlyCreated = true;
            state.newlyCreated = true;
            state.newlyOpen = true;
        } else {
            ITransaction txn = transactionManager.newTransaction(state.txnGroup);
            try {
                txn.open();
            } catch (Exception e) {
                IoHelper.safeCloseObject(txn);
                throw NopException.adapt(e);
            }
            transactionManager.registerTransaction(txn);
            state.txn = txn;
            state.newlyCreated = true;
            state.newlyOpen = true;
        }
    }

    private CompletionStage<Void> commitTransactionAsync(TxnState state) {
        if (state.newlyOpen) {
            if (state.groupTxn != null) {
                return state.groupTxn.commitAsync();
            } else if (state.txn != null) {
                return state.txn.commitAsync();
            }
        }
        return FutureHelper.success(null);
    }

    private CompletionStage<Void> rollbackTransactionAsync(TxnState state, Throwable e) {
        if (state.groupTxn != null) {
            // 与同步版rollbackTransaction保持一致：仅当本层新建了事务组才回滚整个组。
            // 仅新建子事务时异常向外传播，由主事务的owner负责回滚
            if (state.groupNewlyCreated && state.groupTxn.isTransactionOpened())
                return state.groupTxn.rollbackAsync(e);
        } else if (state.txn != null) {
            if (state.newlyCreated && state.txn.isTransactionOpened())
                return state.txn.rollbackAsync(e);
        }
        return FutureHelper.success(null);
    }

    private void commitTransaction(TxnState state) {
        // 负责打开事务的函数负责提交
        if (state.newlyOpen) {
            if (state.groupTxn != null) {
                state.groupTxn.commit();
            } else if (state.txn != null) {
                state.txn.commit();
            }
        }
    }

    private void rollbackTransaction(TxnState state, Throwable e) {
        // 打开事务的函数负责回滚。
        if (state.groupTxn != null) {
            // 事务未打开的情况下不允许回滚
            if (state.groupNewlyCreated && state.groupTxn.isTransactionOpened())
                state.groupTxn.rollback(e);
        } else if (state.txn != null) {
            if (state.newlyCreated && state.txn.isTransactionOpened())
                state.txn.rollback(e);
        }
    }

    private void cleanupTransaction(TxnState state) {
        RuntimeException ex = null;
        // 新建的事务需要取消注册
        if (state.groupNewlyCreated) {
            if (!transactionManager.unregisterTransaction(state.groupTxn)) {
                ex = cleanupIdentityMismatch(state.groupTxn);
            }
        } else if (state.newlyCreated && state.groupTxn == null) {
            // 如果存在主事务，则实际txn是注册到groupTxn中，而不是注册为全局的事务，因此这里也就不需要取消注册
            if (!transactionManager.unregisterTransaction(state.txn)) {
                ex = cleanupIdentityMismatch(state.txn);
            }
        }

        // REQUIRE_NEW需要恢复此前的事务
        if (state.prevTxn != null) {
            // plan 2517 R2-14 裁定⑧：NOT_SUPPORTED 挂起标记走 resume，REQUIRES_NEW 覆盖面走 re-register
            if (state.prevSuspended)
                transactionManager.resumeTransaction(state.prevTxn);
            else
                transactionManager.registerTransaction(state.prevTxn);
        }

        // 新打开的主事务需要关闭。如果是分支事务，则跟随主事务一起关闭
        // newlyCreated的事务即使没有open，回调函数中也有可能调用getConnection导致实际上创建了非事务性的连接
        if (state.groupTxn != null) {
            if (state.newlyOpen || state.groupNewlyCreated) {
                state.groupTxn.close();
            }
        } else if (state.txn != null) {
            if (state.newlyOpen || state.newlyCreated)
                state.txn.close();
        }

        if (ex != null)
            throw ex;
    }

    /**
     * plan 2517 R2-14 裁定⑧(c)：unregister 失败先身份比对（registry 现值==本事务？）。
     * 一致（理论不可达，remove(key,value) 失败即现值不同或已空）→ 真异常保留；
     * 不一致 → 并发方已接管，降级 WARN 非抛错——提交成功后伪 ERR_TXN_NOT_REGISTERED
     * （触发上游重试与数据重复）消除。可观测指标：nop.dao.txn.cleanup-identity-mismatch
     */
    private RuntimeException cleanupIdentityMismatch(ITransaction txn) {
        ITransaction current = transactionManager.getRegisteredTransaction(txn.getTxnGroup());
        if (current == txn) {
            return new NopException(ERR_TXN_NOT_REGISTERED).param(ARG_TXN, txn);
        }
        LOG.warn("nop.dao.txn.cleanup-identity-mismatch:txn={},current={}", txn, current);
        return null;
    }
}