package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTccRunner {

    static ITccBranchTransaction branch(TccStatus status) {
        return new ITccBranchTransaction() {
            @Override
            public TccStatus getBranchStatus() {
                return status;
            }

            @Override
            public String getTxnGroup() {
                return "g";
            }

            @Override
            public String getTxnId() {
                return "t";
            }

            @Override
            public ITccBranchRecord getBranchRecord() {
                return null;
            }

            @Override
            public CompletionStage<Void> beginTryAsync() {
                return null;
            }

            @Override
            public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
                return null;
            }

            @Override
            public CompletionStage<Void> beginConfirmAsync() {
                return null;
            }

            @Override
            public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
                return null;
            }

            @Override
            public CompletionStage<Void> beginCancelAsync(boolean timeout) {
                return null;
            }

            @Override
            public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
                return null;
            }
        };
    }

    @Test
    public void aggregateConfirm_whenAllConfirmSuccess_thenConfirmSuccess() {
        assertEquals(TccStatus.CONFIRM_SUCCESS,
                TccRunner.aggregateConfirmBranchStatus(Arrays.asList(
                        branch(TccStatus.CONFIRM_SUCCESS), branch(TccStatus.CONFIRM_SUCCESS))));
    }

    @Test
    public void aggregateConfirm_whenHasConfirmFailed_thenConfirmFailed() {
        assertEquals(TccStatus.CONFIRM_FAILED,
                TccRunner.aggregateConfirmBranchStatus(Arrays.asList(
                        branch(TccStatus.CONFIRM_SUCCESS), branch(TccStatus.CONFIRM_FAILED))));
    }

    @Test
    public void aggregateConfirm_whenHasCancelled_thenThrow() {
        // confirm 阶段不应出现 cancelled 分支，出现说明状态机被破坏，必须报错
        assertThrows(NopException.class, () ->
                TccRunner.aggregateConfirmBranchStatus(Arrays.asList(
                        branch(TccStatus.CONFIRM_SUCCESS), branch(TccStatus.CANCEL_SUCCESS))));
    }

    @Test
    public void aggregateCancel_whenAllCancelSuccess_thenCancelSuccess() {
        assertEquals(TccStatus.CANCEL_SUCCESS,
                TccRunner.aggregateCancelBranchStatus(Collections.singletonList(
                        branch(TccStatus.CANCEL_SUCCESS))));
    }

    @Test
    public void aggregateCancel_whenHasBizCancelFailed_thenBizCancelFailed() {
        assertEquals(TccStatus.BIZ_CANCEL_FAILED,
                TccRunner.aggregateCancelBranchStatus(Arrays.asList(
                        branch(TccStatus.CANCEL_SUCCESS), branch(TccStatus.BIZ_CANCEL_FAILED))));
    }

    @Test
    public void aggregateCancel_whenHasCancelFailed_thenCancelFailed() {
        assertEquals(TccStatus.CANCEL_FAILED,
                TccRunner.aggregateCancelBranchStatus(Arrays.asList(
                        branch(TccStatus.CANCEL_SUCCESS), branch(TccStatus.CANCEL_FAILED))));
    }
}
