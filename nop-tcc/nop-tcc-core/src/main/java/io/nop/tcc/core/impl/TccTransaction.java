/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.tcc.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class TccTransaction implements ITccTransaction {
    static final Logger LOG = LoggerFactory.getLogger(TccTransaction.class);

    private final TccEngine tccEngine;
    private final ITccRecord tccRecord;
    private final boolean initiator;

    public TccTransaction(boolean initiator, ITccRecord tccRecord, TccEngine tccEngine) {
        this.initiator = initiator;
        this.tccRecord = tccRecord;
        this.tccEngine = tccEngine;
    }

    public String toString() {
        return "TccTransaction[txnId=" + getTxnId() + ",txnGroup=" + getTxnGroup() + ",status=" + tccRecord.getTccStatus() + "]";
    }

    public ITccRecord getTccRecord() {
        return tccRecord;
    }

    @Override
    public boolean isInitiator() {
        return initiator;
    }

    protected ITccRecordStore getRepository() {
        return tccEngine.getTccRecordRepository();
    }

    @Override
    public CompletionStage<Void> beginAsync() {
        LOG.info("nop.tcc.begin:txn={}", this);
        return getRepository().saveTccRecordAsync(tccRecord, TccStatus.TRYING);
    }

    @Override
    public CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
        return tccEngine.loadBranchTransactionsAsync(this).thenCompose(branchTxns -> {
            if (isRollbackOnly(branchTxns) || isFailed(response, ex)) {
                if (ex != null) {
                    LOG.info("nop.tcc.exec-fail", ex);
                } else if (response != null && !response.isOk()) {
                    LOG.info("nop.tcc.response:{}", JsonTool.stringify(response));
                }
                return doCancelAsync(timeout, branchTxns);
            } else {
                return doConfirmAsync(branchTxns);
            }
        }).whenComplete((ret, err2) -> {
            // 出现异常时避免将异常吃掉
            if (ex != null)
                throw NopException.adapt(ex);
        });
    }

    private CompletionStage<Void> doCancelAsync(boolean timeout, List<ITccBranchTransaction> branchTxns) {
        LOG.info("nop.tcc.cancel:txn={},branches={}", this, branchTxns);

        if (tccRecord.getTccStatus().isCancelled())
            return FutureHelper.success(null);

        if (tccRecord.getTccStatus().isFinished())
            return FutureHelper.success(null);

        CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
                .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));

        return FutureHelper.whenCompleteAsync(future, (response, err) -> {
            TccStatus status = TccRunner.aggregateCancelBranchStatus(branchTxns);
            return getRepository().updateTccStatusAsync(tccRecord, status, err);
        });
    }

    private CompletionStage<Void> doConfirmAsync(List<ITccBranchTransaction> branchTxns) {
        LOG.info("nop.tcc.confirm:txn={},branches={}", this, branchTxns);

        if (tccRecord.getTccStatus().isConfirmed())
            return FutureHelper.success(null);

        CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CONFIRMING, null)
                .thenCompose(r -> TccRunner.confirmAllAsync(branchTxns, tccEngine.getServiceInvoker()));
        return FutureHelper.whenCompleteAsync(future, (r2, err) -> {
            TccStatus status = TccRunner.aggregateConfirmBranchStatus(branchTxns);
            return getRepository().updateTccStatusAsync(tccRecord, status, err);
        });
    }

    private boolean isFailed(ApiResponse<?> response, Throwable ex) {
        if (ex != null)
            return true;
        return response != null && !response.isOk();
    }

    private boolean isRollbackOnly(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (branchTxn.getBranchStatus().isRollbackOnly())
                return true;
        }
        return false;
    }
}