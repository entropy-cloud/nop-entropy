/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static io.nop.dao.DaoErrors.ERR_TXN_COMMIT_FAIL;
import static io.nop.dao.DaoErrors.ERR_TXN_ROLLBACK_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 异步commit/rollback失败时，返回的future必须携带包装后的错误码（ERR_TXN_COMMIT_FAIL/ERR_TXN_ROLLBACK_FAIL），
 * 而不是把exceptionally的包装结果丢弃后透出原始异常
 */
public class TestAbstractTransactionAsync {

    static class FailingTxn extends AbstractTransaction {
        final RuntimeException failCommit = new RuntimeException("commit-io-error");
        final RuntimeException failRollback = new RuntimeException("rollback-io-error");

        FailingTxn(String txnGroup) {
            super(txnGroup);
        }

        @Override
        protected void doOpen() {
        }

        @Override
        protected void doCommit() {
            throw failCommit;
        }

        @Override
        protected void doRollback(Throwable error) {
            throw failRollback;
        }

        @Override
        protected void doClose() {
        }
    }

    @Test
    public void testRollbackAsyncWrapsErrorWithErrorCode() {
        FailingTxn txn = new FailingTxn("a");
        txn.open();

        CompletionStage<Void> future = txn.rollbackAsync(new RuntimeException("biz-error"));

        try {
            FutureHelper.syncGet(future);
            throw new AssertionError("rollbackAsync should fail");
        } catch (NopException cause) {
            assertEquals(ERR_TXN_ROLLBACK_FAIL.getErrorCode(), cause.getErrorCode());
            assertEquals(txn.failRollback, cause.getCause(), "wrapped cause must be the raw rollback failure");
        }
    }

    @Test
    public void testCommitAsyncWrapsErrorWithErrorCode() {
        FailingTxn txn = new FailingTxn("a");
        txn.open();

        CompletionStage<Void> future = txn.commitAsync();

        try {
            FutureHelper.syncGet(future);
            throw new AssertionError("commitAsync should fail");
        } catch (NopException cause) {
            assertEquals(ERR_TXN_COMMIT_FAIL.getErrorCode(), cause.getErrorCode());
            assertEquals(txn.failCommit, cause.getCause(), "wrapped cause must be the raw commit failure");
        }
    }
}
