package io.nop.code.service.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ERR_TXN_NOT_IN_TRANSACTION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * W-12/W-08 shared facility (plan 2565, adjudications B and G): run a side effect at the
 * transaction commit point via {@link ITransactionTemplate#afterCommit}.
 *
 * <p>Contract (aligned with the {@code NopDatavSchedulerAfterCommit} precedent, plan 2526,
 * with one deliberate difference): registration MUST happen on an already-open transaction —
 * there is no "execute immediately when no transaction" fallback, because a direct call
 * outside a transaction would be a silent no-op shell (R1-B1: the four invalidation sites had
 * no open transaction context). Callers must invoke this inside
 * {@code transactionTemplate.runInTransaction(...)}; a missing transaction context throws
 * {@code ERR_TXN_NOT_IN_TRANSACTION} (guard mirrors {@code addTransactionListener}).</p>
 *
 * <p><b>Callback failure contract</b>: after commit there is no rollback available, so a
 * callback failure is caught and logged as a structured WARN keyed by {@code site}
 * (grep-able), never propagated.</p>
 */
public final class CodeIndexAfterCommit {

    private static final Logger LOG = LoggerFactory.getLogger(CodeIndexAfterCommit.class);

    private CodeIndexAfterCommit() {
    }

    /**
     * Register {@code action} to run after the current transaction commits.
     *
     * @param transactionTemplate transaction template; must have an open transaction
     * @param site                structured log site identifier for callback-failure WARN
     * @param action              commit-point side effect
     */
    public static void run(ITransactionTemplate transactionTemplate, String site, Runnable action) {
        if (transactionTemplate == null || !transactionTemplate.isTransactionOpened(null)) {
            // W-12 guard: registering on no open transaction would silently swallow the
            // side effect (no-op shell) — fail fast instead.
            throw new NopException(ERR_TXN_NOT_IN_TRANSACTION)
                    .param(ARG_QUERY_SPACE, "nop-code")
                    .param("site", site);
        }
        transactionTemplate.afterCommit(null, () -> {
            try {
                action.run();
            } catch (Throwable e) {
                // MA-07 绝缘层约定对齐：commit 点之后无回滚可用，回调失败（含 Error）
                // 一律 catch + 结构化 WARN，绝不向上传播（throwable 逃逸会污染提交线程）
                LOG.warn("{} failed", site, e);
            }
        });
    }

    /**
     * Test seam only: swap the {@code IDaoProvider} used by {@code owner} (manual delegation
     * proxy, zero-mockito convention). Restores are the test's responsibility.
     */
    static void setDaoProviderForTest(Object owner, IDaoProvider provider) throws Exception {
        java.lang.reflect.Field f = owner.getClass().getDeclaredField("daoProvider");
        f.setAccessible(true);
        f.set(owner, provider);
    }
}
