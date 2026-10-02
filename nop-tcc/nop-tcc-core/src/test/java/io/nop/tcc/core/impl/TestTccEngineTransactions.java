package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_MISSING_TRANSACTION_RECORD;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_ALREADY_FINISHED;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 TccEngine 的事务编排语义：同步 runInTransaction 的提交/回滚、
 * 事务注册表复用与恢复、按 txnId 加载时对缺失/已完结记录的拒绝、
 * 已结束事务不允许开分支、清理接口只删除已完结事务。
 */
@Timeout(10)
public class TestTccEngineTransactions {

    static TccEngine newEngine(FakeTccRecordStore store) {
        TccEngine engine = new TccEngine();
        engine.setTccRecordStore(store);
        engine.setServiceInvoker((svc, method, req, token) ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        io.nop.api.core.beans.ApiResponse.success("ok")));
        return engine;
    }

    @Test
    public void testRunInTransactionCommitsOnSuccess() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);

        String ret = engine.runInTransaction("group-commit", txn -> {
            assertEquals(TccStatus.TRYING, txn.getTccStatus(), "task runs inside trying txn");
            return txn.getTxnId();
        });

        ITccRecord record = store.records.get(ret);
        assertEquals(TccStatus.CONFIRM_SUCCESS, record.getTccStatus(),
                "successful task must end in confirm");
        assertEquals(1, store.saveTccCount.get(), "begin must persist initial record");
    }

    @Test
    public void testRunInTransactionRollsBackOnException() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);

        String[] txnId = new String[1];
        assertThrows(Exception.class, () -> engine.runInTransaction("group-rollback", txn -> {
            txnId[0] = txn.getTxnId();
            throw new IllegalStateException("biz failure");
        }));

        assertEquals(TccStatus.CANCEL_SUCCESS, store.records.get(txnId[0]).getTccStatus(),
                "task failure must cancel the transaction");
    }

    @Test
    public void testRunInTransactionReusesRegisteredTxn() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);

        ITccTransaction existing = engine.newTransaction("group-reuse");
        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        ITccTransaction old = registry.put("group-reuse", existing);

        try {
            ITccTransaction seen = engine.runInTransaction("group-reuse", txn -> txn);
            assertSame(existing, seen, "registered txn must be reused instead of creating a new one");
            assertEquals(0, store.saveTccCount.get(), "reused txn must not begin again");
        } finally {
            registry.put("group-reuse", old);
        }
    }

    @Test
    public void testLoadMissingRecordRejected() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);

        NopException e = assertThrows(NopException.class,
                () -> engine.runInTransaction("group-x", "no-such-txn", txn -> "ok"));
        assertEquals(ERR_TCC_MISSING_TRANSACTION_RECORD.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testLoadFinishedRecordRejected() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        ITccRecord record = store.newTccRecord("group-x");
        ((FakeTccRecordStore.FakeTccRecord) record).status = TccStatus.KILLED;

        NopException e = assertThrows(NopException.class,
                () -> engine.runInTransaction("group-x", record.getTxnId(), txn -> "ok"));
        assertEquals(ERR_TCC_TRANSACTION_ALREADY_FINISHED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testBranchRejectedWhenTxnNotTrying() throws Exception {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        ITccTransaction txn = engine.newTransaction("group-branch");
        ((FakeTccRecordStore.FakeTccRecord) txn.getTccRecord()).status = TccStatus.CANCEL_SUCCESS;

        try {
            engine.runBranchTransactionAsync(txn, new TccBranchRequest(),
                            t -> java.util.concurrent.CompletableFuture.completedStage("ok"))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            throw new AssertionError("branch must be rejected when txn is not TRYING");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof NopException);
            assertEquals(ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH.getErrorCode(),
                    ((NopException) e.getCause()).getErrorCode());
        }
    }

    @Test
    public void testNewBranchTransactionWiredFromRequest() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        ITccTransaction txn = engine.newTransaction("group-branch2");

        TccBranchRequest req = new TccBranchRequest();
        req.setServiceName("svc");
        req.setServiceMethod("tryMethod");
        req.setConfirmMethod("confirmMethod");
        req.setCancelMethod("cancelMethod");
        req.setRequest(new ApiRequest<>());

        TccBranchTransaction branch = (TccBranchTransaction) engine.newBranchTransaction(txn, req);

        assertEquals("svc", branch.getBranchRecord().getServiceName());
        assertEquals("confirmMethod", branch.getBranchRecord().getConfirmMethod());
        assertEquals(TccStatus.CREATED, branch.getBranchRecord().getBranchStatus());
        assertTrue(store.branches.containsKey(txn.getTxnId()), "branch must be tracked under its txn");
    }

    @Test
    public void testCleanCompletedOnlyRemovesCompleted() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);

        engine.cleanCompletedTransactions(1000);

        assertEquals(Boolean.TRUE, store.lastRemoveCompletedOnlyCompleted,
                "cleanup must only delete completed transactions");
    }

    @Test
    public void testCheckExpiredWithNoRecordsReturns() {
        FakeTccRecordStore store = new FakeTccRecordStore();
        TccEngine engine = newEngine(store);
        store.expiredRecords = List.of();

        engine.checkExpiredTransactions(1000, 3, null);
        assertEquals(0, store.updateTccCount.get(), "no expired records must not touch store");
        assertFalse(engine.isSafeFailException(new IllegalStateException("x")),
                "no exceptionChecker configured must yield false");
    }
}
