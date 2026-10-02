package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证全局事务 endAsync 的补偿路由语义：
 * 成功→confirm；业务异常/失败响应/分支 rollbackOnly→cancel；
 * CONFIRMING/CONFIRM_FAILED 中间态必须续跑 confirm；CANCELLING 中间态拒绝再 confirm。
 */
@Timeout(10)
public class TestTccTransactionEndStates {

    static class RecordingInvoker implements io.nop.api.core.rpc.IRpcServiceInvoker {
        final List<String> methods = new CopyOnWriteArrayList<>();
        volatile boolean failRpc = false;

        @Override
        public java.util.concurrent.CompletionStage<ApiResponse<?>> invokeAsync(
                String serviceName, String serviceMethod, ApiRequest<?> request,
                io.nop.api.core.util.ICancelToken cancelToken) {
            methods.add(serviceMethod);
            if (failRpc)
                return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("rpc fail"));
            return java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }
    }

    static class Fixture {
        FakeTccRecordStore store = new FakeTccRecordStore();
        RecordingInvoker invoker = new RecordingInvoker();
        TccEngine engine;

        Fixture() {
            engine = new TccEngine();
            engine.setTccRecordStore(store);
            engine.setServiceInvoker(invoker);
        }

        ITccRecord newRecord(TccStatus status) {
            ITccRecord record = store.newTccRecord("default");
            ((FakeTccRecordStore.FakeTccRecord) record).status = status;
            return record;
        }

        FakeTccRecordStore.FakeBranchRecord addBranch(ITccRecord record, TccStatus status) {
            FakeTccRecordStore.FakeBranchRecord branch = new FakeTccRecordStore.FakeBranchRecord(
                    record.getTxnGroup(), record.getTxnId(), "b-" + status, "svc",
                    "tryMethod", "svc__confirmMethod", "svc__cancelMethod", new ApiRequest<>());
            branch.status = status;
            store.addBranch(branch);
            return branch;
        }

        TccTransaction newTxn(ITccRecord record) {
            return new TccTransaction(false, record, engine);
        }
    }

    /**
     * endAsync 在补偿完成后会原样重抛业务异常（契约），调用方 future 以异常结束。
     */
    private static void endExpectingBusinessError(TccTransaction txn, boolean timeout,
                                                  ApiResponse<?> response, Throwable ex) throws Exception {
        try {
            txn.endAsync(timeout, response, ex).toCompletableFuture().get(5, TimeUnit.SECONDS);
            throw new AssertionError("endAsync must rethrow the business exception");
        } catch (java.util.concurrent.ExecutionException e) {
            // 预期路径：业务异常被重抛
        }
    }

    @Test
    public void testEndWithSuccessConfirmsBranches() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        FakeTccRecordStore.FakeBranchRecord branch = f.addBranch(record, TccStatus.TRY_SUCCESS);

        f.newTxn(record).endAsync(false, ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TccStatus.CONFIRM_SUCCESS, record.getTccStatus(), "global txn must confirm");
        FakeTccRecordStore.assertStatus(branch, TccStatus.CONFIRM_SUCCESS);
        assertTrue(f.invoker.methods.contains("svc__confirmMethod"),
                "confirm method must be invoked, got=" + f.invoker.methods);
    }

    @Test
    public void testEndWithExceptionCancelsBranches() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        FakeTccRecordStore.FakeBranchRecord branch = f.addBranch(record, TccStatus.TRY_SUCCESS);

        endExpectingBusinessError(f.newTxn(record), false, null, new IllegalStateException("biz error"));

        assertEquals(TccStatus.CANCEL_SUCCESS, record.getTccStatus(), "task failure must cancel");
        FakeTccRecordStore.assertStatus(branch, TccStatus.CANCEL_SUCCESS);
        assertTrue(f.invoker.methods.contains("svc__cancelMethod"),
                "cancel method must be invoked, got=" + f.invoker.methods);
    }

    @Test
    public void testEndWithRollbackOnlyBranchCancels() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        // TRY_FAILED 属于 rollbackOnly 状态，即使响应成功也必须走 cancel 路径
        FakeTccRecordStore.FakeBranchRecord branch = f.addBranch(record, TccStatus.TRY_FAILED);
        FakeTccRecordStore.FakeBranchRecord liveBranch = f.addBranch(record, TccStatus.TRY_SUCCESS);

        f.newTxn(record).endAsync(false, ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TccStatus.CANCEL_SUCCESS, record.getTccStatus(),
                "rollbackOnly branch must force cancel path");
        // TRY_FAILED 已是取消终态，cancelAllAsync 必须跳过（不需要补偿），只补偿 try 成功的分支
        FakeTccRecordStore.assertStatus(branch, TccStatus.TRY_FAILED);
        FakeTccRecordStore.assertStatus(liveBranch, TccStatus.CANCEL_SUCCESS);
        assertEquals(List.of("svc__cancelMethod"), f.invoker.methods,
                "only the live branch's cancel method may be invoked");
    }

    @Test
    public void testConfirmingStateResumesConfirmEvenOnFailure() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.CONFIRMING);
        FakeTccRecordStore.FakeBranchRecord branch = f.addBranch(record, TccStatus.CONFIRM_FAILED);

        // confirm 阶段已开始：即使本次业务失败也必须继续 confirm，不允许回滚
        endExpectingBusinessError(f.newTxn(record), false, null, new IllegalStateException("biz error"));

        assertEquals(TccStatus.CONFIRM_SUCCESS, record.getTccStatus(),
                "confirming txn must resume confirm, not cancel");
        FakeTccRecordStore.assertStatus(branch, TccStatus.CONFIRM_SUCCESS);
    }

    @Test
    public void testCancellingStateRejectsConfirm() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.CANCELLING);
        f.addBranch(record, TccStatus.TRY_SUCCESS);

        f.newTxn(record).endAsync(false, ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TccStatus.CANCELLING, record.getTccStatus(),
                "cancelling txn must not be re-confirmed");
        assertEquals(List.of(), f.invoker.methods,
                "no rpc may be invoked when state guard blocks confirm");
    }

    @Test
    public void testAlreadyConfirmedBranchSkippedOnConfirm() throws Exception {
        Fixture f = new Fixture();
        ITccRecord record = f.newRecord(TccStatus.TRYING);
        f.addBranch(record, TccStatus.CONFIRM_SUCCESS);

        f.newTxn(record).endAsync(false, ApiResponse.success("ok"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(TccStatus.CONFIRM_SUCCESS, record.getTccStatus());
        assertEquals(List.of(), f.invoker.methods,
                "already confirmed branch must not trigger a second confirm rpc");
    }
}
