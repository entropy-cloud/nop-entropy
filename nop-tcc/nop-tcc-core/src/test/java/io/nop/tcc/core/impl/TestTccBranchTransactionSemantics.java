package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_INVALID_CANCEL_BRANCH_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证分支事务 try/confirm/cancel 的状态迁移语义：
 * finishTry 按业务结果/safe-fail 标记 TRY_SUCCESS/TRY_FAILED/TRY_UNKNOWN（无 confirmMethod 时自动确认）；
 * begin/finishConfirm 只允许 confirm 阶段重入；begin/finishCancel 区分普通取消与超时取消。
 */
@Timeout(10)
public class TestTccBranchTransactionSemantics {

    static ITccBranchRecord branchRecord(TccStatus initial, String confirmMethod) {
        FakeTccRecordStore store = new FakeTccRecordStore();
        ITccRecord txnRecord = store.newTccRecord("default");
        FakeTccRecordStore.FakeBranchRecord branch = new FakeTccRecordStore.FakeBranchRecord(
                txnRecord.getTxnGroup(), txnRecord.getTxnId(), "b1", "svc",
                "tryMethod", confirmMethod, "cancelMethod", new ApiRequest<>());
        branch.status = initial;
        return branch;
    }

    static TccBranchTransaction newBranchTxn(TccEngine engine, ITccBranchRecord record) {
        return new TccBranchTransaction(record, engine);
    }

    static TccEngine newEngine() {
        TccEngine engine = new TccEngine();
        engine.setTccRecordStore(new FakeTccRecordStore());
        engine.setServiceInvoker((svc, method, req, token) ->
                java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.success("ok")));
        return engine;
    }

    private void finishAndAssert(TccStatus initial, ApiResponse<?> response, Throwable ex,
                                 TccStatus expected, String confirmMethod) throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(initial, confirmMethod);
        newBranchTxn(engine, record).finishTryAsync(response, ex)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, expected);
    }

    @Test
    public void testFinishTrySuccessMarksTrySuccess() throws Exception {
        finishAndAssert(TccStatus.TRYING, ApiResponse.success("ok"), null,
                TccStatus.TRY_SUCCESS, "svc__confirmMethod");
    }

    @Test
    public void testFinishTrySuccessWithoutConfirmMethodAutoConfirms() throws Exception {
        finishAndAssert(TccStatus.TRYING, ApiResponse.success("ok"), null,
                TccStatus.CONFIRM_SUCCESS, null);
    }

    @Test
    public void testFinishTryBizFailResponseMarksTryFailed() throws Exception {
        finishAndAssert(TccStatus.TRYING,
                ApiResponse.error(new ErrorBean("ERR_TEST").description("biz failure")), null,
                TccStatus.TRY_FAILED, "svc__confirmMethod");
    }

    @Test
    public void testFinishTryUnknownExceptionMarksTryUnknown() throws Exception {
        finishAndAssert(TccStatus.TRYING, null, new IllegalStateException("network down"),
                TccStatus.TRY_UNKNOWN, "svc__confirmMethod");
    }

    @Test
    public void testFinishTrySafeFailExceptionMarksTryFailed() throws Exception {
        TccEngine engine = newEngine();
        engine.setExceptionChecker(ex -> true);
        ITccBranchRecord record = branchRecord(TccStatus.TRYING, "svc__confirmMethod");
        newBranchTxn(engine, record).finishTryAsync(null, new IllegalStateException("not sent"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, TccStatus.TRY_FAILED);
    }

    @Test
    public void testBeginConfirmFromTrySuccessMarksConfirming() throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.TRY_SUCCESS, "svc__confirmMethod");
        newBranchTxn(engine, record).beginConfirmAsync()
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, TccStatus.CONFIRMING);
    }

    @Test
    public void testBeginConfirmFromNonTryStatusRejected() {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.TRY_FAILED, "svc__confirmMethod");
        NopException e = assertThrows(NopException.class,
                () -> newBranchTxn(engine, record).beginConfirmAsync());
        assertEquals(ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testFinishConfirmSuccessAndFailure() throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord ok = branchRecord(TccStatus.CONFIRMING, "svc__confirmMethod");
        newBranchTxn(engine, ok).finishConfirmAsync(ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(ok, TccStatus.CONFIRM_SUCCESS);

        ITccBranchRecord fail = branchRecord(TccStatus.CONFIRMING, "svc__confirmMethod");
        newBranchTxn(engine, fail).finishConfirmAsync(null, new IllegalStateException("rpc error"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(fail, TccStatus.CONFIRM_FAILED);
    }

    @Test
    public void testBeginCancelFromTrySuccessMarksCancelling() throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.TRY_SUCCESS, "svc__confirmMethod");
        newBranchTxn(engine, record).beginCancelAsync(false)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, TccStatus.CANCELLING);
    }

    @Test
    public void testBeginTimeoutCancelMarksBeforeTimeout() throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.TRY_SUCCESS, "svc__confirmMethod");
        newBranchTxn(engine, record).beginCancelAsync(true)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, TccStatus.BEFORE_TIMEOUT);
    }

    @Test
    public void testBeginCancelFromConfirmedBranchRejected() {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.CONFIRM_SUCCESS, "svc__confirmMethod");
        NopException e = assertThrows(NopException.class,
                () -> newBranchTxn(engine, record).beginCancelAsync(false));
        assertEquals(ERR_TCC_INVALID_CANCEL_BRANCH_STATUS.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testFinishCancelOutcomes() throws Exception {
        TccEngine engine = newEngine();

        ITccBranchRecord ok = branchRecord(TccStatus.CANCELLING, "svc__confirmMethod");
        newBranchTxn(engine, ok).finishCancelAsync(false, ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(ok, TccStatus.CANCEL_SUCCESS);

        ITccBranchRecord failed = branchRecord(TccStatus.CANCELLING, "svc__confirmMethod");
        newBranchTxn(engine, failed).finishCancelAsync(false, null, new IllegalStateException("rpc error"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(failed, TccStatus.CANCEL_FAILED);

        ITccBranchRecord timeoutFailed = branchRecord(TccStatus.BEFORE_TIMEOUT, "svc__confirmMethod");
        newBranchTxn(engine, timeoutFailed).finishCancelAsync(true, null, new IllegalStateException("rpc error"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(timeoutFailed, TccStatus.TIMEOUT_FAILED);
    }

    @Test
    public void testFinishCancelBizFatalMarksBizCancelFailed() throws Exception {
        TccEngine engine = newEngine();
        ITccBranchRecord record = branchRecord(TccStatus.CANCELLING, "svc__confirmMethod");
        // 业务致命失败以 error 响应承载：非 bizSuccess 才会进入 bizFatal 分支
        ApiResponse<?> response = ApiResponse.error(new ErrorBean("ERR_BIZ").description("biz fatal"));
        response.setBizFatal(Boolean.TRUE);
        newBranchTxn(engine, record).finishCancelAsync(false, response, null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        FakeTccRecordStore.assertStatus(record, TccStatus.BIZ_CANCEL_FAILED);
    }
}
