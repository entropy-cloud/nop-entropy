/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 DAO-02：事务 commit 失败路径必须向监听器发送 onAfterCompletion(UNKNOWN) 终态通知。
 * 修复前 commit()/commitAsync() 失败时直接包装异常抛出，监听器只收到 onBeforeCompletion 而
 * 永远等不到终态回调（metrics 少计失败、监听器状态泄漏）。rollbackAsync 失败路径已有 UNKNOWN 通知。
 */
public class TestAbstractTransactionCommitNotification {

    static class RecordingListener implements ITransactionListener {
        final List<String> events = new ArrayList<>();
        final List<CompleteStatus> statuses = new ArrayList<>();

        @Override
        public void onBeforeCommit(ITransaction txn) {
            events.add("beforeCommit");
        }

        @Override
        public void onBeforeCompletion(ITransaction txn) {
            events.add("beforeCompletion");
        }

        @Override
        public void onAfterCommit(ITransaction txn) {
            events.add("afterCommit");
        }

        @Override
        public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
            events.add("afterCompletion:" + status);
            statuses.add(status);
        }
    }

    static class FailingCommitTxn extends AbstractTransaction {
        FailingCommitTxn(String txnGroup) {
            super(txnGroup);
        }

        @Override
        protected void doOpen() {
        }

        @Override
        protected void doCommit() {
            throw new IllegalStateException("commit-io-error");
        }

        @Override
        protected void doRollback(Throwable error) {
        }

        @Override
        protected void doClose() {
        }
    }

    @Test
    public void testCommitFailureNotifiesAfterCompletionUnknown() {
        FailingCommitTxn txn = new FailingCommitTxn("g");
        RecordingListener listener = new RecordingListener();
        txn.addListener(listener);
        txn.open();

        try {
            txn.commit();
            throw new AssertionError("commit应当失败");
        } catch (Exception e) {
            // 预期：提交失败包装为ERR_TXN_COMMIT_FAIL抛出
        }

        assertTrue(listener.events.contains("afterCompletion:" + ITransactionListener.CompleteStatus.UNKNOWN),
                "commit失败必须发送onAfterCompletion(UNKNOWN)终态通知，实际事件: " + listener.events);
        assertFalse(listener.events.contains("afterCommit"), "提交失败不应发送onAfterCommit");
    }

    @Test
    public void testCommitAsyncFailureNotifiesAfterCompletionUnknown() {
        FailingCommitTxn txn = new FailingCommitTxn("g");
        RecordingListener listener = new RecordingListener();
        txn.addListener(listener);
        txn.open();

        try {
            io.nop.api.core.util.FutureHelper.syncGet(txn.commitAsync());
            throw new AssertionError("commitAsync应当失败");
        } catch (Exception e) {
            // 预期失败
        }

        assertTrue(listener.events.contains("afterCompletion:" + ITransactionListener.CompleteStatus.UNKNOWN),
                "commitAsync失败必须发送onAfterCompletion(UNKNOWN)终态通知，实际事件: " + listener.events);
    }
}
